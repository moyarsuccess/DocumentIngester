package ca.flutra.ingest

import kotlinx.coroutines.awaitAll

object Ingester {

    suspend fun startToIngest() {
        if (!EmbeddingStore.isEmpty()) return
        println("The db is empty, creating the knowledge base ...")
        val fileLoader = IngestionFileLoader.newInstance(OcrProviderImpl, "md", "pdf")
        fileLoader
            .load("knowledge-base")
            .map { IngestionChunker.chunk(it) }
            .awaitAll()
            .forEach { chunks -> EmbeddingStore.addDoc(chunks.map { it.asContent() }) }
    }
}