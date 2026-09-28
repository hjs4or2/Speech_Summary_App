package com.app.speechsummary

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhisperIntegrationTest {
    @Test fun bundledModelLoadsInNativeWhisper() = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val context = WhisperContext.createContextFromAsset(assets, "ggml-base.bin")
        try { assertNotNull(context) } finally { context.release() }
    }

    @Test fun shortAudioCompletesNativeTranscription() = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val context = WhisperContext.createContextFromAsset(assets, "ggml-base.bin")
        try {
            val started = SystemClock.elapsedRealtime()
            val result = context.transcribeData(FloatArray(16_000 * 3), printTimestamp = false)
            Log.i("WhisperIntegrationTest", "3-second silent clip: ${SystemClock.elapsedRealtime() - started} ms")
            assertNotNull(result)
        } finally {
            context.release()
        }
    }
}
