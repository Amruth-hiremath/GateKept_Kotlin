package com.gatekept.kotlinapp

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

class PdfTextExtractor(private val context: Context) {

    init {
        // PDFBox MUST be initialized before you use it!
        PDFBoxResourceLoader.init(context)
    }

    fun extractTextFromUri(uri: Uri): String {
        var document: PDDocument? = null
        var extractedText = ""

        try {
            // 1. Open the PDF file safely from the user's phone
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                document = PDDocument.load(inputStream)

                // 2. Strip the text out
                val pdfStripper = PDFTextStripper()

                // Optional: Limit to first 10 pages so massive textbooks don't freeze the app
                pdfStripper.endPage = 10

                extractedText = pdfStripper.getText(document)
            }
        } catch (e: Exception) {
            Log.e("GateKeptAI", "Failed to extract PDF text", e)
        } finally {
            // 3. Always close the document to prevent memory leaks!
            document?.close()
        }

        // 4. Clean up the text (Remove weird tabs, double spaces, and newlines)
        return cleanText(extractedText)
    }

    private fun cleanText(rawText: String): String {
        return rawText
            .replace("\n", " ")
            .replace("\r", " ")
            .replace(Regex("\\s+"), " ") // Replaces multiple spaces with a single space
            .trim()
    }
}