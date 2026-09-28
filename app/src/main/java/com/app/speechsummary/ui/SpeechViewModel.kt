package com.app.speechsummary.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.speechsummary.audio.AudioPlaybackController
import com.app.speechsummary.audio.PlaybackState
import com.app.speechsummary.data.SpeechItem
import com.app.speechsummary.data.SpeechRepository
import com.app.speechsummary.stt.WhisperTranscriber
import com.app.speechsummary.stt.TranscriptionStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

enum class ExtractionPhase { QUEUED, LOADING, RUNNING, DIARIZING, CANCELLING }

data class ExtractionStatus(
    val phase: ExtractionPhase,
    val processedSeconds: Long = 0,
    val totalSeconds: Long,
    val elapsedSeconds: Long = 0,
    val phaseElapsedSeconds: Long = 0
)

data class SpeechUiState(
    val recording: Boolean = false,
    val recordingBusy: Boolean = false,
    val importing: Boolean = false,
    val playback: PlaybackState = PlaybackState(),
    val parallelLimit: Int = 1,
    val files: List<SpeechItem> = emptyList(),
    val extractions: Map<String, ExtractionStatus> = emptyMap(),
    val transcripts: Map<String, String> = emptyMap(),
    val logs: List<String> = emptyList()
)

class SpeechViewModel(application: Application) : AndroidViewModel(application) {
    private data class PendingExtraction(val itemId: String, val expectedSpeakerCount: Int?)

    private data class RunningTask(
        val transcriber: WhisperTranscriber,
        val startedAtMs: Long,
        var phaseStartedAtMs: Long = startedAtMs,
        var job: Job? = null
    )

    private val repository = SpeechRepository(application)
    private val preferences = application.getSharedPreferences("speech_settings", Context.MODE_PRIVATE)
    private val pending = ArrayDeque<PendingExtraction>()
    private val running = mutableMapOf<String, RunningTask>()
    private var tickerJob: Job? = null

    var state by mutableStateOf(SpeechUiState(
        files = repository.list(),
        parallelLimit = if (preferences.getBoolean("parallel_enabled", false)) 2 else 1
    ))
        private set

    private val audioPlayback = AudioPlaybackController(
        application,
        onStateChanged = { state = state.copy(playback = it) },
        onError = { log("재생 실패: $it") }
    )

    init { log("준비됨") }

    fun togglePlayback(itemId: String) {
        if (state.recording || state.recordingBusy) return
        try {
            audioPlayback.toggle(itemId, repository.fileFor(itemId))
        } catch (e: Exception) {
            log("재생 실패: ${e.message}")
        }
    }

    fun pausePlayback() = audioPlayback.pause()

    private fun log(message: String) {
        val timestamp = DateFormat.format("HH:mm:ss", System.currentTimeMillis())
        state = state.copy(logs = (state.logs + "$timestamp  $message").takeLast(200))
    }

