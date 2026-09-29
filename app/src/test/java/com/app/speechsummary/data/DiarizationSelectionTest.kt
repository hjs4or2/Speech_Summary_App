package com.app.speechsummary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiarizationSelectionTest {
    @Test fun selectionPersistsAndUnknownValueUsesExistingEngine() {
        var stored: String? = null
        val selection = DiarizationSelection({ stored }, { stored = it })
        assertEquals(DiarizationEngine.SHERPA, selection.load())
        selection.save(DiarizationEngine.NEMOTRON)
        assertEquals("NEMOTRON", stored)
        assertEquals(DiarizationEngine.NEMOTRON, DiarizationSelection({ stored }, { stored = it }).load())
        stored = "removed_engine"
        assertEquals(DiarizationEngine.SHERPA, selection.load())
    }

    @Test fun nemotronRejectsNineBeforeSttAndSherpaRetainsLargeMeetings() {
        assertTrue(DiarizationEngine.NEMOTRON.acceptsSpeakerCount(null))
        assertTrue(DiarizationEngine.NEMOTRON.acceptsSpeakerCount(8))
        assertFalse(DiarizationEngine.NEMOTRON.acceptsSpeakerCount(9))
        assertTrue(DiarizationEngine.SHERPA.acceptsSpeakerCount(9))
        assertEquals(setOf(ModelKind.WHISPER, ModelKind.NEMOTRON, ModelKind.QWEN),
            DiarizationEngine.NEMOTRON.requiredModels())
    }
}
