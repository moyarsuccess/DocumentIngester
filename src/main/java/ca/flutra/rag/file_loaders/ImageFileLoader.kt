package ca.flutra.rag.file_loaders

import ca.flutra.rag.ocr.OcrProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.imageio.ImageIO

internal object ImageFileLoader {
    suspend fun load(file: File): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val image = ImageIO.read(file)
            if (image != null) {
                val text = OcrProvider.instance.imageToText(image)
                println("Image content size is ${text.length} chars")
                text
            } else {
                System.err.println("Failed to read image: ${file.name}")
                ""
            }
        } catch (e: Exception) {
            System.err.println("An error occurred while loading the Image: ${e.message}")
            e.printStackTrace()
            ""
        }
    }
}
