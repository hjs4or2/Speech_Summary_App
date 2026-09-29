package com.app.speechsummary.stt

import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment
import com.whispercpp.whisper.WhisperSegment

/** Match each text segment to the voice occupying most of its time span. */
internal object SpeakerAlignment {
    data class Turn(val start: Double, val end: Double, val speaker: Int)

    fun align(
        segments: List<WhisperSegment>,
        turns: List<OfflineSpeakerDiarizationSegment>
    ): List<String> = alignTurns(segments, turns.map { Turn(it.start.toDouble(), it.end.toDouble(), it.speaker) })

    fun alignTurns(segments: List<WhisperSegment>, turns: List<Turn>): List<String> {
        val speakerOrder = turns.map { it.speaker }.distinct()
        val bySpeaker = turns.groupBy { it.speaker }
        return segments.map { segment ->
            val best = bySpeaker.maxByOrNull { (_, voiceTurns) ->
                voiceTurns.sumOf { overlap(segment, it) }
            }
            if (best != null && best.value.sumOf { overlap(segment, it) } > 0.0) {
                letter(speakerOrder.indexOf(best.key))
            } else "?"
        }
    }

    private fun overlap(text: WhisperSegment, turn: Turn): Double =
        (minOf(text.endSeconds, turn.end) - maxOf(text.startSeconds, turn.start)).coerceAtLeast(0.0)

    private fun letter(index: Int): String {
        var value = index
        var result = ""
        do {
            result = ('A' + value % 26) + result
            value = value / 26 - 1
        } while (value >= 0)
        return result
    }
}
