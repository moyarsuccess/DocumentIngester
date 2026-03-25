package ca.flutra.rag

import java.nio.file.Path
import java.nio.file.Paths

object Config {

    // ── Paths ─────────────────────────────────────────────────────────────────
    // Override with env var KNOWLEDGE_BASE_PATH for production deployments.
    val knowledgeBasePath: Path =
        Paths.get(System.getenv("KNOWLEDGE_BASE_PATH") ?: "knowledge-base").toAbsolutePath()

    // ── Models ────────────────────────────────────────────────────────────────
    const val CHAT_MODEL_NAME = "llama3.2"
    const val EMBEDDING_MODEL_NAME = "mxbai-embed-large"
    const val OCR_MODEL_NAME = "qwen2.5vl:7b"
    const val OLLAMA_BASE_URL = "http://localhost:11434"

    // ── OCR ───────────────────────────────────────────────────────────────────
    // Set to "google" to use Google Cloud Vision instead of the local Ollama model.
    const val OCR_PROVIDER = "google"  // "ollama" | "google"
//    const val OCR_PROVIDER = "ollama"  // "ollama" | "google"

    // ── Qdrant ────────────────────────────────────────────────────────────────
    const val QDRANT_HOST = "localhost"
    const val QDRANT_PORT = 6334
    const val COLLECTION_NAME = "docs"

    // Must match the output dimension of EMBEDDING_MODEL_NAME.
    // mxbai-embed-large → 1024, nomic-embed-text → 768, all-minilm → 384
    const val EMBEDDING_DIMENSIONS = 1024

    // ── Chunking ──────────────────────────────────────────────────────────────
    const val AVERAGE_CHUNK_SIZE = 100
    const val AVERAGE_OVERLAP_PERCENT = 25

    // Max number of documents chunked concurrently (each fires an LLM call).
    const val INGESTION_PARALLELISM = 4

    // ── Retrieval ─────────────────────────────────────────────────────────────
    const val RETRIEVAL_K = 20
    const val FINAL_K = 10

    // ── Prompts ───────────────────────────────────────────────────────────────
    val SYSTEM_PROMPT = """
        You are a knowledgeable, friendly assistant.
        Your answer will be evaluated for accuracy, relevance and completeness.
        If you don't know the answer, say so.
        For context, here are specific extracts from the Knowledge Base that might be directly relevant to the user's question:
        {context}

        With this context, please answer the user's question. Be accurate, relevant and complete.
    """.trimIndent()
}
