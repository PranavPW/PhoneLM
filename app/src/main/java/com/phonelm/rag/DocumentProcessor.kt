package com.phonelm.rag

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

import com.phonelm.data.VectorStore
import com.phonelm.rag.EmbeddingGenerator

class DocumentProcessor(private val context: Context) {

    private val embeddingGenerator = EmbeddingGenerator(context)
    private val vectorStore = VectorStore(embeddingGenerator)

    suspend fun processPdf(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            
            if (text.isBlank()) {
                // Return fallback message or try OCR if it's scanned (simplified logic here)
                return@withContext "PDF contained no extractable text (possibly scanned)."
            }
            
            // Store in Vector DB
            // We should ideally chunk this. For now, storing as one big chunk or simple split.
            // MVP: Split by paragraphs or fixed size
            val chunks = text.chunked(500) // Simple chunking
            chunks.forEachIndexed { index, chunk ->
                vectorStore.addDocument(chunk, File(uri.path ?: "doc").name, index)
            }
            
            return@withContext "Processed ${chunks.size} chunks from PDF."
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Error extracting PDF text: ${e.message}"
        }
    }

    suspend fun processImage(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        // Using ML Kit
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        
        // This is async, so we need to wrap it in a suspendCoroutine or blocking wait
        // ideally suspendCancellableCoroutine. For simplicity in this non-UI logic:
        var resultText = ""
        val latch = java.util.concurrent.CountDownLatch(1)
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                resultText = visionText.text
                latch.countDown()
            }
            .addOnFailureListener { e ->
                resultText = "Error: ${e.message}"
                latch.countDown()
            }
            
        latch.await()
        latch.await()
        
        if (resultText.isNotBlank()) {
            vectorStore.addDocument(resultText, "scanned_image.jpg", 0)
        }
        
        return@withContext resultText
    }
}
