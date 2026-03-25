package ca.flutra.ingest.ocr

import ca.flutra.ingest.IngestionModel

interface OcrRefiner {

    suspend fun refine(documents: List<IngestionModel>): List<IngestionModel>
}