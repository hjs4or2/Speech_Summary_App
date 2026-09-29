package com.app.speechsummary

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.app.speechsummary.ui.SettingsScreen
import com.app.speechsummary.ui.ModelSetupScreen
import com.app.speechsummary.ui.SpeechScreen
import com.app.speechsummary.ui.SpeechViewModel
import com.app.speechsummary.ui.theme.SpeechSummaryTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: SpeechViewModel

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importMedia(uri)
    }
    private val modelLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.installModel(uri)
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.toggleRecording() else viewModel.microphonePermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[SpeechViewModel::class.java]
        setContent {
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var showSetup by rememberSaveable { mutableStateOf(true) }
            BackHandler(showSettings) { showSettings = false }
            BackHandler(showSetup && !showSettings && viewModel.state.requiredMissingModels.isNotEmpty()) { showSetup = false }
            SpeechSummaryTheme {
                if (showSettings) SettingsScreen(
                    state = viewModel.state,
                    onBack = { showSettings = false },
                    onParallelToggle = viewModel::setParallelEnabled,
                    onDiarizationEngineChange = viewModel::setDiarizationEngine,
                    onWhisperModelChange = viewModel::setWhisperModel,
                    onImportModel = { modelLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                    onDownloadModels = viewModel::downloadModels,
                    onCancelDownload = viewModel::cancelModelDownload
                ) else if (showSetup && viewModel.state.requiredMissingModels.isNotEmpty()) ModelSetupScreen(
                    state = viewModel.state,
                    onDownloadModels = viewModel::downloadModels,
                    onCancelDownload = viewModel::cancelModelDownload,
                    onWhisperModelChange = viewModel::setWhisperModel,
                    onLater = { showSetup = false }
                ) else SpeechScreen(
                    state = viewModel.state,
                    onRecordClick = {
                        if (viewModel.state.recording ||
                            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.toggleRecording()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onImportClick = { importLauncher.launch(arrayOf("audio/*", "video/*")) },
                    onExtractText = viewModel::extractText,
                    onTogglePlayback = viewModel::togglePlayback,
                    onCancelText = viewModel::cancelTranscription,
                    onCreateDocument = viewModel::createDocument,
                    onCancelDocument = viewModel::cancelDocument,
                    onRenameTitle = viewModel::renameTitle,
                    onSpeakerCountChange = viewModel::setSpeakerCount,
                    onSettingsClick = { showSettings = true },
                )
            }
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations) viewModel.pausePlayback()
        super.onStop()
    }
}
