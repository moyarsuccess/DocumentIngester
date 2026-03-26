package ca.flutra.rag.eval

import ca.flutra.rag.common.ModelProvider
import com.google.gson.Gson
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ResponseFormat
import dev.langchain4j.model.chat.request.ResponseFormatType
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonSchema

/**
 * Uses the LLM as an impartial judge to score two dimensions of answer quality.
 *
 * Both methods return a score in [0.0, 1.0].
 *
 * **Faithfulness** — is every claim in the answer supported by the retrieved
 * context?  A score of 1.0 means the answer contains nothing that is not
 * directly derivable from the provided chunks.  A score of 0.0 means the
 * answer is hallucinated or contradicts the context.
 *
 * **Correctness** — how closely does the actual answer match the expected
 * answer?  Scored semantically, not by exact string match: synonyms and
 * paraphrases are rewarded.  A score of 1.0 means the answers are equivalent;
 * 0.0 means they are unrelated or contradictory.
 */
object LlmJudge {

    private val gson = Gson()

    private val scoreFormat: ResponseFormat by lazy {
        val schema = JsonObjectSchema.builder()
            .addProperty(
                "score", JsonIntegerSchema.builder()
                    .description("Integer score from 0 to 10")
                    .build()
            )
            .required(listOf("score"))
            .additionalProperties(false)
            .build()

        ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder().name("Score").rootElement(schema).build())
            .build()
    }

    /**
     * Scores how faithful the [actualAnswer] is to the [retrievedContext].
     * Returns 0.0–1.0; higher is better.
     */
    fun scoreFaithfulness(
        question: String,
        retrievedContext: String,
        actualAnswer: String,
    ): Double {
        val system = """
            You are a strict faithfulness evaluator for a RAG system.
            Your job is to check whether an answer is fully grounded in the provided context.
            Score 10 if every claim in the answer can be verified from the context.
            Score 0 if the answer contains hallucinations or contradicts the context.
            Use intermediate scores for partial faithfulness.
            Respond only with a JSON object containing a single integer field "score".
        """.trimIndent()

        val user = """
            Question: $question

            Retrieved context:
            $retrievedContext

            Answer to evaluate:
            $actualAnswer

            Score the faithfulness of the answer (0-10):
        """.trimIndent()

        return judge(system, user)
    }

    /**
     * Scores how well [actualAnswer] matches [expectedAnswer] semantically.
     * Returns 0.0–1.0; higher is better.
     */
    fun scoreCorrectness(
        question: String,
        expectedAnswer: String,
        actualAnswer: String,
    ): Double {
        val system = """
            You are a strict correctness evaluator for a RAG system.
            Your job is to compare an actual answer to an expected answer for a given question.
            Score 10 if the actual answer is semantically equivalent to the expected answer.
            Score 0 if the actual answer is wrong, irrelevant, or contradicts the expected answer.
            Award partial credit for partially correct answers.
            Respond only with a JSON object containing a single integer field "score".
        """.trimIndent()

        val user = """
            Question: $question

            Expected answer: $expectedAnswer

            Actual answer: $actualAnswer

            Score the correctness of the actual answer (0-10):
        """.trimIndent()

        return judge(system, user)
    }

    private fun judge(system: String, user: String): Double {
        return try {
            val request = ChatRequest.builder()
                .messages(listOf(SystemMessage.from(system), UserMessage.from(user)))
                .responseFormat(scoreFormat)
                .build()
            val reply = ModelProvider.chatModel.chat(request).aiMessage().text()
            val raw = gson.fromJson(reply, ScoreResponse::class.java).score
            raw.coerceIn(0, 10) / 10.0
        } catch (e: Exception) {
            System.err.println("LlmJudge failed: ${e.message}")
            0.0
        }
    }

    private data class ScoreResponse(val score: Int = 0)
}
