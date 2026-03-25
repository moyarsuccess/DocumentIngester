package ca.flutra.ingest

import ca.flutra.ingest.ocr.OcrProvider
import ca.flutra.ingest.ocr.OcrRefiner
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
    private val ocrProvider: OcrProvider,
    private val ocrRefiner: OcrRefiner,
) {

    private val pdfFileLoader = PdfFileLoader(ocrProvider = ocrProvider)
    private val textBasedFileLoader = TextBasedFileLoader()
    private val textBasedExtensions = setOf("txt", "md", "log")

    suspend fun load(baseDir: String): List<IngestionModel> = withContext(Dispatchers.IO) {
        val outputPath = "output"
        val output = File(outputPath)
        if (output.exists()) {
            return@withContext output.walkTopDown()
                .filter { it.isFile }
                .map {
                    IngestionModel(
                        content = textBasedFileLoader.load(it),
                        sourcePath = it.absolutePath,
                    )
                }
                .toList()
        }
        if (!output.exists()) {
            output.mkdir()
        }
        val models = File(baseDir)
            .walk(FileWalkDirection.TOP_DOWN)
            .mapNotNull { file ->
                if (file.exists() && !file.isDirectory) {
                    async {
                        when {
                            file.extension == "pdf" -> {
                                IngestionModel(
                                    pdfFileLoader.load(file),
                                    file.absolutePath,
                                )
                            }

                            textBasedExtensions.contains(file.extension) -> {
                                IngestionModel(
                                    textBasedFileLoader.load(file),
                                    file.absolutePath,
                                )
                            }

                            else -> null
                        }
                    }
                } else {
                    null
                }
            }
            .toList()
            .awaitAll()
            .filterNotNull()
            .onEach { model ->
                File(model.sourcePath).apply {
                    File("$outputPath/$name.txt").writeText(model.content)
                }
            }
        return@withContext ocrRefiner.refine(models)
    }

    companion object {
        @JvmStatic
        fun newInstance(
            ocrProvider: OcrProvider,
            ocrRefiner: OcrRefiner,
        ): IngestionFileLoader {
            return IngestionFileLoader(ocrRefiner = ocrRefiner, ocrProvider = ocrProvider)
        }
    }
}

class TextBasedFileLoader {
    fun load(file: File): String = file.readText()
}

class PdfFileLoader(
    private val ocrProvider: OcrProvider,
) {
    suspend fun load(file: File): String {
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