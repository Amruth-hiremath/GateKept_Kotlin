package com.gatekept.kotlinapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class DocumentMeta(
    val id: String,
    val title: String,
    val embedding: List<Float>,
    val tags: List<String>,
    val fileUrl: String,
    val contentSnippet: String = ""
)

class EmbeddingCache(private val context: Context) {
    private val fileName = "gatekept_embeddings.json"
    private val gson = Gson()

    fun saveToDisk(documents: List<DocumentMeta>) {
        try {
            // Ensure no duplicates before saving (by ID)
            val unique = documents.distinctBy { it.id }
            val jsonString = gson.toJson(unique)
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
            val list: List<DocumentMeta> = gson.fromJson(jsonString, type) ?: emptyList()
            list.map { doc ->
                if (doc.title == null) {
                    doc.copy(title = "Untitled", contentSnippet = doc.contentSnippet ?: "")
                } else doc
            }.distinctBy { it.id }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}