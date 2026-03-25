package ca.flutra.rag

import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore
import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import io.qdrant.client.grpc.Collections

object EmbeddingStoreProvider {

    private val qdrantClient: QdrantClient by lazy {
        QdrantClient(
            QdrantGrpcClient.newBuilder(Config.QDRANT_HOST, Config.QDRANT_PORT, false).build()
        )
    }

    val store by lazy {
        ensureCollectionExists()
        QdrantEmbeddingStore.builder()
            .host(Config.QDRANT_HOST)
            .port(Config.QDRANT_PORT)
            .collectionName(Config.COLLECTION_NAME)
            .build()
    }

    private fun ensureCollectionExists() {
        if (qdrantClient.collectionExists(Config.COLLECTION_NAME)) return
        qdrantClient.createCollectionAsync(
            Config.COLLECTION_NAME,
            Collections.VectorParams.newBuilder()
                .setSize(Config.EMBEDDING_DIMENSIONS.toLong())
                .setDistance(Collections.Distance.Cosine)
                .build()
        ).get()
        println("Created Qdrant collection '${Config.COLLECTION_NAME}' with ${Config.EMBEDDING_DIMENSIONS} dimensions")
    }

    private fun QdrantClient.collectionExists(collectionName: String): Boolean =
        listCollectionsAsync().get().contains(collectionName)
}
