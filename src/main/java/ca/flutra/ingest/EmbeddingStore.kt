package ca.flutra.ingest

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.ollama.OllamaEmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore
import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import io.qdrant.client.grpc.Collections
import java.time.Duration

object EmbeddingStore {

    private const val VECTOR_SIZE = 1024L
    private const val DB_HOST = "localhost"
    private const val COLLECTION_NAME = "embedding"
    private const val DB_PORT = 6334
    private val qdrantClient: QdrantClient by lazy {
        QdrantClient(
            QdrantGrpcClient.newBuilder("localhost", 6334, false).build()
        )
    }

    private val embeddingStore by lazy {
        ensureCollectionExists()
        QdrantEmbeddingStore.builder()
            .host(DB_HOST)
            .port(DB_PORT)
            .collectionName(COLLECTION_NAME)
            .build()
    }

    private val embeddingModel = OllamaEmbeddingModel.builder()
        .baseUrl("http://localhost:11434")
        .modelName("mxbai-embed-large")
        .timeout(Duration.ofMinutes(2))
        .build()

    init {
        ensureCollectionExists()
    }

    fun clearDb() {
        if (!qdrantClient.collectionExists(COLLECTION_NAME)) return
        qdrantClient.deleteCollectionAsync(COLLECTION_NAME).get()
        println("Deleted collection '$COLLECTION_NAME'")
        ensureCollectionExists()
        println("Recreated empty collection '$COLLECTION_NAME'")
    }

    fun addDoc(chunks: List<String>) {
        val segments = chunks.map { TextSegment.from(it) }
        val embeddings = embeddingModel.embedAll(segments).content()
        embeddingStore.addAll(embeddings, segments)  // pass segments as second argument!
    }

    fun isEmpty(): Boolean {
        if (!qdrantClient.collectionExists(COLLECTION_NAME)) return true
        return qdrantClient.countAsync(COLLECTION_NAME).get() == 0L
    }

    fun provideChunks(maxResult: Int, input: String): List<TextSegment> {
        val queryEmbedding = embeddingModel.embed(input).content()
        println("Query embedding dimensions: ${queryEmbedding.vectorAsList().size}")

        val request = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(maxResult)
            .minScore(0.0)  // add this — default min score might be filtering everything out
            .build()

        val results = embeddingStore.search(request).matches()
        println("Search returned ${results.size} results")

        return results.map { it.embedded() }
    }

    private fun ensureCollectionExists() {
        if (qdrantClient.collectionExists(COLLECTION_NAME)) return
        qdrantClient.createCollectionAsync(
            COLLECTION_NAME,
            Collections.VectorParams.newBuilder()
                .setSize(VECTOR_SIZE)
                .setDistance(Collections.Distance.Cosine)
                .build()
        ).get()
        println("Created Qdrant collection '$COLLECTION_NAME' with $VECTOR_SIZE dimensions")
    }

    private fun QdrantClient.collectionExists(collectionName: String): Boolean =
        listCollectionsAsync().get().contains(collectionName)
}