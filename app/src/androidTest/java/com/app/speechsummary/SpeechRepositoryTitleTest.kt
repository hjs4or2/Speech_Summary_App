package com.app.speechsummary

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.speechsummary.data.SpeechRepository
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechRepositoryTitleTest {
    @Test fun customTitlePreservesSpecialCharactersAfterReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(File(context.filesDir, "recordings"), "title_test_${UUID.randomUUID()}.pcm")
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(0, 0))
        try {
            SpeechRepository(context).renameTitle(file.name, "  회의: A/B? ✅  ")
            val reloaded = SpeechRepository(context).list().first { it.id == file.name }
            assertEquals("회의: A/B? ✅", reloaded.name)
        } finally {
            context.getSharedPreferences("speech_titles", Context.MODE_PRIVATE)
                .edit().remove(file.name).commit()
            file.delete()
        }
    }
}
