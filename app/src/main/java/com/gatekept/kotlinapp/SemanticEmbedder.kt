package com.gatekept.kotlinapp

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import kotlin.math.sqrt

class SemanticEmbedder(context: Context) {

    private var embedder: TextEmbedder? = null
    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("ml/universal_sentence_encoder.tflite")
                .build()
            val options = TextEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .build()
            embedder = TextEmbedder.createFromOptions(context, options)
            Log.d(
                "GateKeptAI",
                "Semantic Embedder loaded successfully!"
            )
        } catch (e: Exception) {
            Log.e(
                "GateKeptAI",
                "Error loading embedder",
                e
            )
        }
    }

    fun embedQuery(query: String): FloatArray {
        if (query.isBlank()) {
            return FloatArray(0)
        }
        val result =
            embedder?.embed(query)
                ?: return FloatArray(0)
        val embeddingsList =
            result.embeddingResult().embeddings()
        if (embeddingsList.isEmpty()) {
            return FloatArray(0)
        }
        val rawEmbedding =
            embeddingsList.first().floatEmbedding()
                ?: return FloatArray(0)
        return normalize(rawEmbedding)
    }

    fun embedDocument(text: String): List<Float> {
        if (text.isBlank()) {
            return emptyList()
        }
        // Clean text a bit more aggressively
        val cleanedText = text
            .replace(Regex("[^A-Za-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleanedText.isBlank()) {
            return emptyList()
        }
        // Split into chunks
        val words = cleanedText.split(" ")
        val chunks = words
            .chunked(300)
            .map { it.joinToString(" ") }
            .filter { it.length > 20 }
        val chunkEmbeddings = mutableListOf<FloatArray>()
        for (chunk in chunks) {
            try {
                val result = embedder?.embed(chunk)
                val embeddingsList =
                    result?.embeddingResult()?.embeddings()
                if (!embeddingsList.isNullOrEmpty()) {
                    val floatArr =
                        embeddingsList.first().floatEmbedding()
                    if (floatArr != null) {
                        val normalized =
                            normalize(floatArr)
                        chunkEmbeddings.add(normalized)
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    "GateKeptAI",
                    "Chunk embedding failed",
                    e
                )
            }
        }
        if (chunkEmbeddings.isEmpty()) {
            return emptyList()
        }
        val vectorDim = chunkEmbeddings.first().size
        val meanEmbedding = FloatArray(vectorDim)
        for (i in 0 until vectorDim) {
            var sum = 0f
            for (embedding in chunkEmbeddings) {
                sum += embedding[i]
            }
            meanEmbedding[i] =
                sum / chunkEmbeddings.size
        }
        val normalizedMean =
            normalize(meanEmbedding)
        Log.d(
            "GateKeptAI",
            "Generated embedding with dimension ${normalizedMean.size}"
        )
        return normalizedMean.toList()
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var magnitude = 0f
        for (value in vector) {
            magnitude += value * value
        }
        magnitude = sqrt(magnitude)
        if (magnitude == 0f) {
            return vector
        }
        val normalized = FloatArray(vector.size)
        for (i in vector.indices) {
            normalized[i] = vector[i] / magnitude
        }
        return normalized
    }
}