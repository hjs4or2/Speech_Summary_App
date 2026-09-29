package com.app.speechsummary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.speechsummary.data.LocalModelRepository
import com.app.speechsummary.data.ModelCatalog
import com.app.speechsummary.data.ModelKind
import com.app.speechsummary.data.DiarizationEngine
import com.app.speechsummary.data.WhisperModel

@Composable
fun SettingsScreen(
    state: SpeechUiState,
    onBack: () -> Unit,
    onParallelToggle: (Boolean) -> Unit,
    onDiarizationEngineChange: (DiarizationEngine) -> Unit,
    onWhisperModelChange: (WhisperModel) -> Unit,
    onImportModel: () -> Unit,
    onDownloadModels: (Boolean) -> Unit,
    onCancelDownload: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "돌아가기")
            }
            Text("설정", style = MaterialTheme.typography.headlineMedium)
        }
        WhisperModelPanel(state, onWhisperModelChange)
        ModelSetupPanel(state, onDownloadModels, onCancelDownload)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("화자 구분 엔진", style = MaterialTheme.typography.titleMedium)
                DiarizationEngine.entries.forEach { engine ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.diarizationEngine == engine,
                            onClick = { onDiarizationEngineChange(engine) },
                            enabled = !state.downloadingModels && state.extractions.isEmpty())
                        Text(if (engine == DiarizationEngine.SHERPA) "기존 엔진 · 많은 화자 회의" else "NVIDIA Nemotron 3 · 최대 8명")
                    }
                }
                if (state.diarizationEngine == DiarizationEngine.NEMOTRON) {
                    Text("최대 8명까지 구분해. 9명 이상으로 지정한 파일은 추출 전에 거부돼. 큰 회의는 기존 엔진을 선택해 줘.",
                        color = MaterialTheme.colorScheme.error)
                }
                if (state.extractions.isNotEmpty()) {
                    Text("진행 중인 텍스트 추출이 끝나면 엔진을 바꿀 수 있어.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("동시 추출 2개", modifier = Modifier.weight(1f))
                    Switch(checked = state.parallelLimit == 2, onCheckedChange = onParallelToggle)
                }
                Text(
                    "켜면 최대 2개 파일을 함께 처리해. CPU와 메모리를 더 써서 각 파일은 느려질 수 있어.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("로컬 문서화 모델", style = MaterialTheme.typography.titleMedium)
                Text("${LocalModelRepository.DISPLAY_NAME} · Apache-2.0 · 약 1.12 GB")
                Text(when (state.modelStatus) {
                    ModelStatus.CHECKING -> "설치된 모델 검증 중"
                    ModelStatus.MISSING -> "모델이 설치되지 않았어"
                    ModelStatus.INSTALLING -> "크기와 SHA-256 확인 중: ${state.modelProgressBytes / 1_000_000} MB"
                    ModelStatus.READY -> "모델 검증 완료"
                    ModelStatus.ERROR -> "모델 설치 실패"
                })
                if (state.modelStatus == ModelStatus.CHECKING || state.modelStatus == ModelStatus.INSTALLING) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                state.modelError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("공식 파일: huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF")
                Text("SHA-256: ${LocalModelRepository.SHA256}",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = onImportModel,
                    enabled = state.modelStatus != ModelStatus.CHECKING && state.modelStatus != ModelStatus.INSTALLING &&
                        !state.downloadingModels &&
                        state.documentJobs.isEmpty()) { Text("GGUF 파일 가져오기") }
            }
        }
        Text("로그", style = MaterialTheme.typography.titleMedium)
        state.logs.forEach { entry ->
            Text(entry, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ModelSetupScreen(
    state: SpeechUiState,
    onDownloadModels: (Boolean) -> Unit,
    onCancelDownload: () -> Unit,
    onWhisperModelChange: (WhisperModel) -> Unit,
    onLater: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("모델 다운로드 설정", style = MaterialTheme.typography.headlineMedium)
        Text("첫 사용 전에 음성 인식, 화자 구분, 문서화 모델을 설치해. 녹음과 재생은 설치 전에도 사용할 수 있어.")
        WhisperModelPanel(state, onWhisperModelChange)
        ModelSetupPanel(state, onDownloadModels, onCancelDownload)
        TextButton(onClick = onLater) { Text("나중에 · 녹음 화면으로") }
    }
}

@Composable
private fun WhisperModelPanel(state: SpeechUiState, onChange: (WhisperModel) -> Unit) {
    val canChange = !state.downloadingModels && state.extractions.isEmpty()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("음성 인식 모델", style = MaterialTheme.typography.titleMedium)
            WhisperModel.entries.forEach { model ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.whisperModel == model,
                        onClick = { onChange(model) },
                        enabled = canChange
                    )
                    Text(model.displayName)
                }
            }
            Text("빠른 모드: 기존 다국어 base Q5_1 · 약 60 MB")
            Text("정확도 우선: 다국어 표준 small · 약 488 MB. 인식 정확도를 우선하지만 저장 공간과 메모리를 더 쓰고 처리 시간이 길어질 수 있어.",
                style = MaterialTheme.typography.bodySmall)
            if (!canChange) Text("다운로드 또는 텍스트 추출이 끝나면 모델을 바꿀 수 있어.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ModelSetupPanel(
    state: SpeechUiState,
    onDownloadModels: (Boolean) -> Unit,
    onCancelDownload: () -> Unit
) {
    var allowMobileData by rememberSaveable { mutableStateOf(false) }
    val selectedAssets = ModelCatalog.assets.filter { it.kind in state.diarizationEngine.requiredModels(state.whisperModel) }
    val totalBytes = selectedAssets.sumOf { it.bytes }
    val remainingBytes = selectedAssets.filter { it.kind in state.missingModels }.sumOf { it.bytes }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("필요한 모델", style = MaterialTheme.typography.titleMedium)
            Text("남은 다운로드 ${remainingBytes / 1_000_000} MB · 전체 ${totalBytes / 1_000_000} MB · 앱 전용 내부 저장소")
            selectedAssets.forEach { asset ->
                val status = when {
                    state.modelsChecking -> "확인 중"
                    asset.kind !in state.missingModels -> "설치 완료"
                    state.downloadModel == asset.kind -> "다운로드 중"
                    else -> "설치 필요"
                }
                Text("${asset.displayName} · ${asset.bytes / 1_000_000} MB · $status")
            }
            if (state.requiredMissingModels.isNotEmpty()) {
                Text("Wi-Fi 사용을 권장해. 아래에 동의하면 모바일 데이터에서도 다운로드할 수 있어.",
                    style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allowMobileData, onCheckedChange = { allowMobileData = it },
                        enabled = !state.downloadingModels)
                    Text("모바일 데이터 사용에 동의해 (남은 ${remainingBytes / 1_000_000} MB)")
                }
            }
            if (state.downloadingModels) {
                LinearProgressIndicator(
                    progress = { (state.downloadProgressBytes.toFloat() / totalBytes).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${state.downloadProgressBytes / 1_000_000} / ${totalBytes / 1_000_000} MB")
                Button(onClick = onCancelDownload) { Text("다운로드 중단") }
            } else if (!state.modelsChecking && state.requiredMissingModels.isNotEmpty()) {
                Button(onClick = { onDownloadModels(allowMobileData) },
                    enabled = state.modelStatus != ModelStatus.INSTALLING) {
                    Text(if (state.downloadError == null) "모델 다운로드" else "다시 시도 · 이어받기")
                }
            }
            state.downloadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("다운로드한 모델과 임시 파일은 백업되지 않아. 앱을 삭제하면 함께 삭제되고 재설치 시 다시 다운로드해야 해.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
