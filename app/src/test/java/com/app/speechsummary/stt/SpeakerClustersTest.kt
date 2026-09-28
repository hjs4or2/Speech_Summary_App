package com.app.speechsummary.stt

import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment
import com.whispercpp.whisper.WhisperSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerAlignmentTest {
    @Test fun assignsStableLettersByFirstSpeakerAppearance() {
        val turns = listOf(
            OfflineSpeakerDiarizationSegment(0f, 2f, 7, 1f),
            OfflineSpeakerDiarizationSegment(2f, 4f, 3, 1f),
            OfflineSpeakerDiarizationSegment(4f, 6f, 7, 1f)
        )
        val text = listOf(
            WhisperSegment(0.2, 1.8, "hello"),
            WhisperSegment(2.2, 3.8, "hi"),
            WhisperSegment(4.2, 5.8, "again"),
            WhisperSegment(7.0, 8.0, "unknown")
        )
        assertEquals(listOf("A", "B", "A", "?"), SpeakerAlignment.align(text, turns))
    }
}
