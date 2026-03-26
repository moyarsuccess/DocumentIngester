package ca.flutra.rag.eval

import java.io.File

/**
 * A single ground-truth test case generated from a source document.
 *
 * @param question        The question the RAG pipeline should be able to answer.
 * @param expectedAnswer  The correct answer derived from the source text.
 * @param sourceDocument  Absolute path of the document this Q&A was generated from.
 *                        Used as the relevance signal for MRR / NDCG — a retrieved
 *                        chunk is considered relevant iff its "source" metadata value
 *                        contains this file name.
 */
data class EvalCase(
    val question: String,
    val expectedAnswer: String,
    val sourceDocument: String,
) {
    /** Just the filename portion, used for loose source matching. */
    val sourceFileName: String get() = File(sourceDocument).name
}

/**
 * The outcome of running one [EvalCase] through the full RAG pipeline.
 *
 * @param retrievedSources  Ordered list of "source" metadata values from the retrieved
 *                          chunks, ranked from most to least similar.  Used to compute
 *                          MRR, NDCG, and Recall.
 * @param faithfulnessScore 0.0–1.0: is the answer grounded in the retrieved context?
 * @param correctnessScore  0.0–1.0: does the answer match the expected answer?
 */
data class EvalResult(
    val case: EvalCase,
    val actualAnswer: String,
    val retrievedSources: List<String>,
    val faithfulnessScore: Double,
    val correctnessScore: Double,
) {
    /** True when correctness meets the pass threshold (≥ 0.7). */
    val passed: Boolean get() = correctnessScore >= 0.7

    /** Rank (1-based) of the first retrieved chunk from the correct source, or null. */
    val firstRelevantRank: Int? get() =
        retrievedSources.indexOfFirst { it.contains(case.sourceFileName) }
            .takeIf { it >= 0 }
            ?.let { it + 1 }
}

/**
 * Aggregate metrics computed over all [EvalResult]s.
 */
data class EvalMetrics(
    val totalCases: Int,
    val mrr: Double,
    val ndcgAt10: Double,
    val recallAt10: Double,
    val avgFaithfulness: Double,
    val avgCorrectness: Double,
    val passRate: Double,
)
