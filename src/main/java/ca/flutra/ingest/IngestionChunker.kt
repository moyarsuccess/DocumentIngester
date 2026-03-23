package ca.flutra.ingest

import com.google.gson.Gson
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ResponseFormat
import dev.langchain4j.model.chat.request.ResponseFormatType
import dev.langchain4j.model.chat.request.json.JsonArraySchema
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonSchema
import dev.langchain4j.model.chat.request.json.JsonStringSchema
import dev.langchain4j.model.ollama.OllamaChatModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.time.Duration

object IngestionChunker {

    private val chunkSchema = JsonObjectSchema.builder()
        .addProperty(
            "chunks", JsonArraySchema.builder()
                .items(
                    JsonObjectSchema.builder()
                        .addProperty(
                            "headline", JsonStringSchema.builder()
                                .description("Short title describing the chunk's topic")
                                .build()
                        )
                        .addProperty(
                            "summary", JsonStringSchema.builder()
                                .description("Concise summary of the chunk's content")
                                .build()
                        )
                        .addProperty(
                            "original", JsonStringSchema.builder()
                                .description("Full original text or description extracted from the document")
                                .build()
                        )
                        .required(listOf("headline", "summary", "original"))
                        .additionalProperties(false)
                        .build()
                )
                .build()
        )
        .required(listOf("chunks"))
        .additionalProperties(false)
        .build()

    private val responseFormat = ResponseFormat.builder()
        .type(ResponseFormatType.JSON)
        .jsonSchema(
            JsonSchema.builder()
                .name("Chunks")
                .rootElement(chunkSchema)
                .build()
        )
        .build()

    private val chatModel: OllamaChatModel by lazy {
        OllamaChatModel.OllamaChatModelBuilder()
            .baseUrl("http://localhost:11434/")
            .modelName("qwen2.5:7b")
            .timeout(Duration.ofMinutes(5))
            .build()
            ?: throw IllegalStateException("Failed to provide the model!")
    }

    private const val AVERAGE_CHUNK_SIZE = 500
    private const val AVERAGE_OVERLAP_PERCENT = 25

    suspend fun chunk(ingestionModel: IngestionModel): Deferred<IngestionChunks> = withContext(Dispatchers.IO) {
        return@withContext async {
            val userMessage = UserMessage.from(makePrompt(ingestionModel))
            val chatReq = ChatRequest.builder()
                .responseFormat(responseFormat)  // schema-constrained now
                .messages(listOf(userMessage))
                .build()
            val text = chatModel.chat(chatReq).aiMessage().text()
            Gson().fromJson(text, Chunks::class.java).chunks
        }
    }

    private fun makePrompt(ingestionModel: IngestionModel): String {
        val howMany = (ingestionModel.content.length / AVERAGE_CHUNK_SIZE) + 1
        return """
            You take a document and you split the document into overlapping chunks for a KnowledgeBase.
            The document has been retrieved from: ${ingestionModel.sourcePath} 
            
            A chatbot will use these chunks to answer questions about the documents.
            You should divide up the document as you see fit, being sure that the entire document is returned in the chunks - don't leave anything out.
            This document should probably be split into $howMany chunks, but you can have more or less as appropriate.
            There should be overlap between the chunks as appropriate; typically about $AVERAGE_OVERLAP_PERCENT% overlap, so you have the same text in multiple chunks for best retrieval results.
            
            For each chunk, you should provide a headline, a summary, and the original text of the chunk.
            Together your chunks should represent the entire document with overlap.
            
            Here is the document:
            
            ${ingestionModel.content}
            
            Respond with the chunks.
        """.trimIndent()
    }

    private data class Chunks(
        val chunks: IngestionChunks,
    )
}