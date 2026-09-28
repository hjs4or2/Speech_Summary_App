package com.app.speechsummary.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

data class PlaybackState(
    val itemId: String? = null,
    val preparing: Boolean = false,
    val playing: Boolean = false,
    val error: String? = null
)

/** All controls and callbacks run on the main thread; decoding is asynchronous. */
class AudioPlaybackController(
    context: Context,
    private val onStateChanged: (PlaybackState) -> Unit,
    private val onError: (String) -> Unit
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            if (change < 0) pause()
        }, Handler(Looper.getMainLooper()))
        .build()
    private var player: MediaPlayer? = null
    private var source: PcmWaveDataSource? = null
    private var state = PlaybackState()

    private fun publish(next: PlaybackState) {
        state = next
        onStateChanged(next)
    }

    fun toggle(itemId: String, file: File) {
        if (state.itemId == itemId && player != null) {
            if (state.preparing) return
            if (state.playing) pause() else resume()
            return
        }
        stop()
        publish(PlaybackState(itemId = itemId, preparing = true))
        try {
            source = PcmWaveDataSource(file)
            val current = MediaPlayer()
            player = current
            current.setAudioAttributes(attributes)
            current.setOnPreparedListener {
                if (player === it) {
                    publish(state.copy(preparing = false))
                    resume()
                }
            }
            current.setOnCompletionListener { if (player === it) stop() }
            current.setOnErrorListener { failed, what, extra ->
                if (player === failed) fail("오디오 재생 오류 ($what/$extra)")
                true
            }
            current.setDataSource(source!!)
            current.prepareAsync()
        } catch (e: Exception) {
            fail(e.message ?: "오디오를 재생할 수 없습니다.")
        }
    }

    private fun resume() {
        val current = player ?: return
        try {
            check(audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                "다른 앱이 오디오를 사용 중입니다. 잠시 후 다시 재생해 주세요."
            }
            current.start()
            publish(state.copy(playing = true))
        } catch (e: Exception) {
            fail(e.message ?: "재생을 시작할 수 없습니다.")
        }
    }

    fun pause() {
        if (state.preparing) {
            stop()
            return
        }
        if (!state.playing) return
        try {
            player?.pause()
            audioManager.abandonAudioFocusRequest(focusRequest)
            publish(state.copy(playing = false))
        } catch (e: Exception) {
            fail(e.message ?: "재생을 일시정지할 수 없습니다.")
        }
    }

    fun stop() {
        val previous = player
        player = null
        previous?.release()
        source?.close()
        source = null
        audioManager.abandonAudioFocusRequest(focusRequest)
        publish(PlaybackState())
    }

    private fun fail(message: String) {
        val itemId = state.itemId
        stop()
        publish(PlaybackState(itemId = itemId, error = message))
        onError(message)
    }
}
