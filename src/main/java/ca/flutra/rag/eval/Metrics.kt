package ca.flutra.rag.eval

import kotlin.math.log2

/**
 * Pure retrieval and answer quality metrics computed over a list of [EvalResult]s.
 *
 * Retrieval metrics (MRR, NDCG, Recall) treat a retrieved chunk as *relevant*
 * when its "source" metadata value contains the filename of the [EvalCase.sourceDocument].
 * This is a standard binary-relevance assumption: the document that was used to
 * generate the question is the ground-truth relevant document.
 */
object Metrics {

    // ── Retrieval metrics ──────────────────────────────────────────────────────

    /**
     * Mean Reciprocal Rank (MRR).
     *
     * For each query, computes 1 / rank_of_first_relevant_chunk.
     * If no relevant chunk appears, the score for that query is 0.
     * MRR = mean over all queries.
     *
     * Interpretation: 1.0 means the correct source is always ranked first.
     * 0.5 means it is on average ranked second.  0.0 means it is never found.
     */
    fun mrr(results: List<EvalResult>): Double =
        results.map { r ->
            r.firstRelevantRank?.let { 1.0 / it } ?: 0.0
        }.average()

    /**
     * Normalized Discounted Cumulative Gain at K (NDCG@K).
     *
     * Uses binary relevance (0 or 1) for each retrieved chunk.
     * DCG@K  = Σ rel_i / log2(i + 2)  for i = 0..K-1
     * IDCG@K = DCG of the ideal (all relevant chunks ranked first)
     * NDCG@K = DCG@K / IDCG@K
     *
     * Interpretation: 1.0 means all relevant chunks appear at the top.
     * Lower values mean relevant chunks are buried or missing.
     */
    fun ndcg(results: List<EvalResult>, k: Int = 10): Double =
        results.map { r -> ndcgForResult(r, k) }.average()

    private fun ndcgForResult(result: EvalResult, k: Int): Double {
        val topK = result.retrievedSources.take(k)
        val relevance = topK.map { src ->
            if (src.contains(result.case.sourceFileName)) 1.0 else 0.0
        }

        val dcg = relevance.mapIndexed { i, rel -> rel / log2(i + 2.0) }.sum()

        // Ideal DCG: if all relevant chunks appeared at the very top.
        val numRelevant = relevance.count { it > 0 }.coerceAtMost(k)
        val idcg = (0 until numRelevant).sumOf { i -> 1.0 / log2(i + 2.0) }

        return if (idcg == 0.0) 0.0 else dcg / idcg
    }

    /**
     * Recall@K — the fraction of queries for which at least one chunk from
     * the correct source document appears within the top-K retrieved results.
     *
     * Interpretation: 1.0 means the right document was always retrieved.
     * 0.0 means it was never retrieved.
     */
    fun recallAtK(results: List<EvalResult>, k: Int = 10): Double =
        results.map { r ->
            val found = r.retrievedSources.take(k).any { it.contains(r.case.sourceFileName) }
            if (found) 1.0 else 0.0
        }.average()

    // ── Answer quality metrics ─────────────────────────────────────────────────

    /** Mean faithfulness score (0.0–1.0) across all results. */
    fun avgFaithfulness(results: List<EvalResult>): Double =
        results.map { it.faithfulnessScore }.average()

    /** Mean correctness score (0.0–1.0) across all results. */
    fun avgCorrectness(results: List<EvalResult>): Double =
        results.map { it.correctnessScore }.average()

    /** Fraction of results where [EvalResult.passed] is true (correctness ≥ 0.7). */
    fun passRate(results: List<EvalResult>): Double =
        results.count { it.passed }.toDouble() / results.size

    // ── Aggregate ─────────────────────────────────────────────────────────────

    /** Computes all metrics in one call. */
    fun compute(results: List<EvalResult>, k: Int = 10): EvalMetrics = EvalMetrics(
        totalCases     = results.size,
        mrr            = mrr(results),
        ndcgAt10       = ndcg(results, k),
        recallAt10     = recallAtK(results, k),
        avgFaithfulness = avgFaithfulness(results),
        avgCorrectness = avgCorrectness(results),
        passRate       = passRate(results),
    )
}
