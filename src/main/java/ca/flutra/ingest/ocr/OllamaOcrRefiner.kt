package ca.flutra.ingest.ocr

import ca.flutra.ingest.IngestionModel
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.ollama.OllamaChatModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OllamaOcrRefiner : OcrRefiner {

    private const val MAX_CHARS_WINDOW: Int = 12000
    private const val OVERLAP_CHARS: Int = 200
    private val chatModel: OllamaChatModel by lazy {
        OllamaChatModel.builder()
            .baseUrl("http://localhost:11434/")
            .modelName("llama3.2")
            .temperature(0.0)
            .build()
    }

    override suspend fun refine(documents: List<IngestionModel>): List<IngestionModel> =
        withContext(Dispatchers.IO) {
            documents.map { doc ->
                println("\n🔬 Refining: ${doc.sourcePath}")
                if (doc.content.isBlank()) {
                    println("⚠️  Empty content, skipping.")
                    return@map doc
                }

                try {
                    val refined = refineDocument(doc.content)
                    println("✅ Done")
                    IngestionModel(content = refined, sourcePath = doc.sourcePath)
                } catch (e: Exception) {
                    println("⚠️  Refinement failed (${e.message}), using raw text.")
                    doc
                }
            }
        }

    private fun splitIntoWindows(text: String): List<String> {
        val windows = mutableListOf<String>()
        var start = 0

        while (start < text.length) {
            val end = minOf(start + MAX_CHARS_WINDOW, text.length)
            windows.add(text.substring(start, end))
            if (end == text.length) break
            start = end - OVERLAP_CHARS
        }

        return windows
    }

    private fun refineWindow(text: String): String {
        val response = chatModel.chat(
            SystemMessage.from(
                "You are an expert document restoration assistant. " +
                        "Fix all OCR errors including: garbled characters, broken words, " +
                        "missing or extra spaces, corrupted numbers and amounts, scrambled " +
                        "tables, and inconsistent formatting. " +
                        "Restore logical document structure: headings, paragraphs, tables. " +
                        "Keep ALL original information — do not summarize or remove anything. " +
                        "Return ONLY the cleaned text with no explanation or preamble."
            ),
            UserMessage.from("Clean and restore this raw OCR text:\n\n$text"),
        )

        return response.aiMessage().text().trim()
    }

    private fun refineDocument(text: String): String {
        if (text.length <= MAX_CHARS_WINDOW) {
            println("✨ Refining in single pass...")
            return refineWindow(text)
        }

        val windows = splitIntoWindows(text)
        println("✨ Refining in ${windows.size} windows...")

        return windows.mapIndexed { i, window ->
            println("🔄 Window ${i + 1}/${windows.size}...")
            refineWindow(window)
        }.joinToString("\n\n")
    }
}