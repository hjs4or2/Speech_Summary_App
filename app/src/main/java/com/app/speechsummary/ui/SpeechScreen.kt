package com.app.speechsummary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.res.painterResource
import com.app.speechsummary.R
import com.app.speechsummary.data.DiarizationEngine
import com.app.speechsummary.data.ModelKind
import com.app.speechsummary.data.SpeechItem

@Composable
fun SpeechScreen(
    state: SpeechUiState,
    onRecordClick: () -> Unit,
    onImportClick: () -> Unit,
    onExtractText: (String) -> Unit,
    onTogglePlayback: (String) -> Unit,
    onCancelText: (String) -> Unit,
    onCreateDocument: (String) -> Unit,
    onCancelDocument: (String) -> Unit,
    onRenameTitle: (String, String) -> Unit,
    onSpeakerCountChange: (String, Int?) -> Unit,
    onSettingsClick: () -> Unit
) {
    val selectedTabs = remember { mutableStateMapOf<String, Int>() }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editedTitle by remember { mutableStateOf("") }
    var editingSpeakerId by remember { mutableStateOf<String?>(null) }
    var manualSpeakerCount by remember { mutableStateOf(false) }
    var editedSpeakerCount by remember { mutableStateOf("") }

    LaunchedEffect(editingSpeakerId, state.extractions) {
        editingSpeakerId?.let { if (state.extractions.containsKey(it)) editingSpeakerId = null }
    }
    val speakerItem = state.files.firstOrNull { it.id == editingSpeakerId && !state.extractions.containsKey(it.id) }
    if (speakerItem != null) {
        val count = editedSpeakerCount.toIntOrNull()
        val valid = count != null && count in 1..99 && state.diarizationEngine.acceptsSpeakerCount(count)
        AlertDialog(
            onDismissRequest = { editingSpeakerId = null },
            title = { Text("화자 수 설정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "자동 감지", true to "직접 지정").forEach { (manual, label) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(manualSpeakerCount == manual, role = Role.RadioButton) {
                                manualSpeakerCount = manual
                            }.height(48.dp), verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = manualSpeakerCount == manual, onClick = null)
                            Text(label)
                        }
                    }
                    if (manualSpeakerCount) {
                        OutlinedTextField(
                            value = editedSpeakerCount, onValueChange = { editedSpeakerCount = it },
                            label = { Text("화자 수 (1~99명)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, isError = !valid
                        )
                        if (!valid) Text("유효한 화자 수를 입력해 줘.", color = MaterialTheme.colorScheme.error)
                        if (state.diarizationEngine == DiarizationEngine.NEMOTRON)
                            Text("Nemotron 3는 최대 8명까지 지원해. 9명 이상은 설정에서 기존 엔진을 선택해 줘.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("변경한 설정은 다음 텍스트 추출부터 적용돼.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSpeakerCountChange(speakerItem.id, if (manualSpeakerCount) count else null)
                    editingSpeakerId = null
                }, enabled = !manualSpeakerCount || valid) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { editingSpeakerId = null }) { Text("취소") } }
        )
    }
    if (editingId != null) AlertDialog(
        onDismissRequest = { editingId = null },
        title = { Text("제목 변경") },
        text = { OutlinedTextField(editedTitle, { editedTitle = it }, label = { Text("제목") }, singleLine = true, isError = editedTitle.isBlank()) },
        confirmButton = { TextButton(onClick = { editingId?.let { onRenameTitle(it, editedTitle) }; editingId = null }, enabled = editedTitle.isNotBlank()) { Text("저장") } },
        dismissButton = { TextButton(onClick = { editingId = null }) { Text("취소") } }
    )

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SPEECH SUMMARY", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text("나의 음성 기록", style = MaterialTheme.typography.headlineMedium)
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "설정 및 로그")
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("새 기록 만들기", style = MaterialTheme.typography.titleLarge)
                        Text("음성을 녹음하거나 파일을 가져와 텍스트로 정리해.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onRecordClick, modifier = Modifier.fillMaxWidth().height(54.dp), enabled = !state.recordingBusy && !state.importing) {
                            Text(if (state.recording) "녹음 중지" else "녹음 시작")
                        }
                        OutlinedButton(onClick = onImportClick, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !state.importing && !state.recording && !state.recordingBusy) {
                            Text(if (state.importing) "가져오는 중…" else "음성 가져오기 (오디오·영상)")
                        }
                        if (state.recording) Text("녹음 중 · 중지하면 기록에 저장돼.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("저장된 기록", style = MaterialTheme.typography.titleLarge)
                    Text("${state.files.size}개의 음성 파일", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.files.isEmpty()) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("아직 저장된 기록이 없어", style = MaterialTheme.typography.titleMedium)
                        Text("첫 녹음을 시작하거나 가지고 있는 음성 파일을 가져와 줘.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(state.files, key = { it.id }) { file ->
                SpeechCard(
                    file, state, selectedTabs[file.id] ?: 0, { selectedTabs[file.id] = it },
                    onEdit = { editingId = file.id; editedTitle = file.name },
                    onSpeakerEdit = {
                        editingSpeakerId = file.id
                        manualSpeakerCount = file.speakerCount != null
                        editedSpeakerCount = file.speakerCount?.toString() ?: ""
                    },
                    onPlay = { onTogglePlayback(file.id) },
                    onExtract = { if (state.extractions[file.id] == null) onExtractText(file.id) else onCancelText(file.id) },
                    onCreateDocument = { onCreateDocument(file.id) }, onCancelDocument = { onCancelDocument(file.id) }
                )
            }
        }
    }
}

