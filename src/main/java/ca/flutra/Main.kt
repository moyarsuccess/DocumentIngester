package ca.flutra

import ca.flutra.answer.QueryRewriter
import ca.flutra.ingest.ChunkRanker
import ca.flutra.ingest.EmbeddingStore
import ca.flutra.ingest.Ingester
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.ollama.OllamaChatModel
import kotlinx.coroutines.runBlocking

private val chatModel: OllamaChatModel by lazy {
    OllamaChatModel.OllamaChatModelBuilder()
        .baseUrl("http://localhost:11434/")
        .modelName("gpt-oss")
        .build()
        ?: throw IllegalStateException("Failed to provide the model!")
}

fun main(): Unit = runBlocking {
    Ingester.startToIngest()
    val q1 = "What is the total amount of tax for 1 Hammill Heights property?"
    val q2 = "What is the left amount of principal mortgage value of 1 Hammill Heights property?"
    val q3 = "What is the application no of Sedighe Heshmati Rad IRCC biometric letter?"
    println(ask(q1))
    println(ask(q2))
    println(ask(q3))
}

private fun ask(question: String): String {
    val rewrittenQuestion = QueryRewriter.rewriteQuery(question)
    val originalContext = EmbeddingStore.provideChunks(20, question)
    val rewrittenContext = EmbeddingStore.provideChunks(20, rewrittenQuestion)
    val mergedChunks = originalContext + rewrittenContext
    val rerankedChunks = ChunkRanker.rerank(question, mergedChunks).take(10)
    val context = rerankedChunks.joinToString("\n\n") { it }
    println(context)
    val systemPrompt = """
        Your answer will be evaluated for accuracy, relevance and completeness, so make sure it only answers the question and fully answers it.
        If you don't know the answer, say so.
        For context, here are specific extracts from the Knowledge Base that might be directly relevant to the user's question:
        $context
        
        With this context, please answer the user's question. Be accurate, relevant and complete.
    """.trimIndent()

    val userMessage = UserMessage.from(question)
    val systemMessage = UserMessage.from(systemPrompt)
    return chatModel.chat(userMessage, systemMessage)?.aiMessage()?.text() ?: ""
}