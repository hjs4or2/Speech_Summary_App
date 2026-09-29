package com.app.speechsummary

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.speechsummary.data.LocalModelRepository
import com.app.speechsummary.llm.LocalLlama
import com.app.speechsummary.llm.MeetingDocumenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Model tests are opt-in; ordinary UI tests never load the LLM. */
@RunWith(AndroidJUnit4::class)
class MeetingDocumenterIntegrationTest {
    private fun model(): LocalModelRepository {
        assumeTrue(InstrumentationRegistry.getArguments().getString("llmDocumentSmoke") == "true")
        return LocalModelRepository(InstrumentationRegistry.getInstrumentation().targetContext)
            .also { assertTrue("Verified Qwen model required", it.isInstalled()) }
    }

    @Test fun koreanMeetingProducesDocumentWithSourceEvidence() = runBlocking {
        val models = model()
        val transcript = "A: 보고서 제출일을 정하겠습니다.\nB: 다음 주 화요일에 보고서를 제출합시다.\nA: 좋습니다. 다음 주 화요일에 보고서를 제출하기로 결정했습니다."
        val output = MeetingDocumenter(models).document(transcript) { _, _ -> }
        Log.i("DocumentSmoke", "Generated from synthetic fixture only:\n$output")
        assertTrue(output.startsWith("# "))
        assertTrue("Topic must identify the subject, not a generic label",
            output.lineSequence().first().contains("보고서"))
        assertTrue(output.contains("## 시간순 논의"))
        assertTrue(output.contains("보고서"))
        assertTrue(output.contains("화요일"))
        assertTrue("Explicitly agreed deadline must appear under decisions",
            output.substringAfter("## 결정", "").substringBefore("## 할 일").contains("화요일"))
        // Exact excerpts are displayed without a redundant "원문:" duplicate.
        assertTrue(output.contains(transcript.lineSequence().last()))
        assertFalse(output.contains("<think>"))
        assertTrue("No assigned action exists in this fixture",
            output.substringAfter("## 할 일", "").contains("원문에서 확인된 내용 없음"))
    }

    @Test fun abortBeforeGenerationDoesNotProduceCompletedOutput() {
        val models = model()
        LocalLlama().use { llama ->
            llama.load(models.modelFile)
            llama.abort()
            var cancelled = false
            try {
                llama.generate("한국어로 답해. /no_think", "회의 내용을 요약해. /no_think", 32)
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue("Aborted native session must not return a completed result", cancelled)
        }
    }
}
