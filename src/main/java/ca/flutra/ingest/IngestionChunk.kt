package ca.flutra.ingest

data class IngestionChunk(
    val headline: String,
    val summery: String,
    val original: String,
) {

    fun asContent(): String {
        return "$headline \n\n $summery \n\n $original"
    }
}

typealias IngestionChunks = List<IngestionChunk>