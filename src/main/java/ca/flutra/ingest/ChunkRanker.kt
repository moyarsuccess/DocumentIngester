package ca.flutra.ingest

import com.google.gson.Gson
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ResponseFormat
import dev.langchain4j.model.chat.request.ResponseFormatType
import dev.langchain4j.model.chat.request.json.JsonArraySchema
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonSchema
import dev.langchain4j.model.ollama.OllamaChatModel

object ChunkRanker {

    private val chatModel: OllamaChatModel by lazy {
        OllamaChatModel.builder()
            .baseUrl("http://localhost:11434/")
            .modelName("llama3.2")
            .temperature(0.0)
            .build()
    }

    private val responseFormat: ResponseFormat by lazy {
        ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(
                JsonSchema.builder()
                    .name("RankOrder")
                    .rootElement(
                        JsonObjectSchema.builder()
                            .addProperty(
                                "order", JsonArraySchema.builder()
                                    .items(JsonIntegerSchema.builder().build())
                                    .description("Chunk IDs ordered from most to least relevant")
                                    .build()
                            )
                            .required(listOf("order"))
                            .additionalProperties(false)
                            .build()
                    )
                    .build()
            )
            .build()
    }

    fun rerank(question: String, chunks: List<TextSegment?>): List<String> {
        val userPrompt = buildString {
            appendLine("The user has asked the following question:\n\n$question\n")
            appendLine("Order all the chunks by relevance, from most to least relevant. Include all chunk ids.\n")
            appendLine("Here are the chunks:\n")
            chunks.filterNotNull().forEachIndexed { index, chunk ->
                appendLine("# CHUNK ID: ${index + 1}:\n\n$chunk\n")
            }
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

        val reply = chatModel.chat(request).aiMessage().text()
        val rankOrder = Gson().fromJson(reply, RankOrder::class.java)
        return rankOrder.order.map { chunks[it - 1]?.text() ?: "" }
    }

    private val systemPrompt = """
        You are a document re-ranker.
        You must rank order the provided chunks by relevance to the question, with the most relevant chunk first.
        Reply only with a JSON object with an "order" field containing the list of ranked chunk ids.
        Include all chunk ids you are provided with, reranked.
    """.trimIndent()

    data class RankOrder(val order: List<Int> = emptyList())
}