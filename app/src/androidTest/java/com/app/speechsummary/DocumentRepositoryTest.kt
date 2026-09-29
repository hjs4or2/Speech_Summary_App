package com.app.speechsummary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.speechsummary.data.DocumentRepository
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentRepositoryTest {
    @Test fun reloadAndReextractionKeepOldDocumentButMarkItStale() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "document_${UUID.randomUUID()}.pcm"
        val file = File(File(context.filesDir, "documents"), "$id.json")
        try {
            val repository = DocumentRepository(context)
            val first = repository.saveTranscript(id, "발표는 내일이야 😀")
            repository.saveDocument(id, first.transcriptHash, "# 발표 회의")
            assertEquals("# 발표 회의", DocumentRepository(context).read(id)?.document)
            // A cancelled or failed re-extraction has no save call and cannot erase the old result.
            assertEquals("발표는 내일이야 😀", DocumentRepository(context).read(id)?.transcript)
            assertFalse(DocumentRepository(context).read(id)!!.stale)
            repository.saveTranscript(id, "발표는 다음 주야 😀")
            val reloaded = DocumentRepository(context).read(id)!!
            assertEquals("발표는 다음 주야 😀", reloaded.transcript)
            assertEquals("# 발표 회의", reloaded.document)
            assertTrue(reloaded.stale)
        } finally { file.delete() }
    }

    @Test fun corruptEntryDoesNotHideOtherDocuments() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val good = "good_${UUID.randomUUID()}.pcm"
        val bad = "bad_${UUID.randomUUID()}.pcm"
        val dir = File(context.filesDir, "documents").also { it.mkdirs() }
        try {
            val repository = DocumentRepository(context)
            repository.saveTranscript(good, "정상 원문")
            File(dir, "$bad.json").writeText("{broken", Charsets.UTF_8)
            val loaded = DocumentRepository(context).readAll(listOf(bad, good))
            assertEquals("정상 원문", loaded[good]?.transcript)
            assertFalse(loaded.containsKey(bad))
        } finally {
            File(dir, "$good.json").delete()
            File(dir, "$bad.json").delete()
        }
    }
}
