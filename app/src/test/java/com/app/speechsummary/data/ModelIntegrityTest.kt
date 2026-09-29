package com.app.speechsummary.data

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelIntegrityTest {
    @Test fun catalogContainsPinnedOfficialAssets() {
        assertEquals(ModelKind.entries.toSet(), ModelCatalog.assets.map { it.kind }.toSet())
        assertEquals(1_812_776_723L, ModelCatalog.totalBytes)
        ModelCatalog.assets.forEach { asset ->
            assertTrue(asset.url.startsWith("https://huggingface.co/"))
            assertTrue(asset.url.contains(Regex("/resolve/[0-9a-f]{40}/")))
            assertTrue(asset.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(asset.bytes > 0)
        }
    }

    @Test fun mobileModelsArePinnedAndDoNotReplaceTheOldMediumFile() {
        val whisper = ModelCatalog.assets.single { it.kind == ModelKind.WHISPER }
        assertEquals("ggml-base-q5_1.bin", whisper.filename)
        assertEquals(59_707_625L, whisper.bytes)
        assertEquals("422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898", whisper.sha256)
        val small = ModelCatalog.assets.single { it.kind == ModelKind.WHISPER_SMALL }
        assertEquals("ggml-small.bin", small.filename)
        assertEquals(487_601_967L, small.bytes)
        assertEquals("1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b", small.sha256)
        assertEquals("https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-small.bin", small.url)
        val nemotron = ModelCatalog.assets.single { it.kind == ModelKind.NEMOTRON }
        assertEquals("Nemotron-3-Diarization.q8_0.gguf", nemotron.filename)
        assertEquals(107_012_128L, nemotron.bytes)
        assertEquals("08456d9e22cd9a323c0364d98375f3746d6e68507ebb705cd46438c534c7a3a1", nemotron.sha256)
    }

    @Test fun verificationRejectsWrongSizeAndHash() {
        val file = File.createTempFile("model-integrity", ".bin")
        try {
            val bytes = "verified model".toByteArray()
            file.writeBytes(bytes)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val asset = ModelAsset(ModelKind.WHISPER, "test", file.name, bytes.size.toLong(), hash, "https://example.com")
            assertTrue(ModelIntegrity.matches(file, asset))
            assertFalse(ModelIntegrity.matches(file, asset.copy(bytes = asset.bytes + 1)))
            assertFalse(ModelIntegrity.matches(file, asset.copy(sha256 = "0".repeat(64))))
        } finally {
            file.delete()
        }
    }

    @Test fun resumeOnlyAcceptsExactRemainingRange() {
        assertTrue(ModelIntegrity.rangeStartsAt("bytes 10-99/100", 10, 100))
        assertFalse(ModelIntegrity.rangeStartsAt("bytes 0-99/100", 10, 100))
        assertFalse(ModelIntegrity.rangeStartsAt("bytes 10-90/100", 10, 100))
        assertFalse(ModelIntegrity.rangeStartsAt("bytes 10-99/101", 10, 100))
    }
}
