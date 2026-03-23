package ca.flutra.new

import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore
import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import io.qdrant.client.grpc.Collections

object EmbeddingStoreProvider {

    private val qdrantClient: QdrantClient by lazy {
        QdrantClient(
            QdrantGrpcClient.newBuilder("localhost", 6334, false).build()
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
                .setSize(1024)
                .setDistance(Collections.Distance.Cosine)
                .build()
        ).get()
        println("Created Qdrant collection '${Config.COLLECTION_NAME}' with 1024 dimensions")
    }

    private fun QdrantClient.collectionExists(collectionName: String): Boolean =
        listCollectionsAsync().get().contains(collectionName)
}
