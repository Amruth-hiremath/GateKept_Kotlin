package com.gatekept.kotlinapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

// A stripped-down version of your Document class just for searching
data class DocumentMeta(
    val id: String,
    val title: String,
    val embedding: List<Float>,
    val tags: List<String>,
    val fileUrl: String
)

class EmbeddingCache(private val context: Context) {
    private val fileName = "gatekept_embeddings_v1.json"
    private val gson = Gson()

    fun saveToDisk(documents: List<DocumentMeta>) {
        try {
            val jsonString = gson.toJson(documents)
            File(context.filesDir, fileName).writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadFromDisk(): List<DocumentMeta> {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return emptyList()

        return try {
            val jsonString = file.readText()
            val type = object : TypeToken<List<DocumentMeta>>() {}.type
            gson.fromJson(jsonString, type) ?: emptyList()
        } catch (e: Exception) {
            // If the file corrupts, delete it so we pull fresh from Firebase next time
            if (file.exists()) file.delete()
            emptyList()
        }
    }
}