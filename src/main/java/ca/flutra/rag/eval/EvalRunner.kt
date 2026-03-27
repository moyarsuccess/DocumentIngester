package ca.flutra.rag.eval

import ca.flutra.rag.common.Config
import ca.flutra.rag.common.ModelProvider
import ca.flutra.rag.embedding.EmbeddingStoreProvider
import ca.flutra.rag.embedding.MxbaiQueryEmbeddingModel
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.SystemMessage

/**
 * Runs each [EvalCase] through the full RAG pipeline and returns an [EvalResult]
 * capturing both retrieval quality and answer quality signals.
 *
 * Two separate calls are made per case:
 *
 * 1. **Retriever-only call** — fetches the ranked list of chunks directly from
 *    [EmbeddingStoreContentRetriever] so we can compute MRR, NDCG, and Recall
 *    from the raw source metadata without the LLM touching the ranking.
 *
 * 2. **Full pipeline call** — sends the question through [EvalAssistant] (same
 *    AiServices setup as Answer.kt) to get the actual answer and the context
 *    string used to feed [LlmJudge].
 */
object EvalRunner {

    // ── Shared infrastructure ─────────────────────────────────────────────────

    private val queryEmbeddingModel by lazy {
        MxbaiQueryEmbeddingModel(ModelProvider.embeddingModel)
    }

    private val retriever by lazy {
        EmbeddingStoreContentRetriever.builder()
            .embeddingModel(queryEmbeddingModel)
            .embeddingStore(EmbeddingStoreProvider.store)
            .maxResults(Config.FINAL_K)
            .minScore(Config.RETRIEVAL_MIN_SCORE)
            .build()
    }

    private val assistant by lazy {
        AiServices.builder(EvalAssistant::class.java)
            .chatModel(ModelProvider.chatModel)
            .contentRetriever(retriever)
            .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
            .build()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs all [cases] sequentially and returns one [EvalResult] per case.
     * Progress is printed to stdout.
     */
    fun run(cases: List<EvalCase>): List<EvalResult> {
        println("Running eval on ${cases.size} case(s)...\n")
        return cases.mapIndexed { i, case ->
            print("  [${i + 1}/${cases.size}] ${case.question.take(70)}... ")
            val result = runCase(case)
            println("correctness=${"%.2f".format(result.correctnessScore)}  faithfulness=${"%.2f".format(result.faithfulnessScore)}")
            result
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun runCase(case: EvalCase): EvalResult {
        // 1. Retrieval-only: get ranked sources for MRR / NDCG / Recall.
        val contents = retriever.retrieve(
            dev.langchain4j.rag.query.Query.from(case.question)
        )
        val retrievedSources = contents.map { content ->
            content.textSegment().metadata().getString("source") ?: ""
        }

        // 2. Full pipeline: generate the actual answer + capture context.
        val actualAnswer = assistant.answer(case.question)

        val retrievedContext = contents.joinToString("\n\n") {
            it.textSegment().text()
        }

        // 3. Judge: score faithfulness and correctness independently.
        val faithfulness = LlmJudge.scoreFaithfulness(
            question = case.question,
            retrievedContext = retrievedContext,
            actualAnswer = actualAnswer,
        )
        val correctness = LlmJudge.scoreCorrectness(
            question = case.question,
            expectedAnswer = case.expectedAnswer,
            actualAnswer = actualAnswer,
        )

        return EvalResult(
            case = case,
            actualAnswer = actualAnswer,
            retrievedSources = retrievedSources,
            faithfulnessScore = faithfulness,
            correctnessScore = correctness,
        )
    }

    // ── Assistant interface (mirrors Answer.kt) ─────────────────────────────────

    private interface EvalAssistant {
        @SystemMessage(
            """You are a knowledgeable, friendly assistant.
Base your answer on the provided context. If the context does not contain
the answer, say so honestly — do not make things up."""
        )
        fun answer(userMessage: String): String
    }
}
