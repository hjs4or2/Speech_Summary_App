package com.app.speechsummary.stt

import android.content.Context
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.whispercpp.whisper.WhisperSegment
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Assigns stable, per-file speaker labels to Whisper's timestamped segments. */
class SpeakerDiarizer(
    private val context: Context,
) {
    suspend fun label(
        pcm: File,
        segments: List<WhisperSegment>,
        expectedSpeakerCount: Int? = null
    ): List<String> {
        require(expectedSpeakerCount == null || expectedSpeakerCount in 1..99) {
            "화자 수는 1~99명으로 지정해 주세요."
        }
        currentCoroutineContext().ensureActive()
        if (segments.isEmpty()) return emptyList()
        // With one known speaker there is nothing to cluster. Avoid splitting
        // changes in that person's voice into spurious new speakers.
        if (expectedSpeakerCount == 1) return List(segments.size) { "A" }
        val diarizer = OfflineSpeakerDiarization(
            context.assets,
            OfflineSpeakerDiarizationConfig(
                segmentation = OfflineSpeakerSegmentationModelConfig(
                    pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                        model = "speaker-segmentation.onnx",
                        windowShiftRatio = 0.1f
                    ),
                    numThreads = 2
                ),
                embedding = SpeakerEmbeddingExtractorConfig(
                    model = "speaker-eres2net.onnx",
                    numThreads = 2
                ),
                clustering = FastClusteringConfig(
                    numClusters = expectedSpeakerCount ?: -1,
                    threshold = 0.85f
                ),
                minDurationOn = 0.2f,
                minDurationOff = 0.5f
            )
        )
        try {
            currentCoroutineContext().ensureActive()
            val samples = readSamples(pcm)
            // This bundled model has a 160000-sample (10-second) window.
            // sherpa-onnx's single-window shortcut bypasses clustering entirely,
            // including numClusters. One extra silent sample forces the regular
            // overlapping-window path so manual counts are respected on short clips.
            val analysisSamples = if (expectedSpeakerCount != null && samples.size <= 160_000) {
                samples.copyOf(160_001)
            } else samples
            val duration = samples.size / 16_000f
            val turns = diarizer.process(analysisSamples)
                .filter { it.start < duration }
                .sortedBy { it.start }
            currentCoroutineContext().ensureActive()
            return SpeakerAlignment.align(segments, turns)
        } finally {
            diarizer.release()
        }
    }

    private fun readSamples(pcm: File): FloatArray {
        val total = pcm.length() / 2
        require(total in 1..Int.MAX_VALUE.toLong()) { "화자 분석에 사용할 수 없는 음성 길이입니다." }
        val samples = FloatArray(total.toInt())
        val bytes = ByteArray(64 * 1024)
        RandomAccessFile(pcm, "r").use { input ->
            var offset = 0
            while (offset < samples.size) {
                val count = min(bytes.size / 2, samples.size - offset)
                input.readFully(bytes, 0, count * 2)
                for (i in 0 until count) {
                    val lo = bytes[i * 2].toInt() and 0xff
                    val hi = bytes[i * 2 + 1].toInt()
                    samples[offset + i] = ((hi shl 8) or lo).toShort() / 32768f
                }
                offset += count
            }
        }
        return samples
    }

}
