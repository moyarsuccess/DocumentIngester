package ca.flutra.rag

import ca.flutra.rag.ocr.GoogleVisionOcrProvider
import ca.flutra.rag.ocr.OcrProvider
import ca.flutra.rag.ocr.OllamaOcrProvider
import ca.flutra.rag.ocr.TesseractOcrProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name

object DocumentLoader {

    private val ocrProvider: OcrProvider = when (Config.OCR_PROVIDER) {
        Config.OcrProviderType.GOOGLE -> GoogleVisionOcrProvider
        Config.OcrProviderType.TESSERACT -> TesseractOcrProvider
        Config.OcrProviderType.OLLAMA -> OllamaOcrProvider
    }

    /**
     * Loads all PDFs from the knowledge-base directory, skipping any whose
     * absolute path is already present in [alreadyIngested].
     */
    suspend fun load(alreadyIngested: Set<String> = emptySet()): List<Document> =
        withContext(Dispatchers.IO) {
            val root = Config.knowledgeBasePath
            require(Files.exists(root)) { "Knowledge base path not found: $root" }
            Files.walk(root)
                .filter { !it.isDirectory() && it.extension == "pdf" }
                .filter { it.toAbsolutePath().toString() !in alreadyIngested }
                .map { file ->
                    async {
                        val absPath = file.toAbsolutePath().toString()
                        val docType = file.parent?.name ?: "unknown"
                        Document(
                            type = docType,
                            source = absPath,
                            text = PdfFileLoader(ocrProvider).load(File(absPath)),
                        )
                    }
                }
                .toList()
                .awaitAll()
        }
}