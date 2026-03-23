package ca.flutra.new

import ca.flutra.ingest.OcrProviderImpl
import ca.flutra.ingest.PdfFileLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText

object DocumentLoader {

    suspend fun load(): List<Document> = withContext(Dispatchers.IO) {
        val root = Config.knowledgeBasePath
        require(Files.exists(root)) { "Knowledge base path not found: $root" }
        return@withContext Files.walk(root)
            .filter { !it.isDirectory() && it.extension == "pdf" }
            .map { file ->
                async {
                    val docType = file.parent?.name ?: "unknown"
                    Document(
                        type = docType,
                        source = file.toAbsolutePath().toString(),
                        text = PdfFileLoader(OcrProviderImpl).load(File(file.toAbsolutePath().toString())),
                    )
                }
            }
            .toList()
            .awaitAll()
    }
}