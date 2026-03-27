package ca.flutra.rag

import ca.flutra.rag.eval.DatasetGenerator
import ca.flutra.rag.eval.DocumentTextStore
import ca.flutra.rag.eval.EvalMetrics
import ca.flutra.rag.eval.EvalResult
import ca.flutra.rag.eval.EvalRunner
import ca.flutra.rag.eval.Metrics

/**
 * Entry point for the RAG evaluation suite.
 *
 * Prerequisites: run Ingest.kt first. Ingestion builds the vector store AND
 * writes ingested-texts.json, which the eval reads to generate Q&A pairs
 * without re-running OCR.
 *
 * Run order:
 *  1. Load document texts from ingested-texts.json (written by Ingest).
 *  2. Load the eval dataset from eval-dataset.json, or auto-generate it if absent.
 *  3. Run every eval case through the full pipeline.
 *  4. Compute MRR, NDCG@10, Recall@10, faithfulness, correctness, pass-rate.
 *  5. Print a detailed per-case breakdown and an aggregate summary.
 *
 * Flags (set via program arguments):
 *  --regenerate   Force re-generation of the eval dataset even if one exists.
 */
fun main(args: Array<String>) {
    val regenerate = "--regenerate" in args

    println("╔══════════════════════════════════════════╗")
    println("║         RAG Evaluation Suite             ║")
    println("╚══════════════════════════════════════════╝\n")

    // 1. Load document texts cached during ingestion — no OCR needed.
    println("── Step 1: Loading ingested document texts ─")
    val documents = DocumentTextStore.load()
    println("Found ${documents.size} document(s).\n")

    if (documents.isEmpty()) {
        println("No ingested documents found. Run Ingest first, then re-run Eval.")
        return
    }

    // 2. Load or generate the eval dataset.
    println("── Step 2: Eval dataset ───────────────────")
    val cases = if (regenerate) {
        DatasetGenerator.regenerate(documents)
    } else {
        DatasetGenerator.loadOrGenerate(documents)
    }
    println("${cases.size} eval case(s) ready.\n")

    if (cases.isEmpty()) {
        println("No eval cases to run.")
        return
    }

    // 3. Run the eval.
    println("── Step 3: Running eval ───────────────────")
    val results = EvalRunner.run(cases)

    // 4. Compute aggregate metrics.
    val metrics = Metrics.compute(results, k = 10)

    // 5. Print report.
    printReport(results, metrics)
}


// ── Report formatting ──────────────────────────────────────────────────────────

private fun printReport(results: List<EvalResult>, metrics: EvalMetrics) {
    val w = 80

    println("\n${"═".repeat(w)}")
    println("  PER-CASE BREAKDOWN")
    println("═".repeat(w))

    results.forEachIndexed { i, r ->
        val rank = r.firstRelevantRank?.toString() ?: "—"
        val pass = if (r.passed) "✓ PASS" else "✗ FAIL"
        println("\n  [${i + 1}] $pass")
        println("  Q : ${r.case.question.take(75)}")
        println("  Expected : ${r.case.expectedAnswer.take(75)}")
        println("  Actual   : ${r.actualAnswer.take(75)}")
        println(
            "  Scores   : correctness=${"%.2f".format(r.correctnessScore)}" +
            "  faithfulness=${"%.2f".format(r.faithfulnessScore)}" +
            "  first-relevant-rank=$rank"
        )
        println("  Source   : ${r.case.sourceFileName}")
    }

    println("\n${"═".repeat(w)}")
    println("  AGGREGATE METRICS  (${metrics.totalCases} cases)")
    println("═".repeat(w))
    println()

    fun row(label: String, value: Double, description: String) {
        val bar = buildBar(value)
        println("  %-20s %s  %.3f   %s".format(label, bar, value, description))
    }

    row("MRR",            metrics.mrr,             "Mean rank of correct source (1.0 = always first)")
    row("NDCG@10",        metrics.ndcgAt10,         "Ranking quality of relevant chunks in top 10")
    row("Recall@10",      metrics.recallAt10,       "Correct source found in top 10 results")
    row("Faithfulness",   metrics.avgFaithfulness,  "Answer grounded in retrieved context")
    row("Correctness",    metrics.avgCorrectness,   "Answer matches expected answer")
    row("Pass rate",      metrics.passRate,         "Fraction with correctness ≥ 0.70")

    println()
    println("  Overall: ${"%.0f".format(metrics.passRate * 100)}% of cases passed.\n")
}

/** Renders a simple ASCII progress bar for a 0.0–1.0 value. */
private fun buildBar(value: Double, width: Int = 20): String {
    val filled = (value * width).toInt().coerceIn(0, width)
    return "[" + "█".repeat(filled) + "░".repeat(width - filled) + "]"
}
