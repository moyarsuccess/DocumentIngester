package ca.flutra.rag

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

// File that records which document paths have already been ingested.
// On each run we skip files listed here, so re-running never creates duplicates in Qdrant.
private val MANIFEST_PATH = Paths.get(".ingestion-manifest.txt")

private fun loadManifest(): Set<String> {
    if (!Files.exists(MANIFEST_PATH)) return emptySet()
    return Files.readAllLines(MANIFEST_PATH).filter { it.isNotBlank() }.toSet()
}

private fun appendToManifest(sources: List<String>) {
    if (sources.isEmpty()) return
    Files.write(
        MANIFEST_PATH,
        (sources + listOf("")).joinToString("\n").toByteArray(),
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
    )
}

fun main() = runBlocking {
    println("=== Starting ingestion ===")

    val alreadyIngested = loadManifest()
    if (alreadyIngested.isNotEmpty()) {
        println("Skipping ${alreadyIngested.size} already-ingested document(s).")
    }

    val documents = DocumentLoader.load(alreadyIngested)

    if (documents.isEmpty()) {
        println("No new documents to ingest.")
        return@runBlocking
    }

    println("Chunking ${documents.size} new document(s)...")
    val chunks = Chunker.createChunks(documents)
    println("Created ${chunks.size} chunks")

    Embedder.embedAndStore(chunks)

    // Record newly ingested paths so future runs skip them.
    appendToManifest(documents.map { it.source })

    println("=== Ingestion complete ===")
}
