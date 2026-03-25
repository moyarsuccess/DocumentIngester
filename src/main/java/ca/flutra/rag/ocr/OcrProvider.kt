package ca.flutra.rag.ocr

import ca.flutra.rag.common.Config
import java.awt.image.BufferedImage

internal interface OcrProvider {
    fun imageToText(image: BufferedImage): String

    companion object {

        private val ocrProvider: OcrProvider = when (Config.OCR_PROVIDER) {
            Config.OcrProviderType.GOOGLE -> GoogleVisionOcrProvider
            Config.OcrProviderType.TESSERACT -> TesseractOcrProvider
            Config.OcrProviderType.OLLAMA -> OllamaOcrProvider
        }

        val instance: OcrProvider = ocrProvider
    }
}
