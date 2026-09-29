package com.app.speechsummary.stt

import android.content.Context
import android.os.SystemClock
import com.whispercpp.whisper.WhisperContext
import com.whispercpp.whisper.WhisperSegment
import com.app.speechsummary.data.DownloadableModels
import com.app.speechsummary.data.ModelKind
import com.app.speechsummary.data.DiarizationEngine
import com.app.speechsummary.data.WhisperModel
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class TranscriptionStage { MODEL_LOADING, TEXT_EXTRACTION, SPEAKER_DIARIZATION }

class WhisperTranscriber(
    private val context: Context,
    private val diarizationEngine: DiarizationEngine = DiarizationEngine.SHERPA,
    private val whisperModel: WhisperModel = WhisperModel.BASE
) {
    @Volatile
    private var whisper: WhisperContext? = null

    suspend fun transcribe(
        pcm: File,
        onProgress: suspend (Long, Long) -> Unit,
        onDiarizationStart: suspend () -> Unit,
        onStageCompleted: suspend (TranscriptionStage, Long) -> Unit = { _, _ -> },
        expectedSpeakerCount: Int? = null
    ): String {
        require(expectedSpeakerCount == null || expectedSpeakerCount in 1..99) {
            "화자 수는 1~99명으로 지정해 주세요."
        }
        require(diarizationEngine.acceptsSpeakerCount(expectedSpeakerCount)) {
            "Nemotron 3는 최대 8명까지만 지원해. 9명 이상은 기존 화자 구분 엔진을 선택해 줘."
        }
        require(pcm.length() > 0) { "음성 파일이 비어 있습니다." }
        currentCoroutineContext().ensureActive()
        val loadStarted = SystemClock.elapsedRealtime()
        val engine = whisper ?: WhisperContext.createContextFromFile(
            DownloadableModels(context).file(whisperModel.kind).absolutePath
        )
            .also { whisper = it }
        currentCoroutineContext().ensureActive()
        onStageCompleted(TranscriptionStage.MODEL_LOADING, SystemClock.elapsedRealtime() - loadStarted)
        val extractionStarted = SystemClock.elapsedRealtime()
        val chunk = ByteArray(16_000 * 2 * 30)
        val totalSamples = pcm.length() / 2
        var processedSamples = 0L
        val segments = mutableListOf<WhisperSegment>()
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
                    val offsetSeconds = processedSamples / 16_000.0
                    segments += engine.transcribeSegments(samples).map { segment ->
                        segment.copy(
                            startSeconds = segment.startSeconds + offsetSeconds,
                            endSeconds = segment.endSeconds + offsetSeconds
                        )
                    }
                    processedSamples += samples.size
                    onProgress(processedSamples, totalSamples)
                }
        }
        onStageCompleted(TranscriptionStage.TEXT_EXTRACTION, SystemClock.elapsedRealtime() - extractionStarted)
        engine.release()
        whisper = null
        currentCoroutineContext().ensureActive()
        onDiarizationStart()
        val diarizationStarted = SystemClock.elapsedRealtime()
        val labels = when (diarizationEngine) {
            DiarizationEngine.SHERPA -> SpeakerDiarizer(context).label(pcm, segments, expectedSpeakerCount)
            DiarizationEngine.NEMOTRON -> NemotronDiarizer.label(
                DownloadableModels(context).file(ModelKind.NEMOTRON), pcm, segments)
        }
        onStageCompleted(TranscriptionStage.SPEAKER_DIARIZATION, SystemClock.elapsedRealtime() - diarizationStarted)
        return segments.mapIndexedNotNull { index, segment ->
            segment.text.takeIf { it.isNotBlank() }?.let { text ->
                "${labels[index]}: $text"
            }
        }.joinToString("\n")
    }

    fun requestAbort() { whisper?.requestAbort() }

    suspend fun close() {
        whisper?.release()
        whisper = null
    }
}
