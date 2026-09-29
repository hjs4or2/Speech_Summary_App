package com.app.speechsummary.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Qwen's official instruction-tuned Q4_K_M artifact, installed separately from the APK. */
class LocalModelRepository(private val context: Context) {
    private val directory = File(context.noBackupFilesDir, "models").also { it.mkdirs() }
    val modelFile: File get() = File(directory, FILE_NAME)

    fun isInstalled(): Boolean = verify(modelFile)

    fun install(uri: Uri, onBytesCopied: (Long) -> Unit = {}): File {
        val temporary = File.createTempFile("qwen-import-", ".tmp", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var length = 0L
            val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "모델 파일을 열 수 없어." }
            input.use { source ->
                FileOutputStream(temporary).use { fileOutput ->
                    val destination = fileOutput.buffered()
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        length += count
                        require(length <= EXPECTED_SIZE) { "모델 크기가 공식 파일과 달라." }
                        digest.update(buffer, 0, count)
                        destination.write(buffer, 0, count)
                        onBytesCopied(length)
                    }
                    destination.flush()
                    fileOutput.fd.sync()
                }
            }
            require(length == EXPECTED_SIZE) { "모델 크기가 공식 파일과 달라." }
            require(digest.digest().toHex() == SHA256) { "모델 SHA-256이 공식 파일과 달라." }
            // Same-directory POSIX rename atomically replaces the old valid model only after verification.
            require(temporary.renameTo(modelFile)) { "검증된 모델을 설치하지 못했어." }
            return modelFile
        } finally {
            temporary.delete()
        }
    }

    private fun verify(file: File): Boolean {
        if (!file.isFile || file.length() != EXPECTED_SIZE) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex() == SHA256
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val DISPLAY_NAME = "Qwen2.5-1.5B Instruct Q4_K_M"
        const val FILE_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        const val EXPECTED_SIZE = 1_117_320_736L
        const val SHA256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
        const val SOURCE = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/dd26da440ef0330c47919d1ecae0966d24022222/qwen2.5-1.5b-instruct-q4_k_m.gguf"
    }
}
