package com.app.speechsummary

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
}
