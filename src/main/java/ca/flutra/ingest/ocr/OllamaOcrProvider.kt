package ca.flutra.ingest.ocr

import dev.langchain4j.data.image.Image
import dev.langchain4j.data.message.ImageContent
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.ollama.OllamaChatModel
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.Base64
import javax.imageio.ImageIO

internal object OllamaOcrProvider : OcrProvider {

    private val ocrModel: OllamaChatModel by lazy {
        OllamaChatModel.OllamaChatModelBuilder()
            .baseUrl("http://localhost:11434")
            .temperature(0.0)
            .modelName("qwen2.5vl:7b")
            .timeout(Duration.ofMinutes(5))
            .build()
            ?: throw IllegalStateException("Failed to provide the model!")

    }

    override fun imageToText(image: BufferedImage): String {
        return read(image)
    }

    private fun read(image: BufferedImage): String {
        val resized = resizeImageForOcr(image)

        val outputStream = ByteArrayOutputStream()
        ImageIO.write(resized, "png", outputStream)
        val imageBytes = outputStream.toByteArray()
        val base64Data = Base64.getEncoder().encodeToString(imageBytes)

        val ocrImage = Image.builder()
            .base64Data(base64Data)
            .mimeType("image/png")
            .build()

        val userMessage = UserMessage.from(
            TextContent.from("Describe this image"),
            ImageContent.from(ocrImage)
        )
        return ocrModel.chat(userMessage)?.aiMessage()?.text() ?: ""
    }

    private fun resizeImageForOcr(image: BufferedImage): BufferedImage {
        val maxDimension = 1920
        val scale = minOf(maxDimension.toDouble() / image.width, maxDimension.toDouble() / image.height, 1.0)
        if (scale >= 1.0) return image // already small enough

        val newWidth = (image.width * scale).toInt()
        val newHeight = (image.height * scale).toInt()
        val resized = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        resized.createGraphics().apply {
            drawImage(image.getScaledInstance(newWidth, newHeight, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
            dispose()
        }
        return resized
    }
}