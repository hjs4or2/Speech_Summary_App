package com.app.speechsummary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.speechsummary.data.DownloadableModels
import com.app.speechsummary.data.LocalModelRepository
import com.app.speechsummary.data.ModelCatalog
import com.app.speechsummary.data.ModelKind
import java.io.File
import java.io.DataInputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class ModelStorageTest {
    @Test fun officialSpeechModelsInstallInPrivateStorage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val models = DownloadableModels(context)
        listOf(ModelKind.WHISPER, ModelKind.SEGMENTATION, ModelKind.EMBEDDING).forEach { kind ->
            val asset = ModelCatalog.assets.first { it.kind == kind }
            models.download(asset, allowMetered = true) { }
            val installed = models.file(kind)
            assertTrue(installed.isFile)
            assertTrue(installed.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + File.separator))
        }
    }

    @Test fun interruptedDownloadResumesFromPrivatePartialFile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val models = DownloadableModels(context)
        val sourceAsset = ModelCatalog.assets.first { it.kind == ModelKind.SEGMENTATION }
        // Keep the normal installed model intact; exercise Range with a private fixture name.
        models.download(sourceAsset, allowMetered = true) { }
        val fixture = sourceAsset.copy(filename = "segmentation-resume-test.onnx")
        val partial = File(context.noBackupFilesDir, "models/${fixture.filename}.part")
        val destination = File(context.noBackupFilesDir, "models/${fixture.filename}")
        try {
            models.file(ModelKind.SEGMENTATION).inputStream().use { input ->
                partial.outputStream().use { output ->
                    val prefix = ByteArray(16 * 1024)
                    DataInputStream(input).readFully(prefix)
                    output.write(prefix)
                }
            }
            assertTrue(partial.length() == 16 * 1024L)
            var progress = 0L
            models.download(fixture, allowMetered = true) { progress = it }
            assertTrue(progress == fixture.bytes)
            assertTrue(destination.isFile)
            assertFalse(partial.exists())
        } finally {
            partial.delete()
            destination.delete()
        }
    }

    @Test fun officialSegmentationDownloadVerifiesAndReuses() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val models = DownloadableModels(context)
        val asset = ModelCatalog.assets.first { it.kind == ModelKind.SEGMENTATION }
        var progress = 0L
        models.download(asset, allowMetered = true) { progress = it }
        assertTrue(models.file(ModelKind.SEGMENTATION).isFile)
        assertTrue(progress == asset.bytes)
        // The second call returns from the verified local file before checking network.
        var reused = 0L
        models.download(asset, allowMetered = false) { reused = it }
        assertTrue(reused == asset.bytes)
    }

    @Test fun verifiedLegacyQwenMovesIntoPrivateNoBackupStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val legacy = File(context.filesDir, "models/${LocalModelRepository.FILE_NAME}")
        val models = DownloadableModels(context)
        val installed = models.file(ModelKind.QWEN)
        assumeTrue("Install or import Qwen before the migration test", legacy.isFile || installed.isFile)

        val missing = models.missing().map { it.kind }
        assertFalse(ModelKind.QWEN in missing)
        assertTrue(installed.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + File.separator))
        assertTrue(LocalModelRepository(context).isInstalled())
        assertFalse(legacy.exists())
    }
}
