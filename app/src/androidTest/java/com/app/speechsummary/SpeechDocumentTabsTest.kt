package com.app.speechsummary

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.app.speechsummary.data.DocumentRepository
import com.app.speechsummary.data.SpeechItem
import com.app.speechsummary.data.StoredDocument
import com.app.speechsummary.ui.ModelStatus
import com.app.speechsummary.ui.SpeechScreen
import com.app.speechsummary.ui.SpeechUiState
import com.app.speechsummary.ui.theme.SpeechSummaryTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SpeechDocumentTabsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun originalAndDocumentTabsKeepTheirOwnContentAndAction() {
        val id = "render-only.pcm"
        val transcript = "A: 다음 주 화요일에 보고서를 제출합시다.\nB: 좋습니다."
        val document = "# 보고서 제출 일정 회의\n\n## 시간순 논의\n- 다음 주 화요일 제출을 논의함\n\n## 결정\n- 화요일 보고서 제출\n\n## 할 일\n- 원문에서 확인된 내용 없음"
        val hash = DocumentRepository.hash(transcript)
        var requested: String? = null
        compose.setContent {
            SpeechSummaryTheme {
                SpeechScreen(
                    state = SpeechUiState(
                        files = listOf(SpeechItem(id, "보고서 일정 회의", 32000, 2)),
                        transcripts = mapOf(id to transcript),
                        documents = mapOf(id to StoredDocument(transcript, hash, document, hash)),
                        modelStatus = ModelStatus.READY
                    ),
                    onRecordClick = {}, onImportClick = {}, onExtractText = {},
                    onTogglePlayback = {}, onCancelText = {},
                    onCreateDocument = { requested = it }, onCancelDocument = {},
                    onRenameTitle = { _, _ -> }, onSpeakerCountChange = { _, _ -> },
                    onSettingsClick = {}
                )
            }
        }
        compose.onNodeWithText("전체 텍스트").assertIsSelected()
        compose.onNodeWithText(transcript).assertExists()
        compose.onNodeWithText("문서화").performClick().assertIsSelected()
        compose.onNodeWithText(document).assertExists()
        compose.onNodeWithText(transcript).assertDoesNotExist()
        compose.onNodeWithText("문서 다시 생성").performClick()
        assertEquals(id, requested)
        // Render-only fixture: never inserted into the user's recording list.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "document-tabs-test.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("전체 텍스트").performClick().assertIsSelected()
        compose.onNodeWithText(transcript).assertExists()
    }
}
