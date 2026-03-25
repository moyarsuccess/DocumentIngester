package ca.flutra.rag.ocr

import ca.flutra.rag.Config
import net.sourceforge.tess4j.Tesseract
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage

/**
 * OCR provider backed by Tesseract via the Tess4J JNI wrapper.
 *
 * This implementation runs entirely on the local JVM — no cloud API calls and
 * no LLM inference required.  It delegates to the native Tesseract engine that
 * must be installed on the host machine.
 *
 * Prerequisites
 * ─────────────
 * 1. Install Tesseract:
 *      • macOS  : `brew install tesseract`
 *      • Debian : `apt-get install tesseract-ocr tesseract-ocr-eng`
 *      • Windows: download the UB Mannheim installer from GitHub
 * 2. (Optional) Set TESSDATA_PREFIX to the directory that contains the
 *    `tessdata/` language-data folder, or configure [Config.TESSERACT_DATA_PATH].
 *
 * Configuration (Config.kt)
 * ─────────────────────────
 *  • TESSERACT_DATA_PATH – absolute path to the tessdata directory, or empty
 *                          string to let Tess4J auto-detect from the system.
 *  • TESSERACT_LANGUAGE  – Tesseract language code(s), e.g. "eng", "fra",
 *                          or "eng+fra" for multilingual documents.
 *  • TESSERACT_PAGE_SEG_MODE – Tesseract page segmentation mode (PSM).
 *                              3 = fully automatic (default), 6 = single
 *                              uniform block, 11 = sparse text.
 */
internal object TesseractOcrProvider : OcrProvider {

    private val log = LoggerFactory.getLogger(TesseractOcrProvider::class.java)

    /**
     * Tesseract instance.  Tess4J's [Tesseract] is NOT thread-safe, so we
     * keep one instance per coroutine thread via [ThreadLocal] rather than a
     * bare singleton field.
     */
    private val threadLocalTesseract = ThreadLocal.withInitial {
        Tesseract().apply {
            // ── Data path ────────────────────────────────────────────────────
            // If Config.TESSERACT_DATA_PATH is blank, Tess4J falls back to the
            // TESSDATA_PREFIX environment variable and then to common OS paths.
            if (Config.TESSERACT_DATA_PATH.isNotBlank()) {
                setDatapath(Config.TESSERACT_DATA_PATH)
            }

            // ── Language ─────────────────────────────────────────────────────
            setLanguage(Config.TESSERACT_LANGUAGE)

            // ── Page Segmentation Mode ────────────────────────────────────────
            // PSM 3 = fully automatic page segmentation (no OSD). Works well
            // for mixed-layout document pages rendered from PDFs.
            setPageSegMode(Config.TESSERACT_PAGE_SEG_MODE)

            // ── OCR Engine Mode ───────────────────────────────────────────────
            // OEM 1 = LSTM neural net only (Tesseract 4+). Falls back to
            // OEM 0 (legacy) if the LSTM traineddata is not available.
            setOcrEngineMode(1)
        }
    }

    /**
     * Runs Tesseract OCR on the supplied [image] and returns the extracted
     * plain text.  The image is passed directly as a [BufferedImage] — no
     * temporary files are written to disk.
     */
    override fun imageToText(image: BufferedImage): String {
        return try {
            val result = threadLocalTesseract.get().doOCR(image)
            result ?: ""
        } catch (e: Exception) {
            log.error("Tesseract OCR failed: ${e.message}", e)
            ""
        }
    }
}
