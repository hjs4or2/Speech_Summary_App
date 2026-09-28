package com.app.speechsummary.audio

import android.media.MediaDataSource
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Exposes the app's 16 kHz mono PCM16 as WAV without copying the audio. */
internal class PcmWaveDataSource(file: File) : MediaDataSource() {
    private val input = RandomAccessFile(file, "r")
    private val dataSize = input.length()
    private val header: ByteArray
    private var closed = false

    init {
        if (dataSize == 0L || dataSize % 2 != 0L || dataSize > 0xffff_ffffL - 36) {
            input.close()
            error("재생할 수 없는 PCM 파일입니다.")
        }
        header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36 + dataSize).toInt())
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(16_000)
            putInt(32_000)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize.toInt())
        }.array()
    }

    override fun getSize(): Long = header.size + dataSize

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        require(position >= 0 && offset >= 0 && size >= 0 && offset <= buffer.size - size)
        check(!closed) { "Audio source is closed" }
        if (size == 0) return 0
        if (position >= getSize()) return -1
        var copied = 0
        if (position < header.size) {
            copied = minOf(size, header.size - position.toInt())
            header.copyInto(buffer, offset, position.toInt(), position.toInt() + copied)
        }
        if (copied < size) {
            input.seek(position + copied - header.size)
            val count = input.read(buffer, offset + copied, size - copied)
            if (count > 0) copied += count
        }
        return if (copied == 0) -1 else copied
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            input.close()
        }
    }
}
