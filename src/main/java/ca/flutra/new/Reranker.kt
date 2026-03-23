package ca.flutra.new

import com.google.gson.Gson
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ResponseFormat
import dev.langchain4j.model.chat.request.ResponseFormatType
import dev.langchain4j.model.chat.request.json.JsonArraySchema
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonSchema

object Reranker {

    private val gson = Gson()

    private val responseFormat: ResponseFormat by lazy {
        val schema = JsonObjectSchema.builder()
            .addProperty(
                "order", JsonArraySchema.builder()
                    .items(JsonIntegerSchema.builder().build())
                    .description("Chunk IDs ordered from most to least relevant")
                    .build()
            )
            .required(listOf("order"))
            .additionalProperties(false)
            .build()

        ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(
                JsonSchema.builder()
                    .name("RankOrder")
                    .rootElement(schema)
                    .build()
            )
            .build()
    }

    private val systemPrompt = """
        You are a document re-ranker.
        You are provided with a question and a list of relevant chunks of text from a query of a knowledge base.
        The chunks are provided in the order they were retrieved; this should be approximately ordered by relevance, but you may be able to improve on that.
        You must rank order the provided chunks by relevance to the question, with the most relevant chunk first.
        Reply only with the list of ranked chunk ids, nothing else. Include all the chunk ids you are provided with, reranked.
    """.trimIndent()

    /**
     * Reranks retrieved chunks by relevance to the question.
     * Equivalent of Python's rerank().
     */
    fun rerank(question: String, chunks: List<Result>): List<Result> {
        val userPrompt = buildString {
            appendLine("The user has asked the following question:\n\n$question\n")
            appendLine("Order all the chunks by relevance, from most to least relevant. Include all chunk ids.\n")
            appendLine("Here are the chunks:\n")
            chunks.forEachIndexed { index, chunk ->
                appendLine("# CHUNK ID: ${index + 1}:\n\n${chunk.pageContent}\n")
            }
            appendLine("Reply only with a JSON object like: {\"order\": [2, 1, 3]}")
        }

        val request = ChatRequest.builder()
            .messages(
                listOf(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt),
                )
            )
            .responseFormat(responseFormat)
            .build()

        val reply = ModelProvider.chatModel.chat(request).aiMessage().text()
        val order = gson.fromJson(reply, RankOrder::class.java).order
        return order.map { chunks[it - 1] }
    }
}
