package ca.flutra.rag

import ca.flutra.rag.common.Config
import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import java.io.File

/**
 * Wipes everything so you can start fresh:
 *  - Drops the Qdrant vector collection
 *  - Deletes .ingestion-manifest.txt
 *  - Deletes ingested-texts.json
 *  - Deletes eval-dataset.json
 *
 * Run this, then re-run Ingest, then re-run Eval.
 */
fun main() {
    println("=== Resetting all pipeline state ===\n")

    // 1. Drop the Qdrant collection
    try {
        val client = QdrantClient(
            QdrantGrpcClient.newBuilder(Config.QDRANT_HOST, Config.QDRANT_PORT, false).build()
        )
        val collections = client.listCollectionsAsync().get()
        if (Config.COLLECTION_NAME in collections) {
            client.deleteCollectionAsync(Config.COLLECTION_NAME).get()
            println("✓ Dropped Qdrant collection '${Config.COLLECTION_NAME}'")
        } else {
            println("– Qdrant collection '${Config.COLLECTION_NAME}' did not exist, skipping")
        }
        client.close()
    } catch (e: Exception) {
        println("✗ Could not connect to Qdrant: ${e.message}")
    }

    // 2. Delete local state files
    listOf(
        File(".ingestion-manifest.txt"),
        File("ingested-texts.json"),
        File("eval-dataset.json"),
    ).forEach { file ->
        if (file.exists()) {
            file.delete()
            println("✓ Deleted ${file.name}")
        } else {
            println("– ${file.name} did not exist, skipping")
        }
    }

    println("\n=== Reset complete — ready for a fresh ingest ===")
}
