package com.app.speechsummary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state: SpeechUiState,
    onBack: () -> Unit,
    onParallelToggle: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "돌아가기")
            }
            Text("설정", style = MaterialTheme.typography.headlineMedium)
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
        Text("로그", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(state.logs) { _, entry ->
                Text(entry, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
