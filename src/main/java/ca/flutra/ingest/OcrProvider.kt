package ca.flutra.ingest

import java.awt.image.BufferedImage

interface OcrProvider {
    fun imageToText(image: BufferedImage): String
}