@Composable
private fun SpeechCard(
    file: SpeechItem, state: SpeechUiState, tab: Int, onTab: (Int) -> Unit,
    onEdit: () -> Unit, onSpeakerEdit: () -> Unit, onPlay: () -> Unit, onExtract: () -> Unit,
    onCreateDocument: () -> Unit, onCancelDocument: () -> Unit
) {
    val extraction = state.extractions[file.id]
    val transcript = state.transcripts[file.id]
    val stored = state.documents[file.id]
    val documentJob = state.documentJobs[file.id]
    val playback = state.playback.takeIf { it.itemId == file.id }
    val status = when {
        extraction != null -> when (extraction.phase) {
            ExtractionPhase.QUEUED -> "대기 중"
            ExtractionPhase.LOADING -> "모델 준비 중"
            ExtractionPhase.RUNNING -> "텍스트 추출 중"
            ExtractionPhase.DIARIZING -> "화자 구분 중"
            ExtractionPhase.CANCELLING -> "중지 중"
        }
        transcript != null -> "추출 완료"
        else -> "추출 전"
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(formatSize(file.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                            Text(status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Edit, contentDescription = "제목 변경") }
            }
            TextButton(onClick = onSpeakerEdit, enabled = extraction == null) {
                Text("화자 수: ${file.speakerCount?.let { "${it}명" } ?: "자동 감지"}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onPlay, modifier = Modifier.weight(1f).height(48.dp), enabled = !state.recording && !state.recordingBusy && playback?.preparing != true) {
                    if (playback?.preparing == true) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(if (playback?.playing == true) painterResource(R.drawable.ic_pause) else androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.PlayArrow), contentDescription = if (playback?.playing == true) "오디오 일시정지" else "오디오 재생", modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (playback?.playing == true) "일시정지" else "재생")
                }
                OutlinedButton(
                    onClick = onExtract, modifier = Modifier.weight(1f).height(48.dp),
                    enabled = if (extraction == null) !state.importing && !state.modelsChecking && state.requiredMissingModels.all { it == ModelKind.QWEN } else extraction.phase != ExtractionPhase.CANCELLING
                ) {
                    Icon(if (extraction == null) painterResource(R.drawable.ic_extract_text) else androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Close), contentDescription = if (extraction == null) "텍스트 추출" else "텍스트 추출 중지", modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (extraction == null) "텍스트 추출" else "중지")
                }
            }
            playback?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (extraction != null) {
                if (extraction.phase != ExtractionPhase.QUEUED) {
                    val progress = if (extraction.totalSeconds > 0 && extraction.processedSeconds > 0)
                        (extraction.processedSeconds.toFloat() / extraction.totalSeconds).coerceIn(0f, 1f) else null
                    if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                Text(when (extraction.phase) {
                    ExtractionPhase.QUEUED -> "대기 중 · 최대 ${state.parallelLimit}개 동시 추출"
                    ExtractionPhase.LOADING -> "음성 인식 모델을 불러오는 중 · ${formatDuration(extraction.elapsedSeconds)}"
                    ExtractionPhase.DIARIZING -> "화자 구간 분석 중 · ${formatDuration(extraction.elapsedSeconds)}"
                    ExtractionPhase.CANCELLING -> "안전하게 중지하는 중…"
                    ExtractionPhase.RUNNING -> "${extraction.processedSeconds}/${extraction.totalSeconds}초 처리 · ${formatDuration(extraction.elapsedSeconds)} 경과"
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (transcript != null) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { onTab(0) }, text = { Text("전체 텍스트") })
                    Tab(selected = tab == 1, onClick = { onTab(1) }, text = { Text("문서화") })
                }
                if (tab == 0) {
                    SelectionContainer { Text(transcript.ifBlank { "인식된 텍스트가 없어." }, style = MaterialTheme.typography.bodyLarge) }
                } else {
                    Text("자동 생성 결과는 원문과 함께 확인해 줘.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (stored?.stale == true) Text("원문이 바뀌었어. 문서를 다시 생성해 줘.", color = MaterialTheme.colorScheme.error)
                    stored?.document?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodyLarge) } }
                    if (documentJob == null) {
                        Button(onClick = onCreateDocument, enabled = state.modelStatus == ModelStatus.READY && extraction == null) {
                            Text(if (stored?.document == null) "문서 생성" else "문서 다시 생성")
                        }
                        if (state.modelStatus != ModelStatus.READY) Text("설정에서 Qwen 로컬 모델을 설치해 줘.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(when (documentJob.phase) {
                            DocumentPhase.QUEUED -> "문서화 대기 중"
                            DocumentPhase.LOADING -> "문서화 모델을 불러오는 중"
                            DocumentPhase.RUNNING -> "문서화 중 · ${documentJob.completedParts}/${documentJob.totalParts}구간"
                            DocumentPhase.CANCELLING -> "문서화 취소 중"
                        })
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton(onClick = onCancelDocument, enabled = documentJob.phase != DocumentPhase.CANCELLING) { Text("취소") }
                    }
                    state.documentErrors[file.id]?.let { Text("문서화 실패: $it", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = if (bytes >= 1_000_000) "${bytes / 1_000_000} MB" else "${bytes / 1_000} KB"
private fun formatDuration(seconds: Long): String = if (seconds >= 60) "${seconds / 60}분 ${seconds % 60}초" else "${seconds}초"
