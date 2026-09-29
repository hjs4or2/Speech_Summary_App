package com.app.speechsummary

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.speechsummary.data.DownloadableModels
import com.app.speechsummary.data.ModelKind
import com.app.speechsummary.stt.SpeakerDiarizer
import com.app.speechsummary.stt.WhisperTranscriber
import com.app.speechsummary.stt.TranscriptionStage
import com.whispercpp.whisper.WhisperSegment
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeakerDiarizerTest {
    @Before fun requireInstalledModels() {
        val models = DownloadableModels(InstrumentationRegistry.getInstrumentation().targetContext)
        listOf(ModelKind.WHISPER, ModelKind.SEGMENTATION, ModelKind.EMBEDDING).forEach {
            assumeTrue("Install models in the app before the diarization tests", models.file(it).isFile)
        }
    }

    @Test fun shortClipRunsTranscriptionAndDiarization() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "short-speaker-test.pcm")
        val transcriber = WhisperTranscriber(instrumentation.targetContext)
        try {
            instrumentation.context.assets.open("four-speakers.wav").use { wav ->
                check(wav.skip(44) == 44L)
                val bytes = ByteArray(32_000 * 5)
                var read = 0
                while (read < bytes.size) {
                    val count = wav.read(bytes, read, bytes.size - read)
                    if (count < 0) break
                    read += count
                }
                file.outputStream().use { it.write(bytes, 0, read) }
            }
            var progressed = false
            var diarizationStarted = false
            val stages = mutableListOf<TranscriptionStage>()
            val text = transcriber.transcribe(
                file,
                onProgress = { processed, _ -> progressed = processed > 0 },
                onDiarizationStart = { diarizationStarted = true },
                onStageCompleted = { stage, elapsedMs ->
                    stages += stage
                    assertTrue(elapsedMs >= 0)
                },
                expectedSpeakerCount = 2
            )
            Log.i("SpeakerDiarizerTest", "pipeline=$text")
            assertTrue(progressed && diarizationStarted)
            assertEquals(TranscriptionStage.entries.toList(), stages)
            assertTrue("Expected A/B speaker labels: $text", text.lines().all { it.startsWith("A:") || it.startsWith("B:") })
        } finally {
            transcriber.close()
            file.delete()
        }
    }

    @Test fun separatesMultipleVoicesInSample() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "four-speakers-test.pcm")
        try {
            instrumentation.context.assets.open("four-speakers.wav").use { wav ->
                check(wav.skip(44) == 44L)
                file.outputStream().use { wav.copyTo(it) }
            }
            val duration = file.length() / 32_000.0
            val segments = generateSequence(0.0) { it + 3.0 }
                .takeWhile { it + 3.0 < duration }
                .map { WhisperSegment(it, it + 3.0, "speech") }
                .toList()
            val detectedSpeakers = SpeakerDiarizer(instrumentation.targetContext).label(file, segments)
            Log.i("SpeakerDiarizerTest", "auto=" + detectedSpeakers.joinToString(","))
            assertTrue("Expected four speakers, got $detectedSpeakers", detectedSpeakers.filter { it != "?" }.distinct().size == 4)
        } finally {
            file.delete()
        }
    }

    @Test fun explicitTwoSpeakersConstrainsNativeClustering() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File.createTempFile("fixed-speakers-", ".pcm", instrumentation.targetContext.cacheDir)
        try {
            instrumentation.context.assets.open("four-speakers.wav").use { wav ->
                check(wav.skip(44) == 44L)
                file.outputStream().use { wav.copyTo(it) }
            }
            val duration = file.length() / 32_000.0
            val segments = generateSequence(0.0) { it + 1.0 }
                .takeWhile { it + 1.0 < duration }
                .map { WhisperSegment(it, it + 1.0, "speech") }
                .toList()
            // Deliberately constrain a multi-voice fixture to two clusters.
            // This tests the count setting, not speaker-identification accuracy.
            val labels = SpeakerDiarizer(instrumentation.targetContext).label(file, segments, 2)
            assertEquals(setOf("A", "B"), labels.filter { it != "?" }.toSet())
        } finally { file.delete() }
    }

    @Test fun singleSpeakerUsesOnlyAAndInvalidCountsFailEarly() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val unusedFile = File(context.cacheDir, "not-read-for-single-speaker.pcm")
        val segments = listOf(WhisperSegment(0.0, 1.0, "안녕하세요"), WhisperSegment(2.0, 3.0, "반갑습니다"))
        val diarizer = SpeakerDiarizer(context)
        assertEquals(listOf("A", "A"), diarizer.label(unusedFile, segments, 1))
        for (invalid in listOf(0, -1, 100)) {
            var rejected = false
            try { diarizer.label(unusedFile, segments, invalid) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue("Expected invalid count $invalid to be rejected", rejected)
        }
    }

    @Test fun shortAudioRespectsFixedCountEvenWhenMoreSpeakersAreRequested() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File.createTempFile("short-fixed-speakers-", ".pcm", instrumentation.targetContext.cacheDir)
        try {
            instrumentation.context.assets.open("four-speakers.wav").use { wav ->
                check(wav.skip(44) == 44L)
                val bytes = ByteArray(32_000 * 5)
                var offset = 0
                while (offset < bytes.size) {
                    val count = wav.read(bytes, offset, bytes.size - offset)
                    check(count > 0)
                    offset += count
                }
                file.writeBytes(bytes)
            }
            val segments = (0 until 10).map { WhisperSegment(it * 0.5, (it + 1) * 0.5, "speech") }
            val diarizer = SpeakerDiarizer(instrumentation.targetContext)
            val two = diarizer.label(file, segments, 2)
            assertTrue(two.any { it != "?" })
            assertTrue(two.all { it in setOf("A", "B", "?") })
            // There may be fewer observed voices than the supplied count.
            val many = diarizer.label(file, segments, 99)
            assertEquals(segments.size, many.size)
            assertTrue(many.any { it != "?" })
        } finally { file.delete() }
    }
}
