package ca.flutra.rag.ocr

import ca.flutra.rag.common.Config
import net.sourceforge.tess4j.Tesseract
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage

internal object TesseractOcrProvider : OcrProvider {

    private val log = LoggerFactory.getLogger(TesseractOcrProvider::class.java)

    override fun imageToText(image: BufferedImage): String {
        return try {
            val result = threadLocalTesseract.get().doOCR(image)
            result ?: ""
        } catch (e: Exception) {
            log.error("Tesseract OCR failed: ${e.message}", e)
            ""
        }
    }

    private val threadLocalTesseract = ThreadLocal.withInitial {
        Tesseract().apply {
            if (Config.TESSERACT_DATA_PATH.isNotBlank()) {
                setDatapath(Config.TESSERACT_DATA_PATH)
            }
            setLanguage(Config.TESSERACT_LANGUAGE)
            setPageSegMode(Config.TESSERACT_PAGE_SEG_MODE)
            setOcrEngineMode(1)
        }
    }
}
