package com.phonelm.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

class ModelDownloader(private val context: Context) {
    
    // GGUF Model
    // https://huggingface.co/LateMonk/PhoneLM_Models/resolve/main/phonelm-1.5b-q4_k_m.gguf
    // Embedding Model
    // https://huggingface.co/LateMonk/PhoneLM_Models/resolve/main/all-MiniLM-L6-v2.onnx
    // Tokenizer
    // https://huggingface.co/LateMonk/PhoneLM_Models/resolve/main/tokenizer.json

    private val REPO_URL = "https://huggingface.co/LateMonk/PhoneLM_Models/resolve/main"

    fun downloadAllModels(): List<Long> {
        val ids = mutableListOf<Long>()
        ids.add(downloadFile("$REPO_URL/phonelm-1.5b-q4_k_m.gguf", "phonelm-1.5b-q4_k_m.gguf"))
        ids.add(downloadFile("$REPO_URL/all-MiniLM-L6-v2.onnx", "all-MiniLM-L6-v2.onnx"))
        ids.add(downloadFile("$REPO_URL/tokenizer.json", "tokenizer.json"))
        return ids
    }

    private fun downloadFile(url: String, fileName: String): Long {
        try {
            val validUrl = if (url.startsWith("http")) url else "https://$url"
            
            // Create directory if it doesn't exist
            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PhoneLM")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            // Check if file already exists (optional optimization)
            val file = File(downloadDir, fileName)
            if (file.exists()) {
               // return -1L // Already exists
            }

            val request = DownloadManager.Request(Uri.parse(validUrl))
                .setTitle(fileName)
                .setDescription("Downloading $fileName")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "PhoneLM/$fileName")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            return downloadManager.enqueue(request)
        } catch (e: Exception) {
            e.printStackTrace()
            return -1L
        }
    }
    
    // Legacy method for single GGUF download (keeping for compatibility)
    fun downloadModel(url: String, fileName: String): Long {
         return downloadFile(url, fileName)
    }
}
