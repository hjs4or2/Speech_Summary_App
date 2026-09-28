package com.app.speechsummary

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class AudioRecorder(private val outputDir: File) {
    companion object { private const val RATE = 16_000; private const val CHANNELS = 1; private const val BITS = 16 }
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private var currentFile: File? = null

    fun start(): File {
        check(recorder == null) { "Already recording" }
        outputDir.mkdirs()
        val file = File(outputDir, "recording_${System.currentTimeMillis()}.wav")
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = (min * 2).coerceAtLeast(2048)
        val audio = AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        check(audio.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
        writeWavHeader(file, 0); recorder = audio; currentFile = file; audio.startRecording()
        worker = thread(name = "audio-recorder") {
            FileOutputStream(file, true).use { output ->
                val buffer = ByteArray(bufferSize); var total = 0
                while (recorder === audio) { val count = audio.read(buffer, 0, buffer.size); if (count > 0) { output.write(buffer, 0, count); total += count } }
                output.flush(); writeWavHeader(file, total)
            }
        }
        return file
    }

    fun stop(): File? {
        val audio = recorder ?: return null
        recorder = null
        try { audio.stop() } catch (_: IllegalStateException) { }
        audio.release(); worker?.join(1000); worker = null
        return currentFile.also { currentFile = null }
    }

    private fun writeWavHeader(file: File, dataSize: Int) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()); header.putInt(36 + dataSize); header.put("WAVEfmt ".toByteArray())
        header.putInt(16); header.putShort(1); header.putShort(CHANNELS.toShort()); header.putInt(RATE)
        header.putInt(RATE * CHANNELS * BITS / 8); header.putShort((CHANNELS * BITS / 8).toShort()); header.putShort(BITS.toShort())
        header.put("data".toByteArray()); header.putInt(dataSize)
        FileOutputStream(file, false).use { it.write(header.array()) }
    }
}

object AudioFormatConverter {
    fun wavToPcm(wav: File): File {
        require(wav.extension.equals("wav", true)) { "WAV 파일만 변환할 수 있습니다." }
        val pcm = File(wav.parentFile, wav.nameWithoutExtension + ".pcm")
        wav.inputStream().use { input -> pcm.outputStream().use { output -> input.skip(44); input.copyTo(output) } }
        return pcm
    }
}
