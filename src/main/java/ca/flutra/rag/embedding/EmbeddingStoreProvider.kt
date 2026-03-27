package ca.flutra.rag.embedding

import ca.flutra.rag.common.Config
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore
import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import io.qdrant.client.grpc.Collections

internal object EmbeddingStoreProvider {

    private val qdrantClient: QdrantClient by lazy {
        QdrantClient(
            QdrantGrpcClient.newBuilder(Config.QDRANT_HOST, Config.QDRANT_GRPC_PORT, false).build()
        )
    }

    val store by lazy {
        ensureCollectionExistsWithCorrectDimensions()
        QdrantEmbeddingStore.builder()
            .host(Config.QDRANT_HOST)
            .port(Config.QDRANT_GRPC_PORT)
            .collectionName(Config.COLLECTION_NAME)
            .build()
    }

    private fun ensureCollectionExistsWithCorrectDimensions() {
        if (!qdrantClient.collectionExists(Config.COLLECTION_NAME)) {
            createCollection()
            return
        }

        val info = qdrantClient.getCollectionInfoAsync(Config.COLLECTION_NAME).get()
        val storedSize = info.config.params.vectorsConfig.params.size
        
        if (storedSize == Config.EMBEDDING_DIMENSIONS.toLong()) {
            println(
                "Qdrant collection '${Config.COLLECTION_NAME}' exists with " +
                "$storedSize dimensions — OK."
            )
            return
        }

        println(
            "WARNING: Qdrant collection '${Config.COLLECTION_NAME}' has vector size " +
            "$storedSize but Config.EMBEDDING_DIMENSIONS=${Config.EMBEDDING_DIMENSIONS}. " +
            "Dropping and recreating the collection — all previously stored vectors will be lost."
        )
        qdrantClient.deleteCollectionAsync(Config.COLLECTION_NAME).get()
        createCollection()
    }

    private fun createCollection() {
        qdrantClient.createCollectionAsync(
            Config.COLLECTION_NAME,
            Collections.VectorParams.newBuilder()
                .setSize(Config.EMBEDDING_DIMENSIONS.toLong())
                .setDistance(Collections.Distance.Cosine)
                .build()
        ).get()
        println("Created Qdrant collection '${Config.COLLECTION_NAME}' with ${Config.EMBEDDING_DIMENSIONS} dimensions")
    }

    /**
     * Drops the Qdrant collection so it can be recreated on the next [store] access.
     * Call this before re-ingesting with a new chunk config.
     */
    fun resetCollection() {
        if (qdrantClient.collectionExists(Config.COLLECTION_NAME)) {
            qdrantClient.deleteCollectionAsync(Config.COLLECTION_NAME).get()
            println("Dropped Qdrant collection '${Config.COLLECTION_NAME}' — will recreate on next ingest.")
        }
    }

    private fun QdrantClient.collectionExists(collectionName: String): Boolean =
        listCollectionsAsync().get().contains(collectionName)
}
