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

    @Volatile
    private var cachedDocs: List<DocumentMeta> = emptyList()

    suspend fun initializeSearch() {

        // Load offline cache instantly
        val localDocs = cache.loadFromDisk()
        cachedDocs = localDocs

        Log.d("GateKeptSearch", "Loaded ${localDocs.size} cached docs")

        val lastSyncTime = sharedPrefs.getLong("LAST_SYNC", 0L)
        val lastSyncDate = Date(lastSyncTime)

        try {

            val snapshot = firestore.collection("documents")
                .whereEqualTo("status", "APPROVED")
                .get()
                .await()

            if (!snapshot.isEmpty) {

                val newDocs = snapshot.documents.mapNotNull { doc ->

                    val embeddingList =
                        doc.get("embedding") as? List<Number>
                            ?: return@mapNotNull null

                    DocumentMeta(
                        id = doc.id,
                        title = doc.getString("title") ?: "Untitled",
                        embedding = embeddingList.map { it.toFloat() },
                        tags = doc.get("tags") as? List<String> ?: emptyList(),
                        fileUrl = doc.getString("fileUrl") ?: ""
                    )
                }

                val mergedDocs = cachedDocs.toMutableList()
                mergedDocs.addAll(newDocs)

                cachedDocs = mergedDocs

                cache.saveToDisk(mergedDocs)

                sharedPrefs.edit()
                    .putLong("LAST_SYNC", System.currentTimeMillis())
                    .apply()

                Log.d("GateKeptSearch", "Downloaded ${newDocs.size} docs")
            }

        } catch (e: Exception) {
            Log.e("GateKeptSearch", "Sync failed", e)
        }
    }

    fun search(queryVector: FloatArray): List<DocumentMeta> {

        if (queryVector.isEmpty()) return emptyList()

        return cachedDocs.map { doc ->

            val similarity =
                cosineSimilarity(
                    queryVector,
                    doc.embedding.toFloatArray()
                )

            Log.d(
                "GateKeptSearch",
                "Doc=${doc.title} score=$similarity"
            )

            doc to similarity
        }
            .filter { it.second > 0.7f }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {

        val size = minOf(a.size, b.size)

        var dot = 0f
        var normA = 0f
        var normB = 0f

        for (i in 0 until size) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        return dot / (sqrt(normA) * sqrt(normB) + 1e-8f)
    }
}