package ca.flutra.rag

import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.SystemMessage

interface RagAssistant {
    @SystemMessage(
        """You are a knowledgeable, friendly assistant.
Your answers will be evaluated for accuracy, relevance, and completeness.
Base your answer on the provided context. If the context does not contain
the answer, say so honestly — do not make things up."""
    )
    fun answer(userMessage: String): String
}

fun main() {
    println("=== RAG Assistant Ready ===")

    // Retriever uses the query-prefixed embedding model so mxbai-embed-large
    // aligns query vectors correctly against the stored passage vectors.
    val retriever = EmbeddingStoreContentRetriever.builder()
        .embeddingModel(MxbaiQueryEmbeddingModel(ModelProvider.embeddingModel))
        .embeddingStore(EmbeddingStoreProvider.store)
        .maxResults(Config.FINAL_K)
        .minScore(Config.RETRIEVAL_MIN_SCORE)
        .build()

    val assistant = AiServices.builder(RagAssistant::class.java)
        .chatModel(ModelProvider.chatModel)
        .contentRetriever(retriever)
        .chatMemory(MessageWindowChatMemory.withMaxMessages(200))
        .build()

    while (true) {
        print("\nAsk a question (or 'quit'): ")
        val question = readlnOrNull()?.trim() ?: break
        if (question.lowercase() == "quit") break

        println("\n--- Answer ---")
        println(assistant.answer(question))
    }
}
