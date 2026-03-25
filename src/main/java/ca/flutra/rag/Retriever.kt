package ca.flutra.rag

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.store.embedding.EmbeddingSearchRequest

object Retriever {

    /**
     * Rewrites a user question to be more specific for KB retrieval.
     * Equivalent of Python's rewrite_query().
     */
    fun rewriteQuery(question: String, history: List<Map<String, String>> = emptyList()): String {
        val prompt = """
            You are about to look up information in a Knowledge Base to answer a user's question.

            This is the conversation history so far:
            $history

            And this is the user's current question:
            $question

            Respond only with a short, refined question most likely to surface relevant content.
            IMPORTANT: Respond ONLY with the precise knowledgebase query, nothing else.
        """.trimIndent()

        return ModelProvider.chatModel.chat(prompt)
    }

    /**
     * Retrieves top-K chunks for a given question string.
     * Equivalent of Python's fetch_context_unranked().
     */
    fun fetchUnranked(question: String): List<Result> {
        val embedding = ModelProvider.embeddingModel.embed(question).content()

        val request = EmbeddingSearchRequest.builder()
            .queryEmbedding(embedding)
            .maxResults(Config.RETRIEVAL_K)
            .build()

        return EmbeddingStoreProvider.store
            .search(request)
            .matches()
            .map { match ->
                val segment: TextSegment = match.embedded()
                Result(
                    pageContent = segment.text(),
                    metadata = segment.metadata().toMap()
                        .mapValues { it.value.toString() },
                )
            }
    }

    /**
     * Merges two result lists, deduplicating by page content.
     * Equivalent of Python's merge_chunks().
     */
    fun mergeChunks(primary: List<Result>, secondary: List<Result>): List<Result> {
        val seen = primary.map { it.pageContent }.toHashSet()
        return primary + secondary.filter { it.pageContent !in seen }
    }

    /**
     * Full retrieval pipeline: rewrite query → dual fetch → merge.
     * Equivalent of Python's fetch_context() minus reranking.
     */
    fun fetchContext(question: String, history: List<Map<String, String>> = emptyList()): List<Result> {
        val rewrittenQuestion = rewriteQuery(question, history)
        val chunks1 = fetchUnranked(question)
        val chunks2 = fetchUnranked(rewrittenQuestion)
        return mergeChunks(chunks1, chunks2)
    }
}
