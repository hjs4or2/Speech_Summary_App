package com.app.speechsummary.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class StoredDocument(
    val transcript: String,
    val transcriptHash: String,
    val document: String?,
    val documentSourceHash: String?
) {
    val stale: Boolean get() = document != null && documentSourceHash != transcriptHash
}

/** Each recording has one independently committed UTF-8 result. A failed operation never replaces it. */
class DocumentRepository(context: Context) {
    private val directory = File(context.filesDir, "documents").also { it.mkdirs() }

    private fun file(id: String): AtomicFile {
        require(Regex("[A-Za-z0-9_.-]+\\.pcm").matches(id)) { "잘못된 녹음 ID" }
        return AtomicFile(File(directory, "$id.json"))
    }

    fun read(id: String): StoredDocument? = try {
        val json = JSONObject(file(id).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        val transcript = json.getString("transcript")
        StoredDocument(
            transcript,
            hash(transcript),
            json.optString("document").takeIf { json.has("document") },
            json.optString("documentSourceHash").takeIf { json.has("documentSourceHash") }
        )
    } catch (_: java.io.FileNotFoundException) {
        null
    }

    fun readAll(ids: List<String>): Map<String, StoredDocument> = ids.mapNotNull { id ->
        // One damaged JSON file must not hide other recordings or their saved work.
        runCatching { read(id) }.getOrNull()?.let { id to it }
    }.toMap()

    fun saveTranscript(id: String, transcript: String): StoredDocument {
        val old = read(id)
        val result = StoredDocument(transcript, hash(transcript), old?.document, old?.documentSourceHash)
        write(id, result)
        return result
    }

    fun saveDocument(id: String, expectedTranscriptHash: String, document: String): StoredDocument {
        require(document.isNotBlank()) { "빈 문서는 저장할 수 없어." }
        val old = requireNotNull(read(id)) { "원문이 없어." }
        require(old.transcriptHash == expectedTranscriptHash) { "원문이 변경됐어. 다시 문서화해 줘." }
        val result = old.copy(document = document, documentSourceHash = expectedTranscriptHash)
        write(id, result)
        return result
    }

    private fun write(id: String, value: StoredDocument) {
        val json = JSONObject().put("transcript", value.transcript)
        value.document?.let { json.put("document", it) }
        value.documentSourceHash?.let { json.put("documentSourceHash", it) }
        val atomic = file(id)
        val stream = atomic.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }

    companion object {
        fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
