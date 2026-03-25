package ca.flutra.rag

import ca.flutra.rag.ocr.OcrProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.RandomAccessReadBufferedFile
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File

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
