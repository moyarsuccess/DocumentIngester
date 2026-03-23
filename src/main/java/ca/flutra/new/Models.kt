package ca.flutra.new

import com.google.gson.annotations.SerializedName

// ── Ingestion models ──────────────────────────────────────────────────────────

data class Document(
    val type: String,
    val source: String,
    val text: String,
)

data class Chunk(
    val headline: String,
    val summary: String,
    @SerializedName("original_text")
    val originalText: String,
) {
    fun asResult(document: Document) = Result(
        pageContent = "$headline\n\n$summary\n\n$originalText",
        metadata = mapOf("source" to document.source, "type" to document.type),
    )
}

data class Chunks(val chunks: List<Chunk>)

// ── RAG models ────────────────────────────────────────────────────────────────

data class Result(
    val pageContent: String,
    val metadata: Map<String, String>,
)

data class RankOrder(
    val order: List<Int> = emptyList(),
)
