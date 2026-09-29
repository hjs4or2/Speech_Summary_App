package com.app.speechsummary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.app.speechsummary.data.DiarizationEngine
import com.app.speechsummary.data.LocalModelRepository
import com.app.speechsummary.data.ModelCatalog
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "돌아가기") }
            Text("설정", style = MaterialTheme.typography.headlineMedium)
        }
        Text("음성 처리", style = MaterialTheme.typography.titleLarge)
        WhisperModelPanel(state, onWhisperModelChange)
        Section("화자 구분 엔진", "음성에서 화자별 구간을 찾아줘.") {
            DiarizationEngine.entries.forEach { engine ->
                val label = if (engine == DiarizationEngine.SHERPA) "기존 엔진 · 화자 수 제한 없음" else "NVIDIA Nemotron 3 · 최대 8명"
                Row(Modifier.fillMaxWidth().selectable(state.diarizationEngine == engine, enabled = !state.downloadingModels && state.extractions.isEmpty(), role = Role.RadioButton) { onDiarizationEngineChange(engine) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.diarizationEngine == engine, onClick = null, enabled = !state.downloadingModels && state.extractions.isEmpty())
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.diarizationEngine == DiarizationEngine.NEMOTRON) Text("9명 이상으로 지정한 파일은 추출할 수 없어. 해당 파일의 화자 수를 바꾸거나 기존 엔진을 선택해 줘.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (state.extractions.isNotEmpty()) Text("진행 중인 추출이 끝나면 엔진을 변경할 수 있어.", style = MaterialTheme.typography.bodySmall)
        }
        Section("처리 성능", "동시 추출은 기기 성능에 따라 느려질 수 있어.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("동시 추출 2개", modifier = Modifier.weight(1f))
                Switch(checked = state.parallelLimit == 2, onCheckedChange = onParallelToggle)
            }
        }
        Text("모델 및 저장 공간", style = MaterialTheme.typography.titleLarge)
        ModelSetupPanel(state, onDownloadModels, onCancelDownload)
        Section("Qwen 로컬 문서화 모델", "음성 텍스트로 문서를 만드는 별도 모델이야.") {
            Text("${LocalModelRepository.DISPLAY_NAME} · 약 1.12 GB", style = MaterialTheme.typography.bodyMedium)
            Text(when (state.modelStatus) {
                ModelStatus.CHECKING -> "설치된 모델 확인 중"
                ModelStatus.MISSING -> "설치 필요"
                ModelStatus.INSTALLING -> "모델 확인 중 · ${state.modelProgressBytes / 1_000_000} MB"
                ModelStatus.READY -> "설치 완료"
                ModelStatus.ERROR -> "설치 실패"
            }, color = MaterialTheme.colorScheme.primary)
            if (state.modelStatus == ModelStatus.CHECKING || state.modelStatus == ModelStatus.INSTALLING) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.modelError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onImportModel, enabled = state.modelStatus != ModelStatus.CHECKING && state.modelStatus != ModelStatus.INSTALLING && !state.downloadingModels && state.documentJobs.isEmpty()) { Text("GGUF 파일 가져오기") }
            Text("공식 파일: huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF", style = MaterialTheme.typography.bodySmall)
            Text("SHA-256: ${LocalModelRepository.SHA256}", style = MaterialTheme.typography.bodySmall)
        }
        Text("로그", style = MaterialTheme.typography.titleLarge)
        Section("처리 기록", "최근 작업의 상태를 확인할 수 있어.") {
            if (state.logs.isEmpty()) Text("표시할 로그가 없어.", style = MaterialTheme.typography.bodyMedium)
            state.logs.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun ModelSetupScreen(
    state: SpeechUiState,
    onDownloadModels: (Boolean) -> Unit,
    onCancelDownload: () -> Unit,
    onWhisperModelChange: (WhisperModel) -> Unit = {},
    onLater: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("모델 다운로드 설정", style = MaterialTheme.typography.headlineMedium)
        Text("음성 인식과 화자 구분에 필요한 모델을 준비해. 녹음과 재생은 설치 전에도 사용할 수 있어.")
        WhisperModelPanel(state, onWhisperModelChange)
        ModelSetupPanel(state, onDownloadModels, onCancelDownload)
        TextButton(onClick = onLater) { Text("나중에 · 녹음 화면으로") }
    }
}

@Composable
private fun WhisperModelPanel(state: SpeechUiState, onChange: (WhisperModel) -> Unit) {
    val enabled = !state.downloadingModels && state.extractions.isEmpty()
    Section("음성 인식 모델", "속도와 정확도에 따라 선택해.") {
        WhisperModel.entries.forEach { model ->
            Row(Modifier.fillMaxWidth().selectable(state.whisperModel == model, enabled = enabled, role = Role.RadioButton) { onChange(model) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = state.whisperModel == model, onClick = null, enabled = enabled)
                Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("base Q5_1 · 약 60 MB · 빠른 처리", style = MaterialTheme.typography.bodySmall)
        Text("small · 약 488 MB · 정확도 우선, 저장 공간과 메모리 사용량 증가", style = MaterialTheme.typography.bodySmall)
        if (!enabled) Text("다운로드나 추출이 끝나면 변경할 수 있어.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ModelSetupPanel(state: SpeechUiState, onDownloadModels: (Boolean) -> Unit, onCancelDownload: () -> Unit) {
    var allowMobileData by rememberSaveable { mutableStateOf(false) }
    val selected = ModelCatalog.assets.filter { it.kind in state.diarizationEngine.requiredModels(state.whisperModel) }
    val total = selected.sumOf { it.bytes }
    val remaining = selected.filter { it.kind in state.missingModels }.sumOf { it.bytes }
    Section("필요한 모델", "남은 다운로드 ${remaining / 1_000_000} MB · 전체 ${total / 1_000_000} MB") {
        selected.forEach { asset ->
            val status = when {
                state.modelsChecking -> "확인 중"
                asset.kind !in state.missingModels -> "설치 완료"
                state.downloadModel == asset.kind -> "다운로드 중"
                else -> "설치 필요"
            }
            Text("${asset.displayName} · ${asset.bytes / 1_000_000} MB · $status", style = MaterialTheme.typography.bodyMedium)
        }
        if (state.requiredMissingModels.isNotEmpty()) {
            Text("Wi-Fi 사용을 권장해. 동의하면 모바일 데이터로도 다운로드할 수 있어.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = allowMobileData, onCheckedChange = { allowMobileData = it }, enabled = !state.downloadingModels)
                Text("모바일 데이터 사용에 동의 (${remaining / 1_000_000} MB)", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.downloadingModels) {
            LinearProgressIndicator(progress = { if (total > 0) (state.downloadProgressBytes.toFloat() / total).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
            Text("${state.downloadProgressBytes / 1_000_000} / ${total / 1_000_000} MB")
            Button(onClick = onCancelDownload) { Text("다운로드 중단") }
        } else if (!state.modelsChecking && state.requiredMissingModels.isNotEmpty()) {
            Button(onClick = { onDownloadModels(allowMobileData) }, enabled = state.modelStatus != ModelStatus.INSTALLING) {
                Text(if (state.downloadError == null) "모델 다운로드" else "다시 시도 · 이어받기")
            }
        }
        state.downloadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("모델과 임시 파일은 백업되지 않아. 앱을 삭제하면 다시 다운로드해야 해.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Section(title: String, description: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
