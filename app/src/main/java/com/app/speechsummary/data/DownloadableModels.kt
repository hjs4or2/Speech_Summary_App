package com.app.speechsummary.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class ModelKind { WHISPER, WHISPER_SMALL, SEGMENTATION, EMBEDDING, NEMOTRON, QWEN }

enum class WhisperModel(val kind: ModelKind, val displayName: String) {
    BASE(ModelKind.WHISPER, "빠른 모드 · base Q5_1"),
    SMALL(ModelKind.WHISPER_SMALL, "정확도 우선 · small")
}

enum class DiarizationEngine { SHERPA, NEMOTRON;
    fun requiredModels(whisperModel: WhisperModel = WhisperModel.BASE): Set<ModelKind> = when (this) {
        SHERPA -> setOf(whisperModel.kind, ModelKind.SEGMENTATION, ModelKind.EMBEDDING, ModelKind.QWEN)
        NEMOTRON -> setOf(whisperModel.kind, ModelKind.NEMOTRON, ModelKind.QWEN)
    }

    fun acceptsSpeakerCount(count: Int?): Boolean = this != NEMOTRON || count == null || count <= 8
}

data class ModelAsset(
    val kind: ModelKind,
    val displayName: String,
    val filename: String,
    val bytes: Long,
    val sha256: String,
    val url: String
)

