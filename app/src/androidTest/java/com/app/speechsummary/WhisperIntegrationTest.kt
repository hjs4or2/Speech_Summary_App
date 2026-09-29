package com.app.speechsummary

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.speechsummary.data.DownloadableModels
import com.app.speechsummary.data.ModelKind
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhisperIntegrationTest {
    @Test fun installedModelLoadsInNativeWhisper() = runBlocking {
        val model = DownloadableModels(InstrumentationRegistry.getInstrumentation().targetContext).file(ModelKind.WHISPER)
        assumeTrue("Install models in the app before this integration test", model.isFile)
        val context = WhisperContext.createContextFromFile(model.absolutePath)
        try { assertNotNull(context) } finally { context.release() }
    }

    @Test fun shortAudioCompletesNativeTranscription() = runBlocking {
        val model = DownloadableModels(InstrumentationRegistry.getInstrumentation().targetContext).file(ModelKind.WHISPER)
        assumeTrue("Install models in the app before this integration test", model.isFile)
        val context = WhisperContext.createContextFromFile(model.absolutePath)
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
