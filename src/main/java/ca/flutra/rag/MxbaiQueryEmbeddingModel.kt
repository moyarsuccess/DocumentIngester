package ca.flutra.rag

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response

class MxbaiQueryEmbeddingModel(
    private val delegate: EmbeddingModel,
) : EmbeddingModel by delegate {

    override fun embed(text: String): Response<Embedding> =
        delegate.embed("${Config.EMBEDDING_QUERY_PREFIX}$text")
}
