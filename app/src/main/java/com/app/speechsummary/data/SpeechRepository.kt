package com.app.speechsummary.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.app.speechsummary.audio.AudioFormatConverter
import com.app.speechsummary.audio.AudioRecorder
import com.app.speechsummary.audio.MediaAudioConverter
import java.io.File
import java.util.UUID

data class SpeechItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val speakerCount: Int? = null
)

class SpeechRepository(private val context: Context) {
    private val recordingsDir = File(context.filesDir, "recordings")
    private val recorder = AudioRecorder(recordingsDir)
    private val titles = context.getSharedPreferences("speech_titles", Context.MODE_PRIVATE)
    private val speakerCounts = context.getSharedPreferences("speech_speaker_counts", Context.MODE_PRIVATE)

    fun list(): List<SpeechItem> = recordingsDir.listFiles { file ->
        file.isFile && file.extension == "pcm"
    }?.sortedByDescending { it.lastModified() }?.map { file ->
        itemFor(file)
    }.orEmpty()

    private fun itemFor(file: File): SpeechItem = SpeechItem(
        file.name,
        titles.getString(file.name, null) ?: file.nameWithoutExtension,
        file.length(),
        speakerCounts.getInt(file.name, 0).takeIf { it in 1..99 }
    )

    fun startRecording() { recorder.start() }

    fun stopRecording(): SpeechItem {
        val wav = recorder.stop() ?: error("녹음 파일이 없습니다.")
        val pcm = AudioFormatConverter.wavToPcm(wav)
        return itemFor(pcm)
    }

    fun importMedia(uri: Uri): SpeechItem {
        val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "imported_media"
        val title = displayName.substringBeforeLast('.').trim().ifBlank { "가져온 음성" }
        val pcm = File(recordingsDir, "imported_${UUID.randomUUID()}.pcm")
        MediaAudioConverter.convert(context, uri, pcm)
        titles.edit().putString(pcm.name, title).apply()
        return itemFor(pcm)
    }

    fun renameTitle(itemId: String, title: String): SpeechItem {
        val normalized = title.trim()
        require(normalized.isNotEmpty()) { "제목을 입력해 주세요." }
        val file = fileFor(itemId)
        titles.edit().putString(itemId, normalized).apply()
        return itemFor(file)
    }

    fun setSpeakerCount(itemId: String, speakerCount: Int?): SpeechItem {
        require(speakerCount == null || speakerCount in 1..99) { "화자 수는 1~99명으로 지정해 주세요." }
        val file = fileFor(itemId)
        val editor = speakerCounts.edit()
        if (speakerCount == null) editor.remove(itemId) else editor.putInt(itemId, speakerCount)
        editor.apply()
        return itemFor(file)
    }

    fun fileFor(itemId: String): File = recordingsDir.listFiles { file ->
        file.isFile && file.extension == "pcm" && file.name == itemId
    }?.singleOrNull() ?: error("음성 파일을 찾을 수 없습니다.")

    fun close() { recorder.stop() }
}
