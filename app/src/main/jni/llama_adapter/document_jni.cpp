#include <jni.h>
#include <llama.h>
#include <algorithm>
#include <atomic>
#include <codecvt>
#include <locale>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace {
struct Engine {
    std::atomic<bool> abort{false};
    llama_model * model = nullptr;
};

std::once_flag backend_once;

void fail(JNIEnv * env, const char * message) {
    jclass exception = env->FindClass("java/lang/IllegalStateException");
    if (exception) env->ThrowNew(exception, message);
}

// JNI's modified UTF-8 is deliberately avoided. llama.cpp consumes ordinary UTF-8.
std::string from_java(JNIEnv * env, jstring value) {
    if (!value) return {};
    const jchar * chars = env->GetStringChars(value, nullptr);
    if (!chars) return {};
    const jsize length = env->GetStringLength(value);
    std::u16string utf16(reinterpret_cast<const char16_t *>(chars), length);
    env->ReleaseStringChars(value, chars);
    return std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>{}.to_bytes(utf16);
}

jstring to_java(JNIEnv * env, const std::string & value) {
    const auto utf16 = std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>{}.from_bytes(value);
    return env->NewString(reinterpret_cast<const jchar *>(utf16.data()), static_cast<jsize>(utf16.size()));
}

jobjectArray result(JNIEnv * env, const std::string & text, const char * status) {
    jclass string_class = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(2, string_class, nullptr);
    env->SetObjectArrayElement(result, 0, to_java(env, text));
    env->SetObjectArrayElement(result, 1, to_java(env, status));
    return result;
}

bool aborted(void * data) {
    return static_cast<Engine *>(data)->abort.load(std::memory_order_relaxed);
}

