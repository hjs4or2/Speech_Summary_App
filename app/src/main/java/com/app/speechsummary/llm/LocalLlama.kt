package com.app.speechsummary.llm

import java.io.Closeable
import java.io.File

data class GenerationResult(val text: String, val complete: Boolean)

/** A single-use CPU llama.cpp session. abort() is safe to call from the UI thread. */
class LocalLlama : Closeable {
    private var handle: Long = nativeCreate()

    fun load(model: File) {
        require(model.isFile) { "설치된 모델을 찾을 수 없어." }
        nativeLoad(handle, model.absolutePath)
    }

    fun generate(system: String, user: String, maxTokens: Int = 512, jsonOnly: Boolean = false): GenerationResult {
        val output = nativeGenerate(handle, system, user, maxTokens, jsonOnly)
        return when (output[1]) {
            "complete" -> GenerationResult(output[0], true)
            "limit" -> throw IllegalStateException("출력 토큰 한도에 도달했어. 문서는 저장하지 않았어.")
            "cancelled" -> throw kotlinx.coroutines.CancellationException("문서화를 취소했어.")
            else -> throw IllegalStateException("알 수 없는 모델 응답 상태")
        }
    }

    @Synchronized
    fun abort() {
        if (handle != 0L) nativeAbort(handle)
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeLoad(handle: Long, path: String)
    private external fun nativeGenerate(handle: Long, system: String, user: String, maxTokens: Int, jsonOnly: Boolean): Array<String>
    private external fun nativeAbort(handle: Long)
    private external fun nativeClose(handle: Long)
    private external fun nativeUtf8RoundTrip(value: String): String

    fun utf8RoundTripForTest(value: String): String = nativeUtf8RoundTrip(value)

    companion object {
        init { System.loadLibrary("llama_document") }
    }
}
