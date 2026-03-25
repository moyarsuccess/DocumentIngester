package ca.flutra.rag.common

internal data class Document(
    val type: String,
    val source: String,
    val text: String,
)
