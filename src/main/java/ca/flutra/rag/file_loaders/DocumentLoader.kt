package ca.flutra.rag.file_loaders

import ca.flutra.rag.common.Config
import ca.flutra.rag.common.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name

internal object DocumentLoader {

    private val pdfSet = setOf("pdf")
    private val textBasedSet = setOf(
        "txt", "md", "csv", "json", "xml", "html", "htm", "yaml", "yml", "log", "rtf", "ini", "properties"
    )
    private val imageSet = setOf(
        "png", "jpg", "jpeg", "bmp", "gif", "webp", "tiff", "tif"
    )
    private val supportedExtensions = pdfSet + textBasedSet + imageSet

    /**
     * Loads all supported document types from the knowledge-base directory, skipping any whose
     * absolute path is already present in [alreadyIngested].
     */
    suspend fun load(alreadyIngested: Set<String> = emptySet()): List<Document> =
        withContext(Dispatchers.IO) {
            val root = Config.knowledgeBasePath
            require(Files.exists(root)) { "Knowledge base path not found: $root" }
            Files.walk(root)
                .filter { !it.isDirectory() && it.extension.lowercase() in supportedExtensions }
                .filter { it.toAbsolutePath().toString() !in alreadyIngested }
                .map { file ->
                    async {
                        val absPath = file.toAbsolutePath().toString()
                        val docType = file.parent?.name ?: "unknown"

                        val text = when (file.extension.lowercase()) {
                            in pdfSet -> PdfFileLoader.load(File(absPath))
                            in imageSet -> ImageFileLoader.load(File(absPath))
                            in textBasedSet -> TextFileLoader.load(File(absPath))
                            else -> ""
                        }

                        Document(
                            type = docType,
                            source = absPath,
                            text = text,
                        )
                    }
                }
                .toList()
                .awaitAll()
        }
}