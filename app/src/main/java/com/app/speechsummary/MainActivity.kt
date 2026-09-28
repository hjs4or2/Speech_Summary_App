package com.app.speechsummary

import android.Manifest
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import com.app.speechsummary.ui.theme.SpeechSummaryTheme
import com.whispercpp.whisper.WhisperContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var recorder: AudioRecorder
    private var recording by mutableStateOf(false)
    private var status by mutableStateOf("준비됨")
    private var files by mutableStateOf(emptyList<File>())
    private var transcripts by mutableStateOf(emptyMap<String, String>())
    private var working by mutableStateOf(false)
    private var whisperContext: WhisperContext? = null

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importMedia(uri)
    }

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
                    Button(onClick = { importLauncher.launch(arrayOf("audio/*", "video/*")) }, modifier = Modifier.fillMaxWidth(), enabled = !working && !recording) {
                        Text("음성 가져오기 (오디오·영상)")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("음성 파일", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(files, key = { it.name }) { file ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(file.name.removeSuffix(".pcm"))
                                    Button(onClick = { transcribe(file) }, enabled = !working && !recording) { Text("로컬 STT") }
                                    transcripts[file.name]?.let { Text(it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasMicPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun toggleRecording() {
        if (!hasMicPermission()) { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return }
        if (recording) {
            recording = false
            working = true
            status = "녹음 저장 및 변환 중..."
            lifecycleScope.launch {
                try {
                    val pcm = withContext(Dispatchers.IO) {
                        val wav = recorder.stop() ?: error("녹음 파일이 없습니다.")
                        AudioFormatConverter.wavToPcm(wav)
                    }
                    refreshFiles()
                    status = "변환 완료: ${pcm.name}"
                } catch (e: Exception) { status = "녹음 저장 실패: ${e.message}" }
                finally { working = false }
            }
        } else try {
            recorder.start(); recording = true; status = "녹음 중..."
        } catch (e: Exception) { status = "녹음 시작 실패: ${e.message}" }
    }

    private fun importMedia(uri: Uri) {
        working = true
        status = "음성 트랙 추출 및 변환 중..."
        lifecycleScope.launch {
            try {
                val pcm = withContext(Dispatchers.IO) {
                    val name = android.provider.OpenableColumns.DISPLAY_NAME.let { column ->
                        contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    } ?: "imported_media"
                    val safeName = name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9가-힣_-]"), "_").take(60)
                    val target = File(filesDir, "recordings/${safeName}_${System.currentTimeMillis()}.pcm")
                    MediaAudioConverter.convert(this@MainActivity, uri, target)
                    target
                }
                refreshFiles()
                status = "변환 완료: ${pcm.name}"
            } catch (e: Exception) { status = "가져오기 실패: ${e.message}" }
            finally { working = false }
        }
    }

    private fun transcribe(file: File) {
        working = true
        status = "${file.name} 로컬 STT 처리 중..."
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val context = whisperContext ?: WhisperContext.createContextFromAsset(assets, "ggml-base.bin")
                        .also { whisperContext = it }
                    val chunk = ByteArray(16_000 * 2 * 30)
                    buildString {
                        file.inputStream().buffered().use { input ->
                            while (true) {
                                var count = 0
                                while (count < chunk.size) {
                                    val read = input.read(chunk, count, chunk.size - count)
                                    if (read < 0) break
                                    count += read
                                }
                                if (count == 0) break
                                val samples = FloatArray(count / 2) { i ->
                                    val lo = chunk[i * 2].toInt() and 0xff
                                    val hi = chunk[i * 2 + 1].toInt()
                                    ((hi shl 8) or lo).toShort() / 32768f
                                }
                                append(context.transcribeData(samples, printTimestamp = false))
                                append('\n')
                            }
                        }
                    }.trim()
                }
                transcripts = transcripts + (file.name to text)
                status = "STT 완료: ${file.name}"
            } catch (e: Exception) { status = "STT 실패: ${e.message}" }
            finally { working = false }
        }
    }

    private fun refreshFiles() { files = File(filesDir, "recordings").listFiles { it.isFile && it.extension == "pcm" }?.sortedByDescending { it.lastModified() }.orEmpty() }
    override fun onDestroy() { recorder.stop(); super.onDestroy() }
}
