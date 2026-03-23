package ca.flutra.new

import java.nio.file.Path
import java.nio.file.Paths

object Config {

    // ── Paths ─────────────────────────────────────────────────────────────────
    val knowledgeBasePath: Path = Paths.get("knowledge-base")

    // ── Models ────────────────────────────────────────────────────────────────
    const val CHAT_MODEL_NAME = "qwen2.5:14b"
    const val EMBEDDING_MODEL_NAME = "mxbai-embed-large"
    const val OLLAMA_BASE_URL = "http://localhost:11434"

    // ── Qdrant ────────────────────────────────────────────────────────────────
    const val QDRANT_HOST = "localhost"
    const val QDRANT_PORT = 6334
    const val COLLECTION_NAME = "docs"

    // ── Chunking ──────────────────────────────────────────────────────────────
    const val AVERAGE_CHUNK_SIZE = 100
    const val AVERAGE_OVERLAP_PERCENT = 25

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
