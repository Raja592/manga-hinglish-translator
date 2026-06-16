package com.manga.hinglish.translator

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object OcrProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extracts all English text from a bitmap using ML Kit OCR.
     * Returns a cleaned, deduped string of the visible text blocks.
     */
    suspend fun extractText(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()

        val blocks = result.textBlocks
        if (blocks.isEmpty()) return ""

        // Collect text blocks sorted by vertical position (top to bottom)
        val lines = blocks
            .sortedBy { it.boundingBox?.top ?: 0 }
            .flatMap { block ->
                block.lines.map { line ->
                    line.text.trim()
                }
            }
            .filter { it.isNotBlank() && it.length > 1 }
            .distinct()

        return lines.joinToString("\n")
    }

    fun shutdown() {
        recognizer.close()
    }
}
