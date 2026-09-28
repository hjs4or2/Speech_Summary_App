package com.app.speechsummary

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.app.speechsummary.ui.theme.SpeechSummaryTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var recorder: AudioRecorder
    private var recording by mutableStateOf(false)
    private var status by mutableStateOf("준비됨")
    private var transcript by mutableStateOf("")
    private var files by mutableStateOf(emptyList<File>())
    private var speechRecognizer: SpeechRecognizer? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) status = "마이크 권한이 필요합니다."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recorder = AudioRecorder(File(filesDir, "recordings"))
        refreshFiles()
        setContent {
            SpeechSummaryTheme {
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Speech Summary", style = MaterialTheme.typography.headlineMedium)
                    Text(status)
                    Button(onClick = { toggleRecording() }, modifier = Modifier.fillMaxWidth()) { Text(if (recording) "녹음 중지" else "녹음 시작") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { convertLatest() }) { Text("최근 파일 PCM 변환") }
                        Button(onClick = { startSpeechToText() }) { Text("STT 시작") }
                    }
                    if (transcript.isNotBlank()) Card(Modifier.fillMaxWidth()) { Text("추출 텍스트\n$transcript", Modifier.padding(12.dp)) }
                    Spacer(Modifier.height(4.dp))
                    Text("녹음 파일", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) { items(files) { file -> Text("• ${file.name} (${file.length()} bytes)") } }
                }
            }
        }
    }

    private fun hasMicPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun toggleRecording() {
        if (!hasMicPermission()) { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return }
        if (recording) {
            recorder.stop(); recording = false; status = "녹음 파일 저장 완료"; refreshFiles()
        } else try {
            recorder.start(); recording = true; status = "녹음 중..."
        } catch (e: Exception) { status = "녹음 시작 실패: ${e.message}" }
    }

    private fun convertLatest() {
        val latest = files.firstOrNull() ?: run { status = "먼저 녹음하세요."; return }
        try { status = "변환 완료: ${AudioFormatConverter.wavToPcm(latest).name}"; refreshFiles() }
        catch (e: Exception) { status = "변환 실패: ${e.message}" }
    }

    private fun startSpeechToText() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { status = "이 기기에서 STT를 사용할 수 없습니다."; return }
        if (!hasMicPermission()) { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) { transcript = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty(); status = "STT 완료" }
                override fun onError(error: Int) { status = "STT 오류 코드: $error" }
                override fun onReadyForSpeech(params: Bundle?) { status = "말씀하세요..." }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            })
        }
    }

    private fun refreshFiles() { files = File(filesDir, "recordings").listFiles()?.sortedByDescending { it.lastModified() }.orEmpty() }
    override fun onDestroy() { recorder.stop(); speechRecognizer?.destroy(); super.onDestroy() }
}