/** Each URL is pinned to a public repository commit, and each payload is checked independently. */
object ModelCatalog {
    val assets = listOf(
        ModelAsset(ModelKind.WHISPER, "Whisper base Q5_1 · 다국어", "ggml-base-q5_1.bin", 59_707_625L,
            "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base-q5_1.bin"),
        ModelAsset(ModelKind.WHISPER_SMALL, "Whisper small · 다국어 표준", "ggml-small.bin", 487_601_967L,
            "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-small.bin"),
        ModelAsset(ModelKind.SEGMENTATION, "화자 구간 분석", "speaker-segmentation.onnx", 1_540_506L,
            "d582f4b4c6b48205de7e0643c57df0df5615a3c176189be3fc461e9d18827b5d",
            "https://huggingface.co/csukuangfj/sherpa-onnx-pyannote-segmentation-3-0/resolve/9403a6902bb58e3d5ae8c7e77c3422de279db2e0/model.int8.onnx"),
        ModelAsset(ModelKind.EMBEDDING, "화자 음성 특징", "speaker-eres2net.onnx", 39_593_761L,
            "1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b",
            "https://huggingface.co/csukuangfj/speaker-embedding-models/resolve/8be2a75c9ed7a590538b268e46fbb65e1aa9d208/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"),
        ModelAsset(ModelKind.NEMOTRON, "Nemotron 3 화자 구분 · 최대 8명", "Nemotron-3-Diarization.q8_0.gguf", 107_012_128L,
            "08456d9e22cd9a323c0364d98375f3746d6e68507ebb705cd46438c534c7a3a1",
            "https://huggingface.co/nvidia/Nemotron-3-Diarization/resolve/f667ed73aee57d40cc39428eb768b4fd87a0a29e/Nemotron-3-Diarization.q8_0.gguf"),
        ModelAsset(ModelKind.QWEN, LocalModelRepository.DISPLAY_NAME, LocalModelRepository.FILE_NAME,
            LocalModelRepository.EXPECTED_SIZE, LocalModelRepository.SHA256,
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/dd26da440ef0330c47919d1ecae0966d24022222/qwen2.5-1.5b-instruct-q4_k_m.gguf")
    )
    val totalBytes: Long = assets.sumOf { it.bytes }
}

internal object ModelIntegrity {
    fun matches(file: File, asset: ModelAsset, checkActive: () -> Unit = {}): Boolean {
        if (!file.isFile || file.length() != asset.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val bytes = ByteArray(1024 * 1024)
            while (true) {
                checkActive()
                val count = input.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == asset.sha256
    }

    fun rangeStartsAt(header: String?, offset: Long, total: Long): Boolean {
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(header ?: "") ?: return false
        return match.groupValues[1].toLongOrNull() == offset &&
            match.groupValues[2].toLongOrNull() == total - 1 &&
            match.groupValues[3].toLongOrNull() == total
    }
}

/** All installed and partial files live in noBackupFilesDir, which is removed on uninstall. */
class DownloadableModels(private val context: Context) {
    private val directory = File(context.noBackupFilesDir, "models").also { check(it.isDirectory || it.mkdirs()) }

    fun file(kind: ModelKind): File = File(directory, ModelCatalog.assets.first { it.kind == kind }.filename)

    /** Called on Dispatchers.IO. A verified older Qwen import is moved without recopying it. */
    fun missing(checkActive: () -> Unit = {}, onChecked: (ModelAsset, Boolean) -> Unit = { _, _ -> }): List<ModelAsset> =
        ModelCatalog.assets.filter { asset ->
            val destination = file(asset.kind)
            var valid = ModelIntegrity.matches(destination, asset, checkActive)
            if (asset.kind == ModelKind.QWEN && !valid) {
                val legacy = File(context.filesDir, "models/${asset.filename}")
                if (ModelIntegrity.matches(legacy, asset, checkActive)) {
                    checkActive()
                    if (destination.exists()) check(destination.delete()) { "손상된 모델 파일을 교체하지 못했어." }
                    check(legacy.renameTo(destination)) { "기존 문서화 모델을 내부 저장소로 옮기지 못했어." }
                    valid = true
                }
            }
            onChecked(asset, valid)
            !valid
        }

    suspend fun download(asset: ModelAsset, allowMetered: Boolean, onBytes: suspend (Long) -> Unit) {
        val coroutine = currentCoroutineContext()
        require(File(asset.filename).name == asset.filename && !asset.filename.contains('\\')) { "잘못된 모델 파일 이름이야." }
        val destination = File(directory, asset.filename)
        if (ModelIntegrity.matches(destination, asset) { coroutine.ensureActive() }) {
            onBytes(asset.bytes)
            return
        }
        val partial = File(directory, "${asset.filename}.part")
        if (partial.length() > asset.bytes) check(partial.delete())
        if (partial.length() == asset.bytes) {
            if (ModelIntegrity.matches(partial, asset) { coroutine.ensureActive() }) {
                coroutine.ensureActive()
                installVerified(partial, destination)
                onBytes(asset.bytes)
                return
            }
            check(partial.delete()) { "손상된 임시 파일을 지우지 못했어." }
        }
        var offset = partial.length()
        checkNetwork(allowMetered)
        checkSpace(asset.bytes - offset)
        onBytes(offset)
        val connection = (URL(asset.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val response = connection.responseCode
            require(connection.url.protocol == "https") { "안전하지 않은 다운로드 주소야." }
            if (response == HttpURLConnection.HTTP_OK && offset > 0) {
                // The server ignored Range. Restart rather than append a duplicate payload.
                offset = 0
                onBytes(0)
            } else if (response == HttpURLConnection.HTTP_PARTIAL) {
                if (!ModelIntegrity.rangeStartsAt(connection.getHeaderField("Content-Range"), offset, asset.bytes)) {
                    partial.delete()
                    throw IOException("서버가 잘못된 다운로드 구간을 반환했어. 다시 시도해 줘.")
                }
            } else {
                require(response == HttpURLConnection.HTTP_OK && offset == 0L) {
                    "다운로드 서버 응답 오류: HTTP $response"
                }
            }
            val expectedTransfer = asset.bytes - offset
            val contentLength = connection.contentLengthLong
            require(contentLength < 0 || contentLength == expectedTransfer) { "서버 파일 크기가 달라." }
            var written = offset
            connection.inputStream.use { input ->
                FileOutputStream(partial, offset > 0).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        checkNetwork(allowMetered)
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        require(written <= asset.bytes) { "서버 파일 크기가 달라." }
                        output.write(buffer, 0, count)
                        onBytes(written)
                    }
                    output.fd.sync()
                }
            }
            require(written == asset.bytes) { "다운로드가 끝나기 전에 연결이 끊겼어. 다시 시도하면 이어받아." }
            currentCoroutineContext().ensureActive()
            if (!ModelIntegrity.matches(partial, asset) { coroutine.ensureActive() }) {
                partial.delete()
                throw IOException("모델 SHA-256 검증에 실패했어. 다시 다운로드해 줘.")
            }
            coroutine.ensureActive()
            // A valid destination is never replaced. An invalid one is removed only after the new file verifies.
            installVerified(partial, destination)
        } finally {
            connection.disconnect()
        }
    }

    private fun installVerified(partial: File, destination: File) {
        if (destination.exists()) check(destination.delete()) { "손상된 모델 파일을 교체하지 못했어." }
        check(partial.renameTo(destination)) { "검증된 모델을 설치하지 못했어." }
    }

    private fun checkSpace(remainingBytes: Long) {
        val reserve = 100L * 1024 * 1024
        require(directory.usableSpace >= remainingBytes + reserve) {
            "내부 저장 공간이 부족해. 최소 ${(remainingBytes + reserve) / 1_000_000} MB가 필요해."
        }
    }

    private fun checkNetwork(allowMetered: Boolean) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: throw IOException("네트워크에 연결되지 않았어. 연결 후 다시 시도해 줘.")
        val capabilities = manager.getNetworkCapabilities(network)
            ?: throw IOException("네트워크 상태를 확인할 수 없어.")
        if (!allowMetered && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            throw IOException("모바일 데이터 사용 동의가 필요해. Wi-Fi에 연결하거나 동의 후 다시 시도해 줘.")
        }
    }
}
