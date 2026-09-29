#include <jni.h>

#include <algorithm>
#include <cstdio>
#include <exception>
#include <stdexcept>
#include <string>
#include <vector>

#include "diarizer.h"

static void fail(JNIEnv* env, const char* message) {
    jclass type = env->FindClass("java/io/IOException");
    if (type) env->ThrowNew(type, message);
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_app_speechsummary_stt_NemotronNative_diarize(
    JNIEnv* env, jobject, jstring model_path, jstring pcm_path) {
    if (!model_path || !pcm_path) {
        fail(env, "Nemotron model or audio path is missing");
        return nullptr;
    }
    const char* model_chars = env->GetStringUTFChars(model_path, nullptr);
    const char* pcm_chars = env->GetStringUTFChars(pcm_path, nullptr);
    const std::string model(model_chars ? model_chars : "");
    const std::string pcm(pcm_chars ? pcm_chars : "");
    if (model_chars) env->ReleaseStringUTFChars(model_path, model_chars);
    if (pcm_chars) env->ReleaseStringUTFChars(pcm_path, pcm_chars);
    try {
        auto diarizer = nemo_speech::asr::Diarizer::load(
            -1, model, nemo_speech::asr::DiarGeometry::preset("v3-offline"));
        if (diarizer->num_speakers() != 8)
            throw std::runtime_error("Expected eight-speaker Nemotron 3 model");
        auto stream = diarizer->streaming_diarize();
        FILE* input = std::fopen(pcm.c_str(), "rb");
        if (!input) throw std::runtime_error("Cannot open local PCM audio");
        std::vector<unsigned char> bytes(16000 * 2 * 5);
        std::vector<float> samples(bytes.size() / 2);
        try {
            while (true) {
                const size_t count = std::fread(bytes.data(), 1, bytes.size(), input);
                if (count == 0) break;
                if (count % 2) throw std::runtime_error("Invalid PCM audio length");
                for (size_t i = 0; i < count / 2; ++i) {
                    const unsigned int bits = static_cast<unsigned int>(bytes[i * 2]) |
                        (static_cast<unsigned int>(bytes[i * 2 + 1]) << 8);
                    samples[i] = static_cast<int16_t>(bits) / 32768.0f;
                }
                stream->push(samples.data(), count / 2, 16000);
            }
        } catch (...) {
            std::fclose(input);
            throw;
        }
        std::fclose(input);
        stream->finish();
        const auto segments = stream->segments();
        std::vector<jdouble> values;
        values.reserve(segments.size() * 3);
        for (const auto& segment : segments) {
            values.push_back(segment.t0);
            values.push_back(segment.t1);
            values.push_back(segment.speaker);
        }
        auto result = env->NewDoubleArray(static_cast<jsize>(values.size()));
        if (result) env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
        return result;
    } catch (const std::exception& error) {
        fail(env, error.what());
        return nullptr;
    }
}
