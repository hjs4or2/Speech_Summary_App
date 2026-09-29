package com.app.speechsummary

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.app.speechsummary.audio.PlaybackState
import com.app.speechsummary.data.SpeechItem
import com.app.speechsummary.ui.ExtractionPhase
import com.app.speechsummary.ui.ExtractionStatus
import com.app.speechsummary.ui.SpeechScreen
import com.app.speechsummary.ui.SpeechUiState
import com.app.speechsummary.ui.theme.SpeechSummaryTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SpeechPlaybackControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun playbackAndExtractionAreSeparateActionsForEachFile() {
        var playbackId: String? = null
        var extractionId: String? = null
        compose.setContent {
            SpeechSummaryTheme {
                SpeechScreen(
                    state = SpeechUiState(
                        files = listOf(
                            SpeechItem("recording", "녹음한 음성", 32_000),
                            SpeechItem("imported", "가져온 회의 영상", 32_000)
                        ),
                        playback = PlaybackState(itemId = "recording", playing = true)
                    ),
                    onRecordClick = {},
                    onImportClick = {},
                    onExtractText = { extractionId = it },
                    onTogglePlayback = { playbackId = it },
                    onCancelText = {},
                    onCreateDocument = {},
                    onCancelDocument = {},
                    onRenameTitle = { _, _ -> },
                    onSpeakerCountChange = { _, _ -> },
                    onSettingsClick = {}
                )
            }
        }
        compose.onNodeWithContentDescription("오디오 일시정지").performClick()
        assertEquals("recording", playbackId)
        compose.onAllNodesWithContentDescription("텍스트 추출")[0].performClick()
        assertEquals("recording", extractionId)
        compose.onNodeWithContentDescription("오디오 재생").performClick()
        assertEquals("imported", playbackId)
        compose.onAllNodesWithContentDescription("텍스트 추출")[1].performClick()
        assertEquals("imported", extractionId)

        // Render-only fixtures; no audio or sample entries are saved in the app.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "playback-controls-test.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun speakerCountControlsShowAutomaticAndManualSettings() {
        var setting by mutableStateOf<Int?>(null)
        var extraction by mutableStateOf<ExtractionStatus?>(null)
        var changedId: String? = null
        compose.setContent {
            SpeechSummaryTheme {
                SpeechScreen(
                    state = SpeechUiState(
                        files = listOf(
                            SpeechItem("recording", "녹음한 음성", 32_000),
                            SpeechItem("imported", "가져온 회의 영상", 32_000, setting)
                        ),
                        extractions = extraction?.let { mapOf("imported" to it) }.orEmpty()
                    ),
                    onRecordClick = {},
                    onImportClick = {},
                    onExtractText = {},
                    onTogglePlayback = {},
                    onCancelText = {},
                    onCreateDocument = {},
                    onCancelDocument = {},
                    onRenameTitle = { _, _ -> },
                    onSpeakerCountChange = { id, count ->
                        changedId = id
                        setting = count
                    },
                    onSettingsClick = {}
                )
            }
        }

        compose.onAllNodesWithText("화자 수: 자동 감지")[1].performClick()
        compose.onNodeWithText("직접 지정").performClick()
        compose.onNodeWithText("저장").assertIsNotEnabled()
        compose.onNodeWithText("화자 수 (1~99명)").performTextInput("2")
        compose.onNodeWithText("저장").performClick()
        assertEquals("imported", changedId)
        assertEquals(2, setting)
        compose.onNodeWithText("화자 수: 자동 감지").assertExists()
        compose.onNodeWithText("화자 수: 2명").assertExists()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "speaker-count-controls-test.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle {
            extraction = ExtractionStatus(ExtractionPhase.QUEUED, totalSeconds = 1)
        }
        compose.onNodeWithText("화자 수: 2명").assertIsNotEnabled()
    }
}
