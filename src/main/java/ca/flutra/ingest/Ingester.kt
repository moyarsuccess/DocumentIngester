package ca.flutra.ingest

import ca.flutra.ingest.ocr.GoogleVisionOcrProvider
import ca.flutra.ingest.ocr.OllamaOcrRefiner
import kotlinx.coroutines.awaitAll

object Ingester {

    suspend fun startToIngest() {
        if (!EmbeddingStore.isEmpty()) return
        println("The db is empty, creating the knowledge base ...")
        val fileLoader = IngestionFileLoader.newInstance(GoogleVisionOcrProvider, OllamaOcrRefiner)
        fileLoader
            .load("knowledge-base")
            .map { IngestionChunker.chunk(it) }
            .awaitAll()
            .forEach { chunks -> EmbeddingStore.addDoc(chunks.map { it.asContent() }) }
    }
}