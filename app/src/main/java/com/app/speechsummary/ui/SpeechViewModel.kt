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
import com.app.speechsummary.data.DocumentRepository
import com.app.speechsummary.data.LocalModelRepository
import com.app.speechsummary.data.DownloadableModels
import com.app.speechsummary.data.ModelCatalog
import com.app.speechsummary.data.ModelKind
import com.app.speechsummary.data.DiarizationEngine
import com.app.speechsummary.data.DiarizationSelection
import com.app.speechsummary.data.WhisperModel
import com.app.speechsummary.data.WhisperModelSelection
import com.app.speechsummary.data.StoredDocument
import com.app.speechsummary.llm.MeetingDocumenter
import com.app.speechsummary.stt.WhisperTranscriber
import com.app.speechsummary.stt.TranscriptionStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

enum class ExtractionPhase { QUEUED, LOADING, RUNNING, DIARIZING, CANCELLING }
enum class ModelStatus { CHECKING, MISSING, INSTALLING, READY, ERROR }
enum class DocumentPhase { QUEUED, LOADING, RUNNING, CANCELLING }
data class DocumentStatus(val phase: DocumentPhase, val completedParts: Int = 0, val totalParts: Int = 0, val error: String? = null)

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
    val diarizationEngine: DiarizationEngine = DiarizationEngine.SHERPA,
    val whisperModel: WhisperModel = WhisperModel.BASE,
    val files: List<SpeechItem> = emptyList(),
    val extractions: Map<String, ExtractionStatus> = emptyMap(),
    val transcripts: Map<String, String> = emptyMap(),
    val documents: Map<String, StoredDocument> = emptyMap(),
    val documentJobs: Map<String, DocumentStatus> = emptyMap(),
    val documentErrors: Map<String, String> = emptyMap(),
    val modelStatus: ModelStatus = ModelStatus.CHECKING,
    val modelProgressBytes: Long = 0,
    val modelError: String? = null,
    val modelsChecking: Boolean = true,
    val missingModels: Set<ModelKind> = ModelKind.entries.toSet(),
    val downloadingModels: Boolean = false,
    val downloadModel: ModelKind? = null,
    val downloadProgressBytes: Long = 0,
    val downloadError: String? = null,
    val logs: List<String> = emptyList()
) {
    val requiredMissingModels: Set<ModelKind>
        get() = missingModels.intersect(diarizationEngine.requiredModels(whisperModel))
}

class SpeechViewModel(application: Application) : AndroidViewModel(application) {
    private data class PendingExtraction(
        val itemId: String,
        val expectedSpeakerCount: Int?,
        val diarizationEngine: DiarizationEngine,
        val whisperModel: WhisperModel
    )

    private data class RunningTask(
        val transcriber: WhisperTranscriber,
        val startedAtMs: Long,
        var phaseStartedAtMs: Long = startedAtMs,
        var job: Job? = null
    )

    private val repository = SpeechRepository(application)
    private val documentRepository = DocumentRepository(application)
    private val modelRepository = LocalModelRepository(application)
    private val downloadableModels = DownloadableModels(application)
    private var downloadJob: Job? = null
    private val documenter = MeetingDocumenter(modelRepository)
    private val initialFiles = repository.list()
    private val documentQueue = ArrayDeque<String>()
    private var closed = false
    private var activeDocumentId: String? = null
    private var documentJob: Job? = null
    private val preferences = application.getSharedPreferences("speech_settings", Context.MODE_PRIVATE)
    private val diarizationSelection = DiarizationSelection(
        { preferences.getString("diarization_engine", null) },
        { preferences.edit().putString("diarization_engine", it).apply() }
    )
    private val whisperModelSelection = WhisperModelSelection(
        { preferences.getString("whisper_model", null) },
        { preferences.edit().putString("whisper_model", it).apply() }
    )
    private val pending = ArrayDeque<PendingExtraction>()
    private val running = mutableMapOf<String, RunningTask>()
    private var tickerJob: Job? = null

