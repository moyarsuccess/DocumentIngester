package ca.flutra.rag

import ca.flutra.rag.common.Config
import ca.flutra.rag.common.ModelProvider
import ca.flutra.rag.embedding.EmbeddingStoreProvider
import ca.flutra.rag.eval.DocumentTextStore
import ca.flutra.rag.file_loaders.DocumentLoader
import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.document.splitter.DocumentSplitters
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

private val MANIFEST_PATH = Paths.get(".ingestion-manifest.txt")

fun main() = runBlocking {
    println("=== Starting ingestion ===")

    val alreadyIngested = loadManifest()
    if (alreadyIngested.isNotEmpty()) {
        println("Skipping ${alreadyIngested.size} already-ingested document(s).")
    }

    // 1. Load and OCR all new PDFs.
    val documents = DocumentLoader.load(alreadyIngested)
    if (documents.isEmpty()) {
        println("No new documents to ingest.")
        return@runBlocking
    }
    println("Loaded ${documents.size} new document(s).")

    // 2. Convert to LangChain4j Documents so the ingestor can process them.
    val lc4jDocs = documents.map { doc ->
        dev.langchain4j.data.document.Document.from(
            doc.text,
            Metadata.from(mapOf("source" to doc.source, "type" to doc.type)),
        )
    }

    // 3. Split → embed → store, all handled by EmbeddingStoreIngestor.
    //    DocumentSplitters.recursive splits on paragraph/sentence/word boundaries
    //    in that order, never cutting mid-sentence.
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
    println("Ingested and stored all chunks.")

    // 4. Cache raw document texts for the eval suite so it never needs to re-run OCR.
    DocumentTextStore.save(documents)

    // 5. Record newly ingested paths so future runs skip them.
    appendToManifest(documents.map { it.source })

    println("=== Ingestion complete ===")
}

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
