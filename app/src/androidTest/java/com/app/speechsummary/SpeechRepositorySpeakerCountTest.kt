package com.app.speechsummary

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.speechsummary.data.SpeechRepository
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechRepositorySpeakerCountTest {
    @Test fun countIsStoredPerFileAndCanReturnToAutomatic() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "recordings").apply { mkdirs() }
        val first = File(directory, "speaker_test_${UUID.randomUUID()}.pcm")
        val second = File(directory, "speaker_test_${UUID.randomUUID()}.pcm")
        try {
            first.writeBytes(byteArrayOf(0, 0))
            second.writeBytes(byteArrayOf(0, 0))
            val repository = SpeechRepository(context)
            assertNull(repository.list().first { it.id == first.name }.speakerCount)
            repository.setSpeakerCount(first.name, 2)
            assertEquals(2, SpeechRepository(context).list().first { it.id == first.name }.speakerCount)
            assertNull(SpeechRepository(context).list().first { it.id == second.name }.speakerCount)
            try {
                repository.setSpeakerCount(first.name, 100)
                fail("100명은 허용되면 안 됩니다.")
            } catch (_: IllegalArgumentException) { }
            assertEquals(2, SpeechRepository(context).list().first { it.id == first.name }.speakerCount)
            repository.setSpeakerCount(first.name, null)
            assertNull(SpeechRepository(context).list().first { it.id == first.name }.speakerCount)
        } finally {
            context.getSharedPreferences("speech_speaker_counts", Context.MODE_PRIVATE)
                .edit().remove(first.name).remove(second.name).commit()
            first.delete()
            second.delete()
        }
    }
}
