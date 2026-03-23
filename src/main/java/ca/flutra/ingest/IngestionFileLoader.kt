package ca.flutra.ingest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.RandomAccessReadBufferedFile
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File

class IngestionFileLoader private constructor(
    private val fileTypeLoaders: Set<FileTypeLoader>,
) {

    private val loadersMap: Map<FileType, FileTypeLoader> by lazy {
        fileTypeLoaders.associateBy { it.supportedFileExtension }
    }

    suspend fun load(baseDir: String): List<IngestionModel> = withContext(Dispatchers.IO) {
        return@withContext File(baseDir)
            .walk(FileWalkDirection.TOP_DOWN)
            .mapNotNull { file ->
                if (file.exists() && !file.isDirectory) {
                    async {
                        IngestionModel(
                            loadersMap[FileType.from(file.extension)]?.load(file) ?: "",
                            file.absolutePath,
                        )
                    }
                } else {
                    null
                }
            }
            .toList()
            .awaitAll()
    }

    companion object {
        @JvmStatic
        fun newInstance(ocrProvider: OcrProvider, vararg fileTypeExtensions: String): IngestionFileLoader {
            val loaders = fileTypeExtensions
                .map { FileType.from(it) }
                .map {
                    when (it) {
                        FileType.TEXT_BASED -> TextBasedFileLoader()
                        FileType.PDF -> PdfFileLoader(ocrProvider)
                    }
                }
                .toSet()
            return IngestionFileLoader(fileTypeLoaders = loaders)
        }
    }
}

enum class FileType(val extensions: Set<String>) {
    TEXT_BASED(setOf("txt", "md", "log")),
    PDF(setOf("pdf"));

    companion object {
        fun from(extension: String): FileType {
            return entries.firstOrNull { it.extensions.contains(extension) } ?: TEXT_BASED
        }
    }
}

interface FileTypeLoader {
    val supportedFileExtension: FileType

    suspend fun load(file: File): String
}

class TextBasedFileLoader : FileTypeLoader {
    override val supportedFileExtension = FileType.TEXT_BASED
    override suspend fun load(file: File): String = file.readText()
}

class PdfFileLoader(
    private val ocrProvider: OcrProvider,
) : FileTypeLoader {
    override val supportedFileExtension = FileType.PDF

    override suspend fun load(file: File): String {
        return imageToText(Loader.loadPDF(RandomAccessReadBufferedFile(file)))
    }

    private suspend fun imageToText(pdf: PDDocument): String = withContext(Dispatchers.IO) {
        return@withContext try {
            pdf.use { document ->
                val pdfRenderer = PDFRenderer(document)
                (0 until document.numberOfPages)
                    .map { pageIndex ->
                        pdfRenderer.renderImageWithDPI(pageIndex, 150f)
                    }
                    .map { image ->
                        async {
                            val text = ocrProvider.imageToText(image)
                            println("Page content size is ${text.length} chars")
                            text
                        }
                    }.awaitAll().joinToString("\n")
            }
        } catch (e: Exception) {
            System.err.println("An error occurred while loading the PDF: ${e.message}")
            e.printStackTrace()
            ""
        }
    }
}