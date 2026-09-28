package com.app.speechsummary.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.app.speechsummary.audio.AudioFormatConverter
import com.app.speechsummary.audio.AudioRecorder
import com.app.speechsummary.audio.MediaAudioConverter
import java.io.File

data class SpeechItem(val id: String, val name: String, val sizeBytes: Long)

class SpeechRepository(private val context: Context) {
    private val recordingsDir = File(context.filesDir, "recordings")
    private val recorder = AudioRecorder(recordingsDir)

    fun list(): List<SpeechItem> = recordingsDir.listFiles { file ->
        file.isFile && file.extension == "pcm"
    }?.sortedByDescending { it.lastModified() }?.map { file ->
        SpeechItem(file.name, file.nameWithoutExtension, file.length())
    }.orEmpty()

    fun startRecording() { recorder.start() }

    fun stopRecording(): SpeechItem {
        val wav = recorder.stop() ?: error("녹음 파일이 없습니다.")
        val pcm = AudioFormatConverter.wavToPcm(wav)
        return SpeechItem(pcm.name, pcm.nameWithoutExtension, pcm.length())
    }

    fun importMedia(uri: Uri): SpeechItem {
        val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "imported_media"
        val safeName = displayName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9가-힣_-]"), "_").take(60)
            .ifBlank { "imported_media" }
        val pcm = File(recordingsDir, "${safeName}_${System.currentTimeMillis()}.pcm")
        MediaAudioConverter.convert(context, uri, pcm)
        return SpeechItem(pcm.name, pcm.nameWithoutExtension, pcm.length())
    }

    fun fileFor(itemId: String): File = recordingsDir.listFiles { file ->
        file.isFile && file.extension == "pcm" && file.name == itemId
    }?.singleOrNull() ?: error("음성 파일을 찾을 수 없습니다.")

    fun close() { recorder.stop() }
}
