package ca.flutra.new

import com.google.gson.Gson
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ResponseFormat
import dev.langchain4j.model.chat.request.ResponseFormatType
import dev.langchain4j.model.chat.request.json.JsonArraySchema
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonSchema
import dev.langchain4j.model.chat.request.json.JsonStringSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

object Chunker {

    private val gson = Gson()

    private val responseFormat: ResponseFormat by lazy {
        val chunkSchema = JsonObjectSchema.builder()
            .addProperty(
                "headline", JsonStringSchema.builder()
                    .description("A brief heading for this chunk, most likely to be surfaced in a query")
                    .build()
            )
            .addProperty(
                "summary", JsonStringSchema.builder()
                    .description("A few sentences summarizing the content of this chunk")
                    .build()
            )
            .addProperty(
                "original_text", JsonStringSchema.builder()
                    .description("The original text of this chunk, exactly as-is")
                    .build()
            )
            .required(listOf("headline", "summary", "original_text"))
            .additionalProperties(false)
            .build()

        val rootSchema = JsonObjectSchema.builder()
            .addProperty(
                "chunks", JsonArraySchema.builder()
                    .items(chunkSchema)
                    .build()
            )
            .required(listOf("chunks"))
            .additionalProperties(false)
            .build()

        ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(
                JsonSchema.builder()
                    .name("Chunks")
                    .rootElement(rootSchema)
                    .build()
            )
            .build()
    }

    /**
     * Processes a single document into chunks via the LLM.
     * Equivalent of Python's process_document().
     */
    fun processDocument(document: Document): List<Result> {
        val request = ChatRequest.builder()
            .messages(listOf(UserMessage.from(makePrompt(document))))
            .responseFormat(responseFormat)
            .build()

        val reply = ModelProvider.chatModel.chat(request).aiMessage().text()
        return gson.fromJson(reply, Chunks::class.java).chunks.map { it.asResult(document) }
    }

    /**
     * Processes all documents in parallel using coroutines.
     * Equivalent of Python's create_chunks() with multiprocessing Pool.
     */
    suspend fun createChunks(documents: List<Document>): List<Result> =
        withContext(Dispatchers.IO) {
            documents
                .map { doc -> async { processDocument(doc) } }
                .awaitAll()
                .flatten()
        }

    private fun makePrompt(document: Document): String {
        val howMany = (document.text.length / Config.AVERAGE_CHUNK_SIZE) + 1
        return """
            You take a document and you split the document into overlapping chunks for a KnowledgeBase.
            The document is of type: ${document.type}
            The document has been retrieved from: ${document.source}

            A chatbot will use these chunks to answer questions about the documents.
            You should divide up the document as you see fit, being sure that the entire document is returned in the chunks - don't leave anything out.
            This document should probably be split into at least $howMany chunks, but you can have more or less as appropriate.
            There should be overlap between the chunks as appropriate; typically about ${Config.AVERAGE_OVERLAP_PERCENT}% overlap, so you have the same text in multiple chunks for best retrieval results.

            For each chunk, provide a headline, a summary, and the original text of the chunk.
            Together your chunks should represent the entire document with overlap.

            Here is the document:

            ${document.text}

            Respond with the chunks.
        """.trimIndent()
    }
}
