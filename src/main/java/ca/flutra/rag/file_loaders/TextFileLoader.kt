package ca.flutra.rag.file_loaders

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal object TextFileLoader {
    suspend fun load(file: File): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val text = file.readText()
            println("Text content size is ${text.length} chars")
            text
        } catch (e: Exception) {
            System.err.println("An error occurred while loading the Text file: ${e.message}")
            e.printStackTrace()
            ""
        }
    }
}
