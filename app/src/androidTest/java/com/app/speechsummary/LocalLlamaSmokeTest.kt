package com.app.speechsummary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.speechsummary.data.LocalModelRepository
import com.app.speechsummary.llm.LocalLlama
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in: requires the official model inside target app files/models. */
@RunWith(AndroidJUnit4::class)
class LocalLlamaSmokeTest {
    @Test fun koreanInferenceAndUtf8EmojiRoundTrip() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("llmSmoke") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val models = LocalModelRepository(context)
        assertTrue("Official GGUF is not installed or has the wrong SHA-256", models.isInstalled())
        LocalLlama().use { llama ->
            assertEquals("한글 회의 😀", llama.utf8RoundTripForTest("한글 회의 😀"))
            llama.load(models.modelFile)
            val answer = llama.generate(
                "한국어로 한 문장만 답해. 생각 과정은 출력하지 마. /no_think",
                "회의에서 다음 주 화요일에 보고서를 제출하기로 결정했어. 결정 내용을 한 문장으로 말해. /no_think",
                256
            )
            assertTrue(answer.complete)
            assertTrue(answer.text.any { it in '가'..'힣' })
        }
    }
}
