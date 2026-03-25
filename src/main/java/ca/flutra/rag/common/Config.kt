package ca.flutra.rag.common

import java.nio.file.Path
import java.nio.file.Paths

internal object Config {

    val knowledgeBasePath: Path = Paths
        .get(System.getenv("KNOWLEDGE_BASE_PATH") ?: "knowledge-base")
        .toAbsolutePath()
    const val CHAT_MODEL_NAME = "llama3.2"
    const val EMBEDDING_MODEL_NAME = "mxbai-embed-large"
    const val OCR_MODEL_NAME = "qwen2.5vl:7b"
    const val OLLAMA_BASE_URL = "http://localhost:11434"
    val OCR_PROVIDER = OcrProviderType.TESSERACT
    const val TESSERACT_DATA_PATH = "C:\\Program Files\\Tesseract-OCR\\tessdata"
    const val TESSERACT_LANGUAGE = "eng"
    const val TESSERACT_PAGE_SEG_MODE = 3
    const val QDRANT_HOST = "localhost"
    const val QDRANT_PORT = 6334
    const val COLLECTION_NAME = "docs"
    const val EMBEDDING_DIMENSIONS = 1024
    const val CHUNK_SIZE_TOKENS = 500
    const val CHUNK_OVERLAP_TOKENS = 50
    const val FINAL_K = 10
    const val RETRIEVAL_MIN_SCORE = 0.35
    const val EMBEDDING_QUERY_PREFIX = "Represent this sentence for searching relevant passages: "

    enum class OcrProviderType {
        GOOGLE,
        OLLAMA,
        TESSERACT,
    }
}
