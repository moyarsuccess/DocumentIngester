package ca.flutra.rag.eval

import ca.flutra.rag.common.Document
import ca.flutra.rag.common.ModelProvider
import ca.flutra.rag.eval.DatasetGenerator.DATASET_FILE
import ca.flutra.rag.eval.DatasetGenerator.loadOrGenerate
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ResponseFormat
import dev.langchain4j.model.chat.request.ResponseFormatType
import dev.langchain4j.model.chat.request.json.JsonArraySchema
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonSchema
import dev.langchain4j.model.chat.request.json.JsonStringSchema
import java.io.File

/**
 * Generates an evaluation dataset from raw documents by asking the LLM to
 * produce question-answer pairs that are answerable from the document text.
 *
 * The dataset is saved to [DATASET_FILE] after generation so it can be reused
 * across eval runs without re-generating (generation is expensive — one LLM
 * call per document chunk).
 *
 * Call [loadOrGenerate] to get the dataset: it loads from disk if the file
 * exists, otherwise generates fresh and saves.
 */
internal object DatasetGenerator {

    private val DATASET_FILE = File("eval-dataset.json")
    private val gson = Gson()

    // Chunk size (chars) used when splitting documents for Q&A generation.
    // Smaller than the ingestion chunk so the LLM has a focused passage to
    // reason about and the Q&A is clearly answerable from that single chunk.
    private const val GENERATION_CHUNK_CHARS = 2_000

    // Number of Q&A pairs to request per document chunk.
    private const val QA_PER_CHUNK = 3

    private val responseFormat: ResponseFormat by lazy {
        val qaSchema = JsonObjectSchema.builder()
            .addProperty(
                "question", JsonStringSchema.builder()
                    .description("A specific, answerable question about the chunk text").build()
            )
            .addProperty(
                "answer", JsonStringSchema.builder()
                    .description("The correct, concise answer drawn directly from the chunk text").build()
            )
            .required(listOf("question", "answer"))
            .additionalProperties(false)
            .build()

        val rootSchema = JsonObjectSchema.builder()
            .addProperty("pairs", JsonArraySchema.builder().items(qaSchema).build())
            .required(listOf("pairs"))
            .additionalProperties(false)
            .build()

        ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder().name("QAPairs").rootElement(rootSchema).build())
            .build()
    }

    /** Loads the dataset from disk, or generates and saves it if absent. */
    fun loadOrGenerate(documents: List<Document>): List<EvalCase> {
        if (DATASET_FILE.exists()) {
            println("Loading eval dataset from ${DATASET_FILE.absolutePath}")
            val type = object : TypeToken<List<EvalCase>>() {}.type
            return gson.fromJson(DATASET_FILE.readText(), type)
        }
        println("No eval dataset found — generating from ${documents.size} document(s)...")
        val cases = generate(documents)
        save(cases)
        return cases
    }

    /** Force-regenerates the dataset even if one already exists. */
    fun regenerate(documents: List<Document>): List<EvalCase> {
        println("Regenerating eval dataset from ${documents.size} document(s)...")
        val cases = generate(documents)
        save(cases)
        return cases
    }

    private fun generate(documents: List<Document>): List<EvalCase> =
        documents.flatMap { doc ->
            doc.text
                .chunked(GENERATION_CHUNK_CHARS)
                .flatMap { chunk -> generatePairs(chunk, doc.source) }
        }.also { println("Generated ${it.size} eval cases total.") }

    private fun generatePairs(chunk: String, sourceDocument: String): List<EvalCase> {
        if (chunk.isBlank()) return emptyList()
        return try {
            val prompt = """
                You are building an evaluation dataset for a RAG (Retrieval-Augmented Generation) system.

                Read the following document excerpt and generate exactly $QA_PER_CHUNK question-answer pairs.

                Rules:
                - Each question must be specific and answerable ONLY from the text below — no outside knowledge.
                - Each answer must be concise (1-3 sentences) and drawn verbatim or near-verbatim from the text.
                - Do NOT generate vague or generic questions like "What is this document about?".
                - Vary the question types: some factual, some about specific numbers/dates, some about processes.

                Document excerpt:
                $chunk
            """.trimIndent()

            val request = ChatRequest.builder()
                .messages(listOf(UserMessage.from(prompt)))
                .responseFormat(responseFormat)
                .build()

            val reply = ModelProvider.chatModel.chat(request).aiMessage().text()
            val parsed = gson.fromJson(reply, QaPairsResponse::class.java)

            parsed.pairs.map { pair ->
                EvalCase(
                    question = pair.question,
                    expectedAnswer = pair.answer,
                    sourceDocument = sourceDocument,
                )
            }
        } catch (e: Exception) {
            System.err.println("Failed to generate Q&A for chunk from $sourceDocument: ${e.message}")
            emptyList()
        }
    }

    private fun save(cases: List<EvalCase>) {
        DATASET_FILE.writeText(gson.toJson(cases))
        println("Saved ${cases.size} eval cases to ${DATASET_FILE.absolutePath}")
    }

    // ── Internal response models ───────────────────────────────────────────────

    private data class QaPairsResponse(val pairs: List<QaPair>)
    private data class QaPair(val question: String, val answer: String)
}
