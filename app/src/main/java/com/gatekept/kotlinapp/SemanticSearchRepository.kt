package com.gatekept.kotlinapp

import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import kotlin.math.sqrt

class SemanticSearchRepository(
    private val firestore: FirebaseFirestore,
    private val cache: EmbeddingCache,
    private val sharedPrefs: SharedPreferences
) {
    // In‑memory store: document ID → metadata + embedding
    private var documentMap = mutableMapOf<String, DocumentMeta>()

    /**
     * Initialize from local JSON cache, then delta‑sync with Firestore.
     */
    suspend fun initializeSearch() {
        // 1. Load cached documents from disk
        val cachedDocs = cache.loadFromDisk()
        documentMap.clear()
        cachedDocs.forEach { doc -> documentMap[doc.id] = doc }

        Log.d("GateKeptSearch", "Cache loaded: ${documentMap.size} documents")

        // 2. Delta sync from Firestore
        val lastSyncTime = sharedPrefs.getLong("LAST_SYNC", 0L)
        val lastSyncDate = Date(lastSyncTime)

        try {
            val snapshot = firestore.collection("documents")
                .whereEqualTo("status", "APPROVED")
                .whereGreaterThan("timestamp", lastSyncDate)
                .get()
                .await()

            val newDocs = snapshot.documents.mapNotNull { doc ->
                val emb = doc.get("embedding") as? List<Double> ?: return@mapNotNull null
                DocumentMeta(
                    id = doc.id,
                    title = doc.getString("title") ?: "",
                    embedding = (doc.get("embedding") as? List<Double>)?.map { it.toFloat() } ?: emptyList(),
                    tags = doc.get("tags") as? List<String> ?: emptyList(),
                    fileUrl = doc.getString("fileUrl") ?: "",
                    contentSnippet = doc.getString("contentSnippet") ?: ""
                )
            }

            // Merge without duplicates (using map ensures unique keys)
            newDocs.forEach { doc -> documentMap[doc.id] = doc }

            // Remove deleted documents? Not needed; they'll be removed from Firestore and won't appear in delta.
            // Write the deduplicated map back to disk
            cache.saveToDisk(documentMap.values.toList())

            // Update sync timestamp
            sharedPrefs.edit().putLong("LAST_SYNC", System.currentTimeMillis()).apply()

            Log.d("GateKeptSearch", "Synced ${newDocs.size} new docs. Total=${documentMap.size}")
        } catch (e: Exception) {
            Log.e("GateKeptSearch", "Delta sync failed, using cached data", e)
            // If the index is missing, fallback to full sync once (optional)
            // For now just continue with cached data
        }
    }

    /**
     * Hybrid search: semantic similarity + exact match boost.
     * Returns documents sorted by final score (higher is better).
     */
    fun search(queryVector: FloatArray, rawQuery: String = "", minScore: Float = 0.45f): List<DocumentMeta> {
        if (documentMap.isEmpty()) return emptyList()
        val lowerQuery = rawQuery.lowercase().trim()

        val scored = documentMap.values.map { doc ->
            var score = cosineSimilarity(queryVector, doc.embedding.toFloatArray())

            // Title boost – always strong
            if (lowerQuery.isNotEmpty() && doc.title.lowercase().contains(lowerQuery)) {
                score += 0.50f
            }

            // Tag / course‑code boost
            if (lowerQuery.isNotEmpty()) {
                val tagMatch = doc.tags.any { it.lowercase().contains(lowerQuery) }
                if (tagMatch) score += 0.20f
            }

            // Snippet boost – always active, but only for queries longer than 2 chars
            if (lowerQuery.length > 2) {
                val snippet = doc.contentSnippet ?: ""
                if (snippet.lowercase().contains(lowerQuery)) {
                    score += 0.15f
                }
            }

            Pair(doc, score)
        }

        scored.forEach { Log.d("GateKeptSearch", "Score=${it.second} for ${it.first.title}") }

        return scored
            .filter { it.second >= minScore }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        // Guard against mismatched dimensions (old 100-dim vs new 384-dim)
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) {
            return 0f
        }
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }
}