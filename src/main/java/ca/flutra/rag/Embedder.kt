package ca.flutra.rag

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment

object Embedder {

    /**
     * Embeds all chunks and upserts them into Qdrant.
     * Equivalent of Python's create_embeddings().
     */
    fun embedAndStore(chunks: List<Result>) {
        val store = EmbeddingStoreProvider.store
        val embeddingModel = ModelProvider.embeddingModel

        val segments = chunks.map { chunk ->
            TextSegment.from(
                chunk.pageContent,
                Metadata().apply {
                    chunk.metadata.forEach { (k, v) -> put(k, v) }
                }
            )
        }

        println("Embedding ${segments.size} chunks...")

        // Batch embed for efficiency
        val embeddings: List<Embedding> = embeddingModel
            .embedAll(segments)
            .content()

        store.addAll(embeddings, segments)

        println("Stored ${chunks.size} chunks in vector store")
    }
}
