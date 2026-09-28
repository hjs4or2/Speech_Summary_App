package com.app.speechsummary

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.speechsummary.audio.AudioPlaybackController
import com.app.speechsummary.audio.PcmWaveDataSource
import com.app.speechsummary.audio.PlaybackState
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioPlaybackTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun virtualWavSupportsHeaderBoundarySeekingAndEof() {
        val file = File(context.cacheDir, "playback_test_${UUID.randomUUID()}.pcm")
        val pcm = ByteArray(320) { it.toByte() }
        file.writeBytes(pcm)
        try {
            PcmWaveDataSource(file).use { source ->
                assertEquals(364L, source.size)
                val wav = ByteArray(364)
                assertEquals(364, source.readAt(0, wav, 0, wav.size))
                assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
                assertEquals(16_000, ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).getInt(24))
                assertArrayEquals(pcm, wav.copyOfRange(44, 364))
                val boundary = ByteArray(10)
                assertEquals(8, source.readAt(40, boundary, 1, 8))
                assertArrayEquals(wav.copyOfRange(40, 48), boundary.copyOfRange(1, 9))
                assertEquals(2, source.readAt(362, boundary, 0, 10))
                assertEquals(-1, source.readAt(364, boundary, 0, 1))
                assertEquals(0, source.readAt(364, boundary, 0, 0))
            }
        } finally { file.delete() }
    }

    @Test fun playbackPausesResumesSwitchesAndCompletes() {
        // Private disposable fixtures: never added to the user's recordings list.
        val first = File(context.cacheDir, "playback_test_${UUID.randomUUID()}.pcm")
        val second = File(context.cacheDir, "playback_test_${UUID.randomUUID()}.pcm")
        first.writeBytes(ByteArray(96_000))
        second.writeBytes(ByteArray(16_000))
        val observed = AtomicReference(PlaybackState())
        val failure = AtomicReference<String?>(null)
        var controller: AudioPlaybackController? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                try {
                    scenario.onActivity { activity ->
                        controller = AudioPlaybackController(activity, observed::set, failure::set)
                        controller!!.toggle("first", first)
                    }
                    await { observed.get().playing }
                    scenario.onActivity { controller!!.toggle("first", first) }
                    assertEquals("first", observed.get().itemId)
                    assertFalse(observed.get().playing)
                    SystemClock.sleep(150)
                    assertFalse(observed.get().playing)
                    scenario.onActivity { controller!!.toggle("first", first) }
                    await { observed.get().playing }
                    scenario.onActivity { controller!!.toggle("second", second) }
                    await { observed.get().itemId == "second" && observed.get().playing }
                    await { observed.get().itemId == null }
                    assertNull(failure.get())

                    // A failed source must reset the loading state and allow retry.
                    first.writeBytes(byteArrayOf(0))
                    scenario.onActivity { controller!!.toggle("invalid", first) }
                    assertNotNull(observed.get().error)
                    assertFalse(observed.get().preparing)
                    scenario.onActivity { controller!!.toggle("second", second) }
                    await { observed.get().playing }
                } finally { scenario.onActivity { controller?.stop() } }
            }
        } finally {
            first.delete()
            second.delete()
        }
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!predicate() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
        assertTrue("Timed out waiting for playback state", predicate())
    }
}
