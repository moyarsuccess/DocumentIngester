package ca.flutra.rag

import ca.flutra.rag.common.Config
import ca.flutra.rag.common.Document
import ca.flutra.rag.common.ModelProvider
import ca.flutra.rag.embedding.EmbeddingStoreProvider
import ca.flutra.rag.eval.ChunkConfig
import ca.flutra.rag.eval.DocumentTextStore
import ca.flutra.rag.file_loaders.DocumentLoader
import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.document.splitter.DocumentSplitters
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("=== Starting ingestion ===")

    val cachedDocuments = DocumentTextStore.load()
    val cachedChunkConfig = DocumentTextStore.loadChunkConfig()
    val currentChunkConfig = ChunkConfig(Config.CHUNK_SIZE_TOKENS, Config.CHUNK_OVERLAP_TOKENS)

    // Detect whether the chunk settings have changed since the last ingest run.
    // If they have, skip OCR entirely and re-chunk everything from the cached texts.
    val rechunk = cachedChunkConfig != null
        && cachedChunkConfig != currentChunkConfig
        && cachedDocuments.isNotEmpty()

    val documentsToEmbed: List<Document>

    if (rechunk) {
        println(
            "Chunk config changed " +
            "(size ${cachedChunkConfig.chunkSize}→${currentChunkConfig.chunkSize}, " +
            "overlap ${cachedChunkConfig.chunkOverlap}→${currentChunkConfig.chunkOverlap}). " +
            "Re-chunking ${cachedDocuments.size} cached document(s) — skipping OCR."
        )
        EmbeddingStoreProvider.resetCollection()
        documentsToEmbed = cachedDocuments
    } else {
        // Normal run: only OCR and embed files not yet in the store.
        val alreadyIngested = cachedDocuments.map { it.source }.toSet()
        if (alreadyIngested.isNotEmpty()) {
            println("Skipping ${alreadyIngested.size} already-ingested document(s).")
        }

        val newDocuments = DocumentLoader.load(alreadyIngested)
        if (newDocuments.isEmpty()) {
            println("No new documents to ingest.")
            return@runBlocking
        }
        println("Loaded ${newDocuments.size} new document(s).")
        documentsToEmbed = newDocuments
    }

    // Convert to LangChain4j Documents so the ingestor can process them.
    val lc4jDocs = documentsToEmbed.map { doc ->
        dev.langchain4j.data.document.Document.from(
            doc.text,
            Metadata.from(mapOf("source" to doc.source, "type" to doc.type)),
        )
    }

    // Split → embed → store.
    // DocumentSplitters.recursive splits on paragraph/sentence/word boundaries
    // in that order, never cutting mid-sentence.
    val ingestor = EmbeddingStoreIngestor.builder()
        .documentSplitter(
            DocumentSplitters.recursive(
                Config.CHUNK_SIZE_TOKENS,
                Config.CHUNK_OVERLAP_TOKENS,
            )
        )
        .embeddingModel(ModelProvider.embeddingModel)   // plain model — no prefix for passages
        .embeddingStore(EmbeddingStoreProvider.store)
        .build()

    ingestor.ingest(lc4jDocs)
    println("Chunked and embedded ${documentsToEmbed.size} document(s).")

    // Persist texts and current chunk config.
    // On a re-chunk run, documentsToEmbed == cachedDocuments so the store is unchanged
    // except for the updated config. On a normal run, new docs are merged in.
    DocumentTextStore.save(documentsToEmbed, currentChunkConfig)

    println("=== Ingestion complete ===")
}
