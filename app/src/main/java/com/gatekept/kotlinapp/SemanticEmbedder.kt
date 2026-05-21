package com.gatekept.kotlinapp

import android.content.Context
import android.util.Log
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

class SemanticEmbedder(private val context: Context) {

    private val sentenceEmbedding = SentenceEmbedding()
    private val ready = CompletableDeferred<Boolean>()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                loadModel()
                ready.complete(true)
            } catch (e: Exception) {
                Log.e("GateKeptAI", "Model loading failed", e)
                ready.completeExceptionally(e)
            }
        }
    }

    private suspend fun loadModel() {
        // Copy model from assets to internal storage (ONNX Runtime needs a file path)
        val modelFile = File(context.filesDir, "model.onnx")
        if (!modelFile.exists()) {
            context.assets.open("all-minilm-l6-v2/model.onnx").use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        val tokenizerBytes = context.assets.open("all-minilm-l6-v2/tokenizer.json")
            .use { it.readBytes() }

        sentenceEmbedding.init(
            modelFilepath = modelFile.absolutePath,
            tokenizerBytes = tokenizerBytes,
            useTokenTypeIds = true,
            outputTensorName = "sentence_embedding",
            useFP16 = false,
            useXNNPack = false,
            normalizeEmbeddings = true   // Required parameter – ensures L2-normalized output
        )
        Log.d("GateKeptAI", "MiniLM embedder ready (384 dims)")
    }

    /**
     * Blocks the calling thread until the embedder is initialized.
     * Called only from background (IO) threads.
     */
    private fun ensureReady(): Boolean {
        return try {
            runBlocking { ready.await() }
            true
        } catch (e: Exception) {
            Log.e("GateKeptAI", "Embedder initialization failed", e)
            false
        }
    }

    fun embedQuery(query: String): FloatArray {
        if (!ensureReady()) return FloatArray(0)
        if (query.isBlank()) return FloatArray(0)
        return try {
            runBlocking { sentenceEmbedding.encode(query) }
        } catch (e: Exception) {
            Log.e("GateKeptAI", "Query encoding failed", e)
            FloatArray(0)
        }
    }

    fun embedDocument(text: String): List<Float> {
        if (!ensureReady()) return emptyList()
        if (text.isBlank()) return emptyList()

        val cleanedText = text
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
            .trim()
        if (cleanedText.isBlank()) return emptyList()

        val words = cleanedText.split(" ")
        val chunks = words.chunked(256).map { it.joinToString(" ") }.filter { it.length > 10 }
        if (chunks.isEmpty()) return emptyList()

        val allEmbeddings = mutableListOf<FloatArray>()
        for (chunk in chunks) {
            try {
                val vec = runBlocking { sentenceEmbedding.encode(chunk) }
                if (vec.isNotEmpty()) allEmbeddings.add(vec)
            } catch (e: Exception) {
                Log.e("GateKeptAI", "Chunk encoding failed", e)
            }
        }

        if (allEmbeddings.isEmpty()) return emptyList()

        val dims = allEmbeddings.first().size
        val mean = FloatArray(dims)
        for (emb in allEmbeddings) {
            for (i in 0 until dims) mean[i] += emb[i]
        }
        for (i in 0 until dims) mean[i] /= allEmbeddings.size.toFloat()

        Log.d("GateKeptAI", "Document embedding ready: size=$dims")
        return mean.toList()
    }
}