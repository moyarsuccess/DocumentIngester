package ca.flutra.rag.embedding

import ca.flutra.rag.common.Config
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response

internal class MxbaiQueryEmbeddingModel(
    private val delegate: EmbeddingModel,
) : EmbeddingModel by delegate {

    override fun embed(text: String): Response<Embedding> =
        delegate.embed("${Config.EMBEDDING_QUERY_PREFIX}$text")
}