    fun setParallelEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("parallel_enabled", enabled).apply()
        state = state.copy(parallelLimit = if (enabled) 2 else 1)
        log("동시 텍스트 추출: ${state.parallelLimit}개")
        startPending()
    }

    fun microphonePermissionDenied() { log("마이크 권한이 필요해.") }

    fun toggleRecording() {
        if (state.recordingBusy || state.importing) return
        if (state.recording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        audioPlayback.pause()
        state = state.copy(recordingBusy = true)
        log("녹음 준비 중...")
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.startRecording() }
                state = state.copy(recording = true)
                log("녹음 시작")
            } catch (e: Exception) { log("녹음 시작 실패: ${e.message}") }
            finally { state = state.copy(recordingBusy = false) }
        }
    }

    private fun stopRecording() {
        state = state.copy(recording = false, recordingBusy = true)
        log("녹음 저장 및 변환 중...")
        viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.IO) { repository.stopRecording() }
                state = state.copy(files = repository.list())
                log("녹음 저장 완료: ${item.name}")
            } catch (e: Exception) { log("녹음 저장 실패: ${e.message}") }
            finally { state = state.copy(recordingBusy = false) }
        }
    }

    fun importMedia(uri: Uri) {
        if (state.importing || state.recording || state.recordingBusy) return
        state = state.copy(importing = true)
        log("음성 트랙 추출 및 변환 중...")
        viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.IO) { repository.importMedia(uri) }
                state = state.copy(files = repository.list())
                log("가져오기 완료: ${item.name}")
            } catch (e: Exception) { log("가져오기 실패: ${e.message}") }
            finally { state = state.copy(importing = false) }
        }
    }

    fun renameTitle(itemId: String, title: String) {
        try {
            val updated = repository.renameTitle(itemId, title)
            state = state.copy(files = state.files.map { if (it.id == itemId) updated else it })
            log("제목 변경: ${updated.name}")
        } catch (e: Exception) {
            log("제목 변경 실패: ${e.message}")
        }
    }

    fun setSpeakerCount(itemId: String, speakerCount: Int?) {
        if (state.extractions.containsKey(itemId)) return
        val current = state.files.firstOrNull { it.id == itemId } ?: return
        try {
            val updated = repository.setSpeakerCount(itemId, speakerCount)
            state = state.copy(files = state.files.map { if (it.id == itemId) updated else it })
            if (current.speakerCount != updated.speakerCount) {
                val setting = updated.speakerCount?.let { "${it}명" } ?: "자동 감지"
                log("화자 수 설정: ${updated.name} - $setting (다음 추출부터 적용)")
            }
        } catch (e: Exception) {
            log("화자 수 설정 실패: ${e.message}")
        }
    }

    fun extractText(itemId: String) {
        if (state.importing || state.extractions.containsKey(itemId)) return
        val item = state.files.firstOrNull { it.id == itemId } ?: return
        val duration = ((item.sizeBytes + 31_999) / 32_000).coerceAtLeast(1)
        state = state.copy(
            extractions = state.extractions + (itemId to ExtractionStatus(ExtractionPhase.QUEUED, totalSeconds = duration)),
            transcripts = state.transcripts - itemId
        )
        pending.addLast(PendingExtraction(itemId, item.speakerCount))
        val setting = item.speakerCount?.let { "${it}명" } ?: "자동 감지"
        log("텍스트 추출 대기: ${item.name} (화자 수: $setting)")
        startPending()
    }

    private fun startPending() {
        while (running.size < state.parallelLimit && pending.isNotEmpty()) {
            val queued = pending.removeFirst()
            val itemId = queued.itemId
            val item = state.files.firstOrNull { it.id == itemId } ?: continue
            val status = state.extractions[itemId] ?: continue
            val task = RunningTask(WhisperTranscriber(getApplication()), SystemClock.elapsedRealtime())
            running[itemId] = task
            state = state.copy(extractions = state.extractions + (itemId to status.copy(phase = ExtractionPhase.LOADING)))
            val setting = queued.expectedSpeakerCount?.let { "${it}명" } ?: "자동 감지"
            log("텍스트 추출 시작: ${item.name} (화자 수: $setting)")
            ensureTicker()
            task.job = viewModelScope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        task.transcriber.transcribe(
                            repository.fileFor(itemId),
                            onProgress = { processed, total ->
                                withContext(Dispatchers.Main.immediate) {
                                    val current = state.extractions[itemId] ?: return@withContext
                                    state = state.copy(extractions = state.extractions + (
                                        itemId to current.copy(processedSeconds = processed / 16_000)
                                    ))
                                    log("처리 완료: ${item.name} ${processed / 16_000}/${(total + 15_999) / 16_000}초")
                                }
                            },
                            onDiarizationStart = {
                                withContext(Dispatchers.Main.immediate) {
                                    val current = state.extractions[itemId] ?: return@withContext
                                    if (current.phase == ExtractionPhase.CANCELLING) return@withContext
                                    task.phaseStartedAtMs = SystemClock.elapsedRealtime()
                                    state = state.copy(extractions = state.extractions + (
                                        itemId to current.copy(phase = ExtractionPhase.DIARIZING, phaseElapsedSeconds = 0)
                                    ))
                                    log("화자 구분 시작: ${item.name}")
                                }
                            },
                            onStageCompleted = { stage, elapsedMs ->
                                withContext(Dispatchers.Main.immediate) {
                                    val stageName = when (stage) {
                                        TranscriptionStage.MODEL_LOADING -> "모델 준비"
                                        TranscriptionStage.TEXT_EXTRACTION -> "음성 인식"
                                        TranscriptionStage.SPEAKER_DIARIZATION -> "화자 구분"
                                    }
                                    log("$stageName 소요: ${elapsedMs / 1000.0}초 · ${item.name}")
                                    if (stage == TranscriptionStage.MODEL_LOADING) {
                                        val current = state.extractions[itemId] ?: return@withContext
                                        if (current.phase == ExtractionPhase.CANCELLING) return@withContext
                                        task.phaseStartedAtMs = SystemClock.elapsedRealtime()
                                        state = state.copy(extractions = state.extractions + (
                                            itemId to current.copy(phase = ExtractionPhase.RUNNING, phaseElapsedSeconds = 0)
                                        ))
                                    }
                                }
                            },
                            expectedSpeakerCount = queued.expectedSpeakerCount
                        )
                    }
                    state = state.copy(transcripts = state.transcripts + (itemId to text))
                    log("텍스트 추출 완료: ${item.name}")
                } catch (_: CancellationException) {
                    log("텍스트 추출 중지됨: ${item.name}")
                } catch (e: Exception) {
                    if (state.extractions[itemId]?.phase == ExtractionPhase.CANCELLING) {
                        log("텍스트 추출 중지됨: ${item.name}")
                    } else {
                        log("텍스트 추출 실패: ${item.name} - ${e.message}")
                    }
                } finally {
                    withContext(NonCancellable) {
                        withContext(Dispatchers.IO) {
                            try { task.transcriber.close() } catch (_: Exception) { }
                        }
                        running.remove(itemId)
                        state = state.copy(extractions = state.extractions - itemId)
                        startPending()
                    }
                }
            }
        }
    }

    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (isActive && running.isNotEmpty()) {
                delay(1_000)
                val now = SystemClock.elapsedRealtime()
                state = state.copy(extractions = state.extractions.mapValues { (id, status) ->
                    val task = running[id]
                    if (task == null) status
                    else status.copy(
                        elapsedSeconds = (now - task.startedAtMs) / 1_000,
                        phaseElapsedSeconds = (now - task.phaseStartedAtMs) / 1_000
                    )
                })
            }
        }
    }

    fun cancelTranscription(itemId: String) {
        when (state.extractions[itemId]?.phase) {
            ExtractionPhase.QUEUED -> {
                pending.removeAll { it.itemId == itemId }
                state = state.copy(extractions = state.extractions - itemId)
                log("대기 취소: $itemId")
            }
            ExtractionPhase.LOADING, ExtractionPhase.RUNNING, ExtractionPhase.DIARIZING -> {
                val task = running[itemId] ?: return
                val current = state.extractions[itemId] ?: return
                state = state.copy(extractions = state.extractions + (
                    itemId to current.copy(phase = ExtractionPhase.CANCELLING)
                ))
                log("텍스트 추출 중지 요청: $itemId")
                task.transcriber.requestAbort()
                task.job?.cancel()
            }
            else -> Unit
        }
    }

    override fun onCleared() {
        audioPlayback.stop()
        running.values.forEach { task ->
            task.transcriber.requestAbort()
            task.job?.cancel()
        }
        tickerJob?.cancel()
        runBlocking(Dispatchers.IO) {
            try { if (state.recording) repository.stopRecording() else repository.close() } catch (_: Exception) { }
        }
        super.onCleared()
    }
}