// Four required keys and both fields in every entry are enforced by llama.cpp's GBNF sampler.
constexpr const char * SECTION_GRAMMAR = R"gbnf(
root ::= "{" ws "\"topic\"" ws ":" ws string "," ws "\"discussions\"" ws ":" ws discussionarray "," ws "\"decisions\"" ws ":" ws array "," ws "\"actions\"" ws ":" ws array "}" ws
discussionarray ::= "[" ws item ("," ws item)* "]" ws
array ::= "[" ws (item ("," ws item)*)? "]" ws
item ::= "{" ws "\"text\"" ws ":" ws string "," ws "\"evidence\"" ws ":" ws string "}" ws
string ::= "\"" ([^"\\\x7F\x00-\x1F] | "\\" (["\\bfnrt] | "u" [0-9a-fA-F]{4}))* "\"" ws
ws ::= | " " | "\n" [ \t]{0,20}
)gbnf";
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_app_speechsummary_llm_LocalLlama_nativeCreate(JNIEnv * env, jobject) {
    try {
        std::call_once(backend_once, llama_backend_init);
        return reinterpret_cast<jlong>(new Engine());
    } catch (const std::exception & error) { fail(env, error.what()); return 0; }
      catch (...) { fail(env, "Native model initialization failed"); return 0; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_app_speechsummary_llm_LocalLlama_nativeLoad(JNIEnv * env, jobject, jlong pointer, jstring path) {
    try {
    auto * engine = reinterpret_cast<Engine *>(pointer);
    if (!engine || engine->model) { fail(env, "Invalid llama handle"); return; }
    const auto model_path = from_java(env, path);
    auto params = llama_model_default_params();
    params.n_gpu_layers = 0;
    params.use_mmap = true;
    params.progress_callback = [](float, void * data) { return !aborted(data); };
    params.progress_callback_user_data = engine;
    engine->model = llama_model_load_from_file(model_path.c_str(), params);
    if (!engine->model) fail(env, engine->abort ? "Model load cancelled" : "Could not load GGUF model");
    } catch (const std::exception & error) { fail(env, error.what()); }
      catch (...) { fail(env, "Native model load failed"); }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_app_speechsummary_llm_LocalLlama_nativeGenerate(
    JNIEnv * env, jobject, jlong pointer, jstring system_prompt, jstring user_prompt, jint max_tokens, jboolean json_only) {
    try {
    auto * engine = reinterpret_cast<Engine *>(pointer);
    if (!engine || !engine->model || max_tokens <= 0 || max_tokens > 768) {
        fail(env, "Invalid llama generation request"); return nullptr;
    }
    const auto system = from_java(env, system_prompt);
    const auto user = from_java(env, user_prompt);
    const llama_chat_message messages[] = {{"system", system.c_str()}, {"user", user.c_str()}};
    const char * tmpl = llama_model_chat_template(engine->model, nullptr);
    if (!tmpl) { fail(env, "GGUF chat template missing"); return nullptr; }
    int32_t prompt_size = llama_chat_apply_template(tmpl, messages, 2, true, nullptr, 0);
    if (prompt_size <= 0 || prompt_size > 24000) { fail(env, "Prompt too long"); return nullptr; }
    std::string prompt(prompt_size + 1, '\0');
    llama_chat_apply_template(tmpl, messages, 2, true, prompt.data(), static_cast<int32_t>(prompt.size()));
    prompt.resize(prompt_size);

    const auto * vocab = llama_model_get_vocab(engine->model);
    std::vector<llama_token> tokens(3072);
    int32_t token_count = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                                          tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (token_count <= 0 || token_count + max_tokens > 3072) {
        fail(env, "Transcript exceeds model context; split it into smaller sections"); return nullptr;
    }
    tokens.resize(token_count);
    auto context_params = llama_context_default_params();
    context_params.n_ctx = 3072;
    context_params.n_batch = 512;
    context_params.n_ubatch = 512;
    context_params.n_threads = 4;
    context_params.n_threads_batch = 4;
    context_params.abort_callback = aborted;
    context_params.abort_callback_data = engine;
    std::unique_ptr<llama_context, decltype(&llama_free)> context(
        llama_init_from_model(engine->model, context_params), llama_free);
    if (!context) { fail(env, "Could not allocate llama context"); return nullptr; }
    for (int32_t offset = 0; offset < token_count; offset += 512) {
        if (engine->abort) return result(env, "", "cancelled");
        const auto count = std::min(512, token_count - offset);
        if (llama_decode(context.get(), llama_batch_get_one(tokens.data() + offset, count)) != 0) {
            if (engine->abort) return result(env, "", "cancelled");
            fail(env, "Prompt evaluation failed"); return nullptr;
        }
    }
    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
        llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
    if (!sampler) { fail(env, "Could not initialize sampler"); return nullptr; }
    if (json_only) {
        auto * grammar = llama_sampler_init_grammar(vocab, SECTION_GRAMMAR, "root");
        if (!grammar) { fail(env, "Could not initialize JSON grammar"); return nullptr; }
        llama_sampler_chain_add(sampler.get(), grammar);
    }
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());
    std::string output;
    for (int step = 0; step < max_tokens; ++step) {
        if (engine->abort) return result(env, "", "cancelled");
        llama_token token = llama_sampler_sample(sampler.get(), context.get(), -1);
        if (llama_vocab_is_eog(vocab, token)) return result(env, output, "complete");
        int32_t needed = llama_token_to_piece(vocab, token, nullptr, 0, 0, false);
        if (needed < 0) needed = -needed;
        std::string piece(needed + 8, '\0');
        const int32_t actual = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        if (actual < 0) { fail(env, "Could not decode token"); return nullptr; }
        output.append(piece.data(), actual);
        if (llama_decode(context.get(), llama_batch_get_one(&token, 1)) != 0) {
            if (engine->abort) return result(env, "", "cancelled");
            fail(env, "Generation failed"); return nullptr;
        }
    }
    return result(env, "", "limit");
    } catch (const std::exception & error) { fail(env, error.what()); return nullptr; }
      catch (...) { fail(env, "Native generation failed"); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_app_speechsummary_llm_LocalLlama_nativeAbort(JNIEnv * env, jobject, jlong pointer) {
    try {
        auto * engine = reinterpret_cast<Engine *>(pointer);
        if (engine) engine->abort.store(true);
    } catch (...) { fail(env, "Native abort failed"); }
}

extern "C" JNIEXPORT void JNICALL
Java_com_app_speechsummary_llm_LocalLlama_nativeClose(JNIEnv * env, jobject, jlong pointer) {
    try {
    auto * engine = reinterpret_cast<Engine *>(pointer);
    if (!engine) return;
    engine->abort.store(true);
    if (engine->model) llama_model_free(engine->model);
    delete engine;
    } catch (...) { fail(env, "Native model close failed"); }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_app_speechsummary_llm_LocalLlama_nativeUtf8RoundTrip(JNIEnv * env, jobject, jstring input) {
    try { return to_java(env, from_java(env, input)); }
    catch (const std::exception & error) { fail(env, error.what()); return nullptr; }
    catch (...) { fail(env, "UTF-8 conversion failed"); return nullptr; }
}
