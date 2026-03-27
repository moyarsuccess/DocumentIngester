package ca.flutra.rag.eval

import ca.flutra.rag.common.Document
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File

internal data class ChunkConfig(val chunkSize: Int, val chunkOverlap: Int)

private data class IngestState(
    val chunkSize: Int,
    val chunkOverlap: Int,
    val documents: List<Document>,
)

/**
 * Single source of truth for ingested documents. Persists raw OCR-extracted
 * document texts and the chunk config used to embed them.
 *
 * Serves three purposes:
 *  1. Tracks which files have already been ingested (by source path) so future
 *     ingest runs can skip OCR on them.
 *  2. Provides cached document text so the eval suite never needs to re-run OCR.
 *  3. Stores the chunk config (size + overlap) so ingest can detect when settings
 *     have changed and trigger a re-chunk of all cached documents.
 *
 * Written by [save] at the end of each ingest run.
 * Read by [load] / [loadChunkConfig] at the start of ingest.
 */
internal object DocumentTextStore {

    private val FILE = File("ingested-texts.json")
    private val gson = Gson()
    private val listType = object : TypeToken<List<Document>>() {}.type

    /**
     * Merges [documents] into the store and persists [chunkConfig].
     * Existing entries are preserved; entries with the same source are overwritten.
     */
    fun save(documents: List<Document>, chunkConfig: ChunkConfig) {
        val existing = load().associateBy { it.source }.toMutableMap()
        documents.forEach { existing[it.source] = it }
        val state = IngestState(
            chunkSize = chunkConfig.chunkSize,
            chunkOverlap = chunkConfig.chunkOverlap,
            documents = existing.values.toList(),
        )
        FILE.writeText(gson.toJson(state))
        println("Saved ${existing.size} document text(s) to ${FILE.absolutePath}")
    }

    /**
     * Returns the chunk config from the last ingest run, or null if the store
     * does not exist yet or was written by an older version without config.
     */
    fun loadChunkConfig(): ChunkConfig? {
        if (!FILE.exists()) return null
        return try {
            val state = gson.fromJson(FILE.readText(), IngestState::class.java)
            if (state?.documents != null) ChunkConfig(state.chunkSize, state.chunkOverlap) else null
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    /**
     * Loads all previously saved document texts.
     * Supports both the current wrapped format and the legacy bare-list format.
     * Returns an empty list if the file does not exist yet.
     */
    fun load(): List<Document> {
        if (!FILE.exists()) return emptyList()
        val text = FILE.readText()
        return try {
            // Current format: { chunkSize, chunkOverlap, documents: [...] }
            val state = gson.fromJson(text, IngestState::class.java)
            if (state?.documents != null) state.documents else emptyList()
        } catch (_: JsonSyntaxException) {
            // Legacy format: bare JSON array
            gson.fromJson(text, listType) ?: emptyList()
        }
    }
}
