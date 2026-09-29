package com.app.speechsummary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperModelSelectionTest {
    @Test fun selectionPersistsAndExistingInstallDefaultsToBase() {
        var stored: String? = null
        val selection = WhisperModelSelection({ stored }, { stored = it })
        assertEquals(WhisperModel.BASE, selection.load())
        selection.save(WhisperModel.SMALL)
        assertEquals("SMALL", stored)
        assertEquals(WhisperModel.SMALL, WhisperModelSelection({ stored }, { stored = it }).load())
        stored = "unknown"
        assertEquals(WhisperModel.BASE, selection.load())
    }

    @Test fun selectedModelAloneDeterminesSttReadinessAndDownloadSet() {
        for (engine in DiarizationEngine.entries) {
            val base = engine.requiredModels(WhisperModel.BASE)
            val small = engine.requiredModels(WhisperModel.SMALL)
            assertTrue(ModelKind.WHISPER in base)
            assertFalse(ModelKind.WHISPER_SMALL in base)
            assertTrue(ModelKind.WHISPER_SMALL in small)
            assertFalse(ModelKind.WHISPER in small)
            assertEquals(base - ModelKind.WHISPER, small - ModelKind.WHISPER_SMALL)
            val installed = ModelKind.entries.toSet() - ModelKind.WHISPER_SMALL
            assertTrue(installed.containsAll(base))
            assertFalse(installed.containsAll(small))
            assertEquals(listOf(ModelKind.WHISPER_SMALL),
                ModelCatalog.assets.filter { it.kind in small && it.kind !in installed }.map { it.kind })
        }
    }

    @Test fun modelKindsRouteToDistinctVerifiedFiles() {
        val base = ModelCatalog.assets.single { it.kind == WhisperModel.BASE.kind }
        val small = ModelCatalog.assets.single { it.kind == WhisperModel.SMALL.kind }
        assertEquals("ggml-base-q5_1.bin", base.filename)
        assertEquals("ggml-small.bin", small.filename)
        assertFalse(base.filename == small.filename)
    }
}
