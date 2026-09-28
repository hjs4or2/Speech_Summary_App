package com.app.speechsummary.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Extracts an audio track from an audio or video document into 16 kHz mono signed PCM. */
object MediaAudioConverter {
    private const val TARGET_RATE = 16_000

    fun convert(context: Context, source: Uri, destination: File) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, source, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("선택한 파일에 음성 트랙이 없습니다.")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("오디오 형식을 알 수 없습니다.")
            if (mime == "audio/raw") {
                val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    else android.media.AudioFormat.ENCODING_PCM_16BIT
                destination.parentFile?.mkdirs()
                BufferedOutputStream(destination.outputStream()).use { output ->
                    val resampler = StreamingResampler(output)
                    val input = ByteBuffer.allocateDirect(64 * 1024)
                    while (true) {
                        input.clear()
                        val count = extractor.readSampleData(input, 0)
                        if (count < 0) break
                        input.position(0)
                        input.limit(count)
                        resampler.append(input.order(ByteOrder.LITTLE_ENDIAN), sampleRate, channels, encoding)
                        extractor.advance()
                    }
                }
                check(destination.length() > 0) { "변환된 음성 데이터가 없습니다." }
                return
            }
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            var inputEnded = false
            var outputEnded = false
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = android.media.AudioFormat.ENCODING_PCM_16BIT
            val info = MediaCodec.BufferInfo()
            destination.parentFile?.mkdirs()
            BufferedOutputStream(destination.outputStream()).use { output ->
                val resampler = StreamingResampler(output)
                while (!outputEnded) {
                    if (!inputEnded) {
                        val index = decoder.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val buffer = decoder.getInputBuffer(index) ?: error("디코더 입력 버퍼 오류")
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val index = decoder.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val out = decoder.outputFormat
                            sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            encoding = if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) out.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                else android.media.AudioFormat.ENCODING_PCM_16BIT
                        }
                        else -> if (index >= 0) {
                            if (info.size > 0) {
                                val buffer = decoder.getOutputBuffer(index) ?: error("디코더 출력 버퍼 오류")
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                resampler.append(buffer.slice().order(ByteOrder.LITTLE_ENDIAN), sampleRate, channels, encoding)
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            decoder.releaseOutputBuffer(index, false)
                        }
                    }
                }
            }
            check(destination.length() > 0) { "변환된 음성 데이터가 없습니다." }
        } catch (e: Exception) {
            destination.delete()
            throw e
        } finally {
            try { decoder?.stop() } catch (_: Exception) { }
            decoder?.release()
            extractor.release()
        }
    }

    private class StreamingResampler(private val output: BufferedOutputStream) {
        private var inputFrame = 0L
        private var nextOutput = 0.0
        private var previous = 0f

        fun append(buffer: ByteBuffer, rate: Int, channels: Int, encoding: Int) {
            require(rate > 0 && channels > 0) { "잘못된 음성 형식입니다." }
            val bytesPerSample = when (encoding) {
                android.media.AudioFormat.ENCODING_PCM_16BIT -> 2
                android.media.AudioFormat.ENCODING_PCM_FLOAT -> 4
                else -> error("지원되지 않는 PCM 인코딩: $encoding")
            }
            val frameBytes = bytesPerSample * channels
            while (buffer.remaining() >= frameBytes) {
                var mono = 0f
                repeat(channels) {
                    mono += if (bytesPerSample == 2) buffer.short / 32768f else buffer.float
                }
                mono /= channels
                if (inputFrame == 0L) previous = mono
                while (nextOutput <= inputFrame.toDouble()) {
                    val fraction = (nextOutput - (inputFrame - 1)).coerceIn(0.0, 1.0).toFloat()
                    val value = (previous + (mono - previous) * fraction).coerceIn(-1f, 1f)
                    val sample = (value * 32767).toInt()
                    output.write(sample and 0xff)
                    output.write((sample shr 8) and 0xff)
                    nextOutput += rate.toDouble() / TARGET_RATE
                }
                previous = mono
                inputFrame++
            }
        }
    }
}
