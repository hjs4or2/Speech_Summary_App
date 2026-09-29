package com.app.speechsummary

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.speechsummary.data.DownloadableModels
import com.app.speechsummary.data.ModelKind
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Fixed 5-second fixture, reusable against baseline and optimized APKs. */
@RunWith(AndroidJUnit4::class)
class WhisperPerformanceTest {
    @Test fun shortSpeechTiming() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val model = DownloadableModels(instrumentation.targetContext).file(ModelKind.WHISPER)
        assumeTrue("Install models in the app before this performance test", model.isFile)
        val bytes = ByteArray(32_000 * 5)
        instrumentation.context.assets.open("four-speakers.wav").use { input ->
            check(input.skip(44) == 44L)
            var offset = 0
            while (offset < bytes.size) {
                val count = input.read(bytes, offset, bytes.size - offset)
                check(count > 0)
                offset += count
            }
        }
        val samples = FloatArray(bytes.size / 2) { index ->
            (((bytes[index * 2 + 1].toInt() shl 8) or (bytes[index * 2].toInt() and 255))
                .toShort() / 32768f)
        }
        val start = SystemClock.elapsedRealtime()
        val engine = WhisperContext.createContextFromFile(model.absolutePath)
        Log.i("WhisperPerformance", "model_load_ms=${SystemClock.elapsedRealtime() - start}")
        try {
            repeat(2) { run ->
                val inferStart = SystemClock.elapsedRealtime()
                val segments = engine.transcribeSegments(samples)
                Log.i("WhisperPerformance", "run=$run audio_ms=5000 inference_ms=${SystemClock.elapsedRealtime() - inferStart} segments=${segments.size} text=${segments.joinToString(" ") { it.text }}")
                assertTrue("Expected text from fixed speech fixture", segments.any { it.text.isNotBlank() })
            }
        } finally { engine.release() }
    }
}
