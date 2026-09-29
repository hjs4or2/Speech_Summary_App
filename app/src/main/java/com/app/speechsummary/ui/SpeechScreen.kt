package com.app.speechsummary.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.speechsummary.R
import com.app.speechsummary.data.ModelKind
import com.app.speechsummary.data.DiarizationEngine

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

    val speakerItem = state.files.firstOrNull {
        it.id == editingSpeakerId && !state.extractions.containsKey(it.id)
    }
    if (speakerItem != null) {
        val count = editedSpeakerCount.toIntOrNull()
        val validCount = count != null && count in 1..99
        AlertDialog(
            onDismissRequest = { editingSpeakerId = null },
            title = { Text("화자 수 설정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = !manualSpeakerCount,
                            onClick = { manualSpeakerCount = false },
                            role = Role.RadioButton
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = !manualSpeakerCount, onClick = null)
                        Text("자동 감지")
                    }
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = manualSpeakerCount,
                            onClick = { manualSpeakerCount = true },
                            role = Role.RadioButton
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = manualSpeakerCount, onClick = null)
                        Text("직접 지정")
                    }
                    if (manualSpeakerCount) {
                        OutlinedTextField(
                            value = editedSpeakerCount,
                            onValueChange = { editedSpeakerCount = it },
                            label = { Text("화자 수 (1~99명)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = !validCount
                        )
                        if (!validCount) Text("1~99 사이의 정수를 입력해 주세요.", color = MaterialTheme.colorScheme.error)
                        if (state.diarizationEngine == DiarizationEngine.NEMOTRON && count != null && count > 8) {
                            Text("Nemotron 3는 최대 8명까지 지원해. 추출하려면 기존 엔진을 선택해 줘.",
                                color = MaterialTheme.colorScheme.error)
                        }
                        if (state.diarizationEngine == DiarizationEngine.NEMOTRON) {
                            Text("Nemotron은 실제 화자 수를 자동 감지해. 직접 지정한 수는 8명 상한 검사에만 사용돼.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("변경한 설정은 다음 텍스트 추출부터 적용돼요. 기존 추출 결과는 그대로 유지돼요.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSpeakerCountChange(speakerItem.id, if (manualSpeakerCount) count else null)
                        editingSpeakerId = null
                    },
                    enabled = !manualSpeakerCount || validCount
                ) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { editingSpeakerId = null }) { Text("취소") } }
        )
    }

    if (editingId != null) {
        AlertDialog(
            onDismissRequest = { editingId = null },
            title = { Text("제목 변경") },
            text = {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text("제목") },
                    singleLine = true,
                    isError = editedTitle.isBlank()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingId?.let { onRenameTitle(it, editedTitle) }
                        editingId = null
                    },
                    enabled = editedTitle.isNotBlank()
                ) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { editingId = null }) { Text("취소") } }
        )
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Speech Summary", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "설정 및 로그")
            }
        }
        if (state.diarizationEngine == DiarizationEngine.NEMOTRON) {
            Text("Nemotron 3 화자 구분 · 최대 8명. 9명 이상 회의는 설정에서 기존 엔진을 선택해 줘.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = onRecordClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.recordingBusy && !state.importing
        ) { Text(if (state.recording) "녹음 중지" else "녹음 시작") }
        Button(
            onClick = onImportClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.importing && !state.recording && !state.recordingBusy
        ) { Text(if (state.importing) "변환 중..." else "음성 가져오기 (오디오·영상)") }

        Text("음성 파일", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.files, key = { it.id }) { file ->
                val extraction = state.extractions[file.id]
                val transcript = state.transcripts[file.id]
                val stored = state.documents[file.id]
                val documentJob = state.documentJobs[file.id]
                val playback = state.playback.takeIf { it.itemId == file.id }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).clickable(
                                    role = Role.Button,
                                    onClickLabel = "제목 변경"
                                ) {
                                    editingId = file.id
                                    editedTitle = file.name
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    file.name,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Default.Edit, contentDescription = "제목 변경", modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onTogglePlayback(file.id) },
                                enabled = !state.recording && !state.recordingBusy && playback?.preparing != true
                            ) {
                                when {
                                    playback?.preparing == true -> CircularProgressIndicator(
                                        Modifier.size(24.dp), strokeWidth = 2.dp
                                    )
                                    playback?.playing == true -> Icon(
                                        painterResource(R.drawable.ic_pause), contentDescription = "오디오 일시정지"
                                    )
                                    else -> Icon(Icons.Default.PlayArrow, contentDescription = "오디오 재생")
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (extraction == null) onExtractText(file.id)
                                    else onCancelText(file.id)
                                },
                                enabled = if (extraction == null) !state.importing && !state.modelsChecking &&
                                    state.requiredMissingModels.all { it == ModelKind.QWEN }
                                    else extraction.phase != ExtractionPhase.CANCELLING
                            ) {
                                if (extraction == null) {
                                    Icon(painterResource(R.drawable.ic_extract_text), contentDescription = "텍스트 추출")
                                } else {
                                    Icon(Icons.Default.Close, contentDescription = "텍스트 추출 중지")
                                }
                            }
                        }

                        TextButton(
                            onClick = {
                                editingSpeakerId = file.id
                                manualSpeakerCount = file.speakerCount != null
                                editedSpeakerCount = file.speakerCount?.toString() ?: ""
                            },
                            enabled = extraction == null
                        ) {
                            Text("화자 수: ${file.speakerCount?.let { "${it}명" } ?: "자동 감지"}")
                        }

                        if (playback?.error != null) {
                            Text(playback.error, color = MaterialTheme.colorScheme.error)
                        }

                        if (extraction != null) {
                            when (extraction.phase) {
                                ExtractionPhase.QUEUED -> Text("대기 중 · 최대 ${state.parallelLimit}개 동시 추출")
                                ExtractionPhase.LOADING -> Text("모델 준비 중")
                                ExtractionPhase.CANCELLING -> Text("중지 중...")
                                ExtractionPhase.DIARIZING -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("화자 구분 중")
                                    }
                                }
                                ExtractionPhase.RUNNING -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("텍스트 추출 중")
                                    }
                                }
                            }
                            if (extraction.phase != ExtractionPhase.QUEUED) {
                                if (extraction.phase == ExtractionPhase.LOADING) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text("음성 인식 모델 불러오는 중 · 경과 ${formatDuration(extraction.elapsedSeconds)}")
                                } else if (extraction.phase == ExtractionPhase.DIARIZING) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text("누가 언제 말했는지 분석 중 · 경과 ${formatDuration(extraction.elapsedSeconds)}")
                                } else if (extraction.processedSeconds == 0L) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text("첫 구간 처리 중 · 경과 ${formatDuration(extraction.elapsedSeconds)}")
                                } else {
                                    val progress = (
                                        extraction.processedSeconds.toFloat() / extraction.totalSeconds
                                    ).coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    val remaining = (
                                        extraction.phaseElapsedSeconds.toDouble() /
                                            extraction.processedSeconds *
                                            (extraction.totalSeconds - extraction.processedSeconds).coerceAtLeast(0)
                                    ).toLong()
                                    Text(
                                        "음성 ${extraction.processedSeconds}/${extraction.totalSeconds}초 · " +
                                            "경과 ${formatDuration(extraction.elapsedSeconds)} · " +
                                            "음성 인식 남은 시간 약 ${formatDuration(remaining)} (이후 화자 분석)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        if (transcript != null) {
                            TabRow(selectedTabIndex = selectedTabs[file.id] ?: 0) {
                                Tab(selected = (selectedTabs[file.id] ?: 0) == 0,
                                    onClick = { selectedTabs[file.id] = 0 }, text = { Text("전체 텍스트") })
                                Tab(selected = selectedTabs[file.id] == 1,
                                    onClick = { selectedTabs[file.id] = 1 }, text = { Text("문서화") })
                            }
                            when (selectedTabs[file.id] ?: 0) {
                                0 -> Text(transcript.ifBlank { "인식된 텍스트가 없어." })
                                1 -> {
                                    Text("자동 생성 결과야. 결정·할 일과 원문 근거를 직접 확인해 줘.",
                                        style = MaterialTheme.typography.bodySmall)
                                    if (stored?.stale == true) {
                                        Text("원문이 바뀌어 이전 문서야. 다시 생성해 줘.",
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                    if (stored?.document != null) Text(stored.document)
                                    if (documentJob == null) {
                                        Button(onClick = { onCreateDocument(file.id) },
                                            enabled = state.modelStatus == ModelStatus.READY && extraction == null) {
                                            Text(if (stored?.document == null) "문서 생성" else "문서 다시 생성")
                                        }
                                        if (state.modelStatus != ModelStatus.READY) {
                                            Text("설정에서 Qwen 로컬 모델을 먼저 설치해 줘.")
                                        }
                                    } else {
                                        Text(when (documentJob.phase) {
                                            DocumentPhase.QUEUED -> "문서화 대기 중 · 음성 인식이 끝나면 시작"
                                            DocumentPhase.LOADING -> "문서화 모델 불러오는 중"
                                            DocumentPhase.RUNNING -> "문서화 중 ${documentJob.completedParts}/${documentJob.totalParts}구간"
                                            DocumentPhase.CANCELLING -> "문서화 취소 중"
                                        })
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                        TextButton(onClick = { onCancelDocument(file.id) },
                                            enabled = documentJob.phase != DocumentPhase.CANCELLING) { Text("취소") }
                                    }
                                    state.documentErrors[file.id]?.let {
                                        Text("문서화 실패: $it", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return if (minutes > 0) "${minutes}분 ${remainder}초" else "${remainder}초"
}
