package com.app.speechsummary.stt

import android.content.Context
import com.whispercpp.whisper.WhisperContext
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class WhisperTranscriber(private val context: Context) {
    @Volatile
    private var whisper: WhisperContext? = null

    suspend fun transcribe(pcm: File, onProgress: suspend (Long, Long) -> Unit): String {
        require(pcm.length() > 0) { "음성 파일이 비어 있습니다." }
        currentCoroutineContext().ensureActive()
        val engine = whisper ?: WhisperContext.createContextFromAsset(context.assets, "ggml-base.bin")
            .also { whisper = it }
        val chunk = ByteArray(16_000 * 2 * 30)
        val totalSamples = pcm.length() / 2
        var processedSamples = 0L
        return buildString {
            pcm.inputStream().buffered().use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    var count = 0
                    while (count < chunk.size) {
                        val read = input.read(chunk, count, chunk.size - count)
                        if (read < 0) break
                        count += read
                    }
                    if (count == 0) break
                    val samples = FloatArray(count / 2) { index ->
                        val lo = chunk[index * 2].toInt() and 0xff
                        val hi = chunk[index * 2 + 1].toInt()
                        ((hi shl 8) or lo).toShort() / 32768f
                    }
                    append(engine.transcribeData(samples, printTimestamp = false))
                    append('\n')
                    processedSamples += samples.size
                    onProgress(processedSamples, totalSamples)
                }
            }
        }.trim()
    }

    fun requestAbort() { whisper?.requestAbort() }

    suspend fun close() {
        whisper?.release()
        whisper = null
    }
}
