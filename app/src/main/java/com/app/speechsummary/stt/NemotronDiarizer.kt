package com.app.speechsummary.stt

import com.whispercpp.whisper.WhisperSegment
import java.io.File

/** Native NeMo-Speech.cpp Sortformer inference; audio remains in app-local storage. */
internal object NemotronNative {
    init { System.loadLibrary("nemotron_diar") }
    external fun diarize(modelPath: String, pcmPath: String): DoubleArray
}

internal object NemotronDiarizer {
    fun label(model: File, pcm: File, segments: List<WhisperSegment>): List<String> {
        require(model.isFile) { "Nemotron 3 모델이 설치되지 않았어." }
        if (segments.isEmpty()) return emptyList()
        val raw = NemotronNative.diarize(model.absolutePath, pcm.absolutePath)
        require(raw.size % 3 == 0) { "Nemotron 결과 형식이 잘못됐어." }
        val turns = raw.toList().chunked(3).map { (start, end, speaker) ->
            SpeakerAlignment.Turn(start, end, speaker.toInt())
        }
        return SpeakerAlignment.alignTurns(segments, turns)
    }
}
