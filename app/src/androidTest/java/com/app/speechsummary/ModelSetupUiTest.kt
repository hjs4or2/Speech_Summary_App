package com.app.speechsummary

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.app.speechsummary.data.ModelKind
import com.app.speechsummary.ui.ModelSetupScreen
import com.app.speechsummary.ui.SpeechUiState
import com.app.speechsummary.ui.theme.SpeechSummaryTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ModelSetupUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun setupExplainsStorageAndAllowsRecordingBeforeModels() {
        var requestedMobileData = true
        var deferred = false
        compose.setContent {
            SpeechSummaryTheme {
                ModelSetupScreen(
                    SpeechUiState(modelsChecking = false, missingModels = ModelKind.entries.toSet()),
                    onDownloadModels = { requestedMobileData = it },
                    onCancelDownload = {},
                    onLater = { deferred = true }
                )
            }
        }
        compose.onNodeWithText("모델 다운로드 설정").assertExists()
        compose.onNodeWithText("모델 다운로드").performScrollTo().performClick()
        assertFalse("Mobile data must require explicit consent", requestedMobileData)
        compose.onNodeWithText("나중에 · 녹음 화면으로").performScrollTo().performClick()
        assertTrue(deferred)
    }

    @Test fun activeDownloadShowsProgressAndCancel() {
        var cancelled = false
        compose.setContent {
            SpeechSummaryTheme {
                ModelSetupScreen(
                    SpeechUiState(modelsChecking = false, downloadingModels = true,
                        downloadModel = ModelKind.WHISPER, downloadProgressBytes = 100_000_000),
                    onDownloadModels = {}, onCancelDownload = { cancelled = true }, onLater = {}
                )
            }
        }
        compose.onNodeWithText("다운로드 중단").performScrollTo().performClick()
        assertTrue(cancelled)
    }
}
