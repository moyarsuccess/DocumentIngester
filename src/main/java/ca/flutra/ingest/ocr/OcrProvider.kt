package ca.flutra.ingest.ocr

import java.awt.image.BufferedImage

interface OcrProvider {
    fun imageToText(image: BufferedImage): String
}