    var state by mutableStateOf(SpeechUiState(
        files = initialFiles,
        parallelLimit = if (preferences.getBoolean("parallel_enabled", false)) 2 else 1,
        diarizationEngine = diarizationSelection.load(),
        whisperModel = whisperModelSelection.load()
    ))
        private set

    private val audioPlayback = AudioPlaybackController(
        application,
        onStateChanged = { state = state.copy(playback = it) },
        onError = { log("재생 실패: $it") }
    )

    init {
        log("준비됨")
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                documentRepository.readAll(initialFiles.map { it.id })
            }
            if (!closed) state = state.copy(
                documents = saved + state.documents,
                transcripts = saved.mapValues { it.value.transcript } + state.transcripts
            )
        }
        viewModelScope.launch {
            try {
                val missing = withContext(Dispatchers.IO) {
                    val coroutine = kotlinx.coroutines.currentCoroutineContext()
                    downloadableModels.missing(checkActive = { coroutine.ensureActive() })
                }.map { it.kind }.toSet()
                if (!closed) state = state.copy(
                    modelsChecking = false,
                    missingModels = missing,
                    modelStatus = if (ModelKind.QWEN in missing) ModelStatus.MISSING else ModelStatus.READY
                )
            } catch (e: Exception) {
                if (!closed) state = state.copy(modelsChecking = false, modelStatus = ModelStatus.ERROR,
                    downloadError = e.message ?: "설치된 모델을 확인하지 못했어.")
            }
        }
    }

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

    fun setDiarizationEngine(engine: DiarizationEngine) {
        if (state.diarizationEngine == engine || state.downloadingModels || state.extractions.isNotEmpty()) return
        diarizationSelection.save(engine)
        state = state.copy(diarizationEngine = engine)
        log(if (engine == DiarizationEngine.NEMOTRON) {
            "Nemotron 3 선택: 최대 8명. 9명 이상 회의는 기존 화자 구분 엔진을 사용해 줘."
        } else "기존 화자 구분 엔진 선택")
    }

    fun setWhisperModel(model: WhisperModel) {
        if (state.whisperModel == model || state.downloadingModels || state.extractions.isNotEmpty()) return
        whisperModelSelection.save(model)
        state = state.copy(whisperModel = model, downloadError = null)
        log("음성 인식 모델 선택: ${model.displayName}")
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
        if (state.importing || state.downloadingModels || state.extractions.containsKey(itemId)) return
        val item = state.files.firstOrNull { it.id == itemId } ?: return
        if (!state.diarizationEngine.acceptsSpeakerCount(item.speakerCount)) {
            log("Nemotron 3는 최대 8명까지만 지원해. ${item.name}: 기존 화자 구분 엔진을 선택해 줘.")
            return
        }
        if (state.modelsChecking || state.requiredMissingModels.any { it != ModelKind.QWEN }) {
            log("텍스트 추출에는 설정에서 음성 모델 설치가 필요해.")
            return
        }
        val duration = ((item.sizeBytes + 31_999) / 32_000).coerceAtLeast(1)
        state = state.copy(
            extractions = state.extractions + (itemId to ExtractionStatus(ExtractionPhase.QUEUED, totalSeconds = duration))
        )
        pending.addLast(PendingExtraction(itemId, item.speakerCount, state.diarizationEngine, state.whisperModel))
        val setting = item.speakerCount?.let { "${it}명" } ?: "자동 감지"
        log("텍스트 추출 대기: ${item.name} (화자 수: $setting, 음성 모델: ${state.whisperModel.displayName})")
        startPending()
    }

    private fun startPending() {
        if (closed) return
        if (state.modelsChecking) return
        if (activeDocumentId != null || documentQueue.isNotEmpty()) {
            startNextDocument()
            return
        }
        while (running.size < state.parallelLimit && pending.isNotEmpty()) {
            val queued = pending.removeFirst()
            if (state.missingModels.any { it != ModelKind.QWEN && it in queued.diarizationEngine.requiredModels(queued.whisperModel) }) {
                pending.addFirst(queued)
                return
            }
            val itemId = queued.itemId
            val item = state.files.firstOrNull { it.id == itemId } ?: continue
            val status = state.extractions[itemId] ?: continue
            val task = RunningTask(WhisperTranscriber(getApplication(), queued.diarizationEngine, queued.whisperModel), SystemClock.elapsedRealtime())
            running[itemId] = task
            state = state.copy(extractions = state.extractions + (itemId to status.copy(phase = ExtractionPhase.LOADING)))
            val setting = queued.expectedSpeakerCount?.let { "${it}명" } ?: "자동 감지"
            log("텍스트 추출 시작: ${item.name} (화자 수: $setting, 음성 모델: ${queued.whisperModel.displayName})")
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
                    val saved = withContext(Dispatchers.IO) { documentRepository.saveTranscript(itemId, text) }
                    state = state.copy(
                        transcripts = state.transcripts + (itemId to text),
                        documents = state.documents + (itemId to saved)
                    )
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

    fun installModel(uri: Uri) {
        if (state.modelStatus == ModelStatus.CHECKING || state.modelStatus == ModelStatus.INSTALLING ||
            state.downloadingModels ||
            activeDocumentId != null || documentQueue.isNotEmpty()) return
        val wasReady = state.modelStatus == ModelStatus.READY
        state = state.copy(modelStatus = ModelStatus.INSTALLING, modelProgressBytes = 0, modelError = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    modelRepository.install(uri) { bytes ->
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            state = state.copy(modelProgressBytes = bytes)
                        }
                    }
                }
                state = state.copy(modelStatus = ModelStatus.READY, modelProgressBytes = LocalModelRepository.EXPECTED_SIZE)
                state = state.copy(missingModels = state.missingModels - ModelKind.QWEN)
                log("로컬 문서화 모델 설치 완료")
            } catch (e: Exception) {
                state = state.copy(modelStatus = if (wasReady) ModelStatus.READY else ModelStatus.ERROR,
                    modelError = e.message ?: "모델 설치 실패")
                log("모델 설치 실패: ${e.message}")
            }
        }
    }

    fun downloadModels(allowMetered: Boolean) {
        if (state.modelsChecking || state.downloadingModels || state.requiredMissingModels.isEmpty() ||
            state.modelStatus == ModelStatus.INSTALLING || state.extractions.isNotEmpty()) return
        val selectedAssets = ModelCatalog.assets.filter {
            it.kind in state.diarizationEngine.requiredModels(state.whisperModel)
        }
        val selectedModel = state.whisperModel
        state = state.copy(downloadingModels = true, downloadError = null, downloadProgressBytes = 0)
        log("모델 다운로드 시작: ${selectedModel.displayName}")
        downloadJob = viewModelScope.launch {
            try {
                val pendingModels = selectedAssets.filter { it.kind in state.missingModels }
                var completed = selectedAssets.sumOf { it.bytes } - pendingModels.sumOf { it.bytes }
                for (asset in pendingModels) {
                    val base = completed
                    state = state.copy(downloadModel = asset.kind)
                    var lastUpdate = 0L
                    withContext(Dispatchers.IO) {
                        downloadableModels.download(asset, allowMetered) { bytes ->
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastUpdate > 250 || bytes == asset.bytes) {
                                lastUpdate = now
                                withContext(Dispatchers.Main.immediate) {
                                    if (state.downloadingModels) state = state.copy(downloadProgressBytes = base + bytes)
                                }
                            }
                        }
                    }
                    completed += asset.bytes
                    state = state.copy(missingModels = state.missingModels - asset.kind,
                        modelStatus = if (asset.kind == ModelKind.QWEN) ModelStatus.READY else state.modelStatus,
                        modelError = if (asset.kind == ModelKind.QWEN) null else state.modelError,
                        downloadProgressBytes = completed)
                }
                log("모든 모델 설치 완료")
                startPending()
            } catch (e: CancellationException) {
                state = state.copy(downloadError = "다운로드를 중단했어. 다시 시도하면 이어받아.")
            } catch (e: Exception) {
                state = state.copy(downloadError = e.message ?: "모델 다운로드에 실패했어.")
                log("모델 다운로드 실패: ${e.message}")
            } finally {
                state = state.copy(downloadingModels = false, downloadModel = null)
                downloadJob = null
            }
        }
    }

    fun cancelModelDownload() { downloadJob?.cancel() }

    fun createDocument(itemId: String) {
        if (state.documentJobs.containsKey(itemId)) return
        if (state.modelStatus != ModelStatus.READY) {
            state = state.copy(documentErrors = state.documentErrors + (itemId to "설정에서 로컬 모델을 설치해 줘."))
            return
        }
        if (state.documents[itemId]?.transcript.isNullOrBlank()) return
        documentQueue.addLast(itemId)
        state = state.copy(documentJobs = state.documentJobs + (itemId to DocumentStatus(DocumentPhase.QUEUED)),
            documentErrors = state.documentErrors - itemId)
        startNextDocument()
    }

    private fun startNextDocument() {
        if (closed || running.isNotEmpty() || activeDocumentId != null || documentQueue.isEmpty()) return
        val itemId = documentQueue.removeFirst()
        val source = state.documents[itemId] ?: run { startPending(); return }
        activeDocumentId = itemId
        state = state.copy(documentJobs = state.documentJobs + (itemId to DocumentStatus(DocumentPhase.LOADING)))
        documentJob = viewModelScope.launch {
            try {
                val document = withContext(Dispatchers.IO) {
                    documenter.document(source.transcript) { done, total ->
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            val current = state.documentJobs[itemId] ?: return@launch
                            if (current.phase != DocumentPhase.CANCELLING) {
                                state = state.copy(documentJobs = state.documentJobs +
                                    (itemId to DocumentStatus(DocumentPhase.RUNNING, done, total)))
                            }
                        }
                    }
                }
                val saved = withContext(Dispatchers.IO) {
                    documentRepository.saveDocument(itemId, source.transcriptHash, document)
                }
                state = state.copy(documents = state.documents + (itemId to saved))
                log("문서화 완료: $itemId")
            } catch (_: CancellationException) {
                log("문서화 취소: $itemId")
            } catch (e: Exception) {
                state = state.copy(documentErrors = state.documentErrors +
                    (itemId to (e.message ?: "문서화 실패")))
                log("문서화 실패: $itemId - ${e.message}")
            } finally {
                withContext(NonCancellable) {
                    activeDocumentId = null
                    documentJob = null
                    state = state.copy(documentJobs = state.documentJobs - itemId)
                    startPending()
                }
            }
        }
    }

    fun cancelDocument(itemId: String) {
        val status = state.documentJobs[itemId] ?: return
        if (status.phase == DocumentPhase.QUEUED) {
            documentQueue.removeAll { it == itemId }
            state = state.copy(documentJobs = state.documentJobs - itemId)
            startPending()
        } else if (activeDocumentId == itemId && status.phase != DocumentPhase.CANCELLING) {
            state = state.copy(documentJobs = state.documentJobs +
                (itemId to status.copy(phase = DocumentPhase.CANCELLING)))
            documenter.abort()
            documentJob?.cancel()
        }
    }

    override fun onCleared() {
        closed = true
        pending.clear()
        documentQueue.clear()
        audioPlayback.stop()
        running.values.forEach { task ->
            task.transcriber.requestAbort()
            task.job?.cancel()
        }
        tickerJob?.cancel()
        downloadJob?.cancel()
        documenter.abort()
        documentJob?.cancel()
        runBlocking(Dispatchers.IO) {
            try { if (state.recording) repository.stopRecording() else repository.close() } catch (_: Exception) { }
        }
        super.onCleared()
    }
}
