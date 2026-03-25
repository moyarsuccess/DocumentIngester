package ca.flutra.rag.ocr

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import javax.imageio.ImageIO

object GoogleVisionOcrProvider : OcrProvider {

    private val client: ImageAnnotatorClient by lazy {
        val credentials = GoogleCredentials
            .fromStream(FileInputStream("google-credentials.json"))
            .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

        val settings = ImageAnnotatorSettings.newBuilder()
            .setCredentialsProvider { credentials }
            .build()

        ImageAnnotatorClient.create(settings)
    }

    override fun imageToText(image: BufferedImage): String {
        val imageBytes = image.toPngBytes()

        val visionImage = Image.newBuilder()
            .setContent(ByteString.copyFrom(imageBytes))
            .build()

        val feature = Feature.newBuilder()
            .setType(Feature.Type.DOCUMENT_TEXT_DETECTION) // best for dense doc text
            .build()

        val request = AnnotateImageRequest.newBuilder()
            .addFeatures(feature)
            .setImage(visionImage)
            .build()

        val response = client.batchAnnotateImages(listOf(request))
        val result = response.getResponses(0)

        if (result.hasError()) {
            throw Exception("Google Vision error: ${result.error.message}")
        }

        return result.fullTextAnnotation.text.trim()
    }

    private fun BufferedImage.toPngBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        ImageIO.write(this, "PNG", output)
        return output.toByteArray()
    }
}
