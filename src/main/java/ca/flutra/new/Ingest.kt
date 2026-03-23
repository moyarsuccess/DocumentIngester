package ca.flutra.new

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("=== Starting ingestion ===")

    val documents = DocumentLoader.load()

    println("Chunking ${documents.size} documents...")
    val chunks = Chunker.createChunks(documents)
    println("Created ${chunks.size} chunks")

    Embedder.embedAndStore(chunks)

    println("=== Ingestion complete ===")
}
