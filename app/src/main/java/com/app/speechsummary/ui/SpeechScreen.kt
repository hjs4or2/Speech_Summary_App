package com.app.speechsummary.ui

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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SpeechScreen(
    state: SpeechUiState,
    onRecordClick: () -> Unit,
    onImportClick: () -> Unit,
    onExtractText: (String) -> Unit,
    onCancelText: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val expandedResults = remember { mutableStateMapOf<String, Boolean>() }

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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                file.name,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = {
                                    if (extraction == null) onExtractText(file.id)
                                    else onCancelText(file.id)
                                },
                                enabled = if (extraction == null) !state.importing
                                    else extraction.phase != ExtractionPhase.CANCELLING
                            ) {
                                Icon(
                                    imageVector = if (extraction == null) Icons.Default.PlayArrow else Icons.Default.Close,
                                    contentDescription = if (extraction == null) "텍스트 추출" else "텍스트 추출 중지"
                                )
                            }
                        }

                        if (extraction != null) {
                            when (extraction.phase) {
                                ExtractionPhase.QUEUED -> Text("대기 중 · 최대 ${state.parallelLimit}개 동시 추출")
                                ExtractionPhase.CANCELLING -> Text("중지 중...")
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
                                if (extraction.processedSeconds == 0L) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text("모델 준비·첫 구간 처리 중 · 경과 ${formatDuration(extraction.elapsedSeconds)}")
                                } else {
                                    val progress = (
                                        extraction.processedSeconds.toFloat() / extraction.totalSeconds
                                    ).coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    val remaining = (
                                        extraction.elapsedSeconds.toDouble() /
                                            extraction.processedSeconds *
                                            (extraction.totalSeconds - extraction.processedSeconds).coerceAtLeast(0)
                                    ).toLong()
                                    Text(
                                        "음성 ${extraction.processedSeconds}/${extraction.totalSeconds}초 · " +
                                            "경과 ${formatDuration(extraction.elapsedSeconds)} · " +
                                            "남은 시간 약 ${formatDuration(remaining)} (추정)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        if (transcript != null) {
                            TextButton(onClick = { expandedResults[file.id] = expandedResults[file.id] != true }) {
                                Text(if (expandedResults[file.id] == true) "추출 결과 접기" else "추출 결과 보기")
                            }
                            if (expandedResults[file.id] == true) {
                                Text(transcript.ifBlank { "인식된 텍스트가 없어." })
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
