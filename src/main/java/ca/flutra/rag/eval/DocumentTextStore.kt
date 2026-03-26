package ca.flutra.rag.eval

import ca.flutra.rag.common.Document
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Persists the raw OCR-extracted document texts to disk during ingestion so
 * the eval suite can access them without re-running OCR.
 *
 * Written by [save] at the end of each ingest run.
 * Read by [load] when the eval suite needs document text to generate Q&A pairs.
 *
 * File: ingested-texts.json (written next to .ingestion-manifest.txt)
 */
internal object DocumentTextStore {

    private val FILE = File("ingested-texts.json")
    private val gson = Gson()
    private val listType = object : TypeToken<List<Document>>() {}.type

    /** Appends [documents] to the store, merging with any previously saved entries. */
    fun save(documents: List<Document>) {
        val existing = load().associateBy { it.source }.toMutableMap()
        documents.forEach { existing[it.source] = it }
        FILE.writeText(gson.toJson(existing.values.toList()))
        println("Saved ${existing.size} document text(s) to ${FILE.absolutePath}")
    }

    /**
     * Loads all previously saved document texts.
     * Returns an empty list if the file does not exist yet (ingest hasn't run).
     */
    fun load(): List<Document> {
        if (!FILE.exists()) return emptyList()
        return gson.fromJson(FILE.readText(), listType)
    }
}
