package com.app.speechsummary

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.speechsummary.data.DownloadableModels
import com.app.speechsummary.data.ModelKind
import com.app.speechsummary.data.DiarizationEngine
import com.app.speechsummary.data.DiarizationSelection
import com.app.speechsummary.stt.NemotronNative
import com.app.speechsummary.stt.WhisperTranscriber
import com.whispercpp.whisper.WhisperContext
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NemotronInferenceTest {
    @Test fun nineSpeakersAreRejectedBeforeAudioOrModelLoad() { runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val transcriber = WhisperTranscriber(context, DiarizationEngine.NEMOTRON)
        try {
            transcriber.transcribe(
                File(context.cacheDir, "intentionally-absent-nine-speakers.pcm"),
                onProgress = { _, _ -> fail("STT started") },
                onDiarizationStart = { fail("Diarization started") },
                expectedSpeakerCount = 9
            )
            fail("Nine speakers were accepted")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("최대 8명"))
        } finally {
            transcriber.close()
        }
    } }

    @Test fun engineChoiceSurvivesPreferenceReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("diarization_selection_test", Context.MODE_PRIVATE)
        try {
            val selection = DiarizationSelection(
                { preferences.getString("engine", null) },
                { preferences.edit().putString("engine", it).commit() }
            )
            selection.save(DiarizationEngine.NEMOTRON)
            val reloaded = DiarizationSelection({ preferences.getString("engine", null) }, {})
            assertEquals(DiarizationEngine.NEMOTRON, reloaded.load())
        } finally {
            preferences.edit().remove("engine").commit()
        }
    }

    @Test fun officialGgufRunsAlongsideWhisperNativeInference() { runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val model = DownloadableModels(instrumentation.targetContext).file(ModelKind.NEMOTRON)
        val whisperModel = DownloadableModels(instrumentation.targetContext).file(ModelKind.WHISPER)
        assumeTrue("Install the official Nemotron model before this test", model.isFile)
        assumeTrue("Install the multilingual Whisper base model before this test", whisperModel.isFile)
        val pcm = File(instrumentation.targetContext.cacheDir, "nemotron-smoke.pcm")
        try {
            instrumentation.context.assets.open("four-speakers.wav").use { wav ->
                check(wav.skip(44) == 44L)
                pcm.outputStream().use { output ->
                    val audio = ByteArray(32_000 * 5)
                    val count = wav.read(audio)
                    assertTrue(count > 0)
                    output.write(audio, 0, count)
                }
            }
            val turns = NemotronNative.diarize(model.absolutePath, pcm.absolutePath)
            Log.i("NemotronInferenceTest", "Native speaker turns: ${turns.toList().chunked(3)}")
            assertEquals(0, turns.size % 3)
            assertTrue("Expected at least one speaker turn from the speech fixture", turns.isNotEmpty())
            turns.toList().chunked(3).forEach { turn ->
                assertTrue(turn[0] >= 0.0 && turn[1] > turn[0])
                assertTrue(turn[2] in 0.0..7.0)
            }
            val whisper = WhisperContext.createContextFromFile(whisperModel.absolutePath)
            try {
                whisper.transcribeData(FloatArray(16_000 * 3), printTimestamp = false)
            } finally {
                whisper.release()
            }
        } finally {
            pcm.delete()
        }
    } }
}
