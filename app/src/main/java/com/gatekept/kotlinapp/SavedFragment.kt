package com.gatekept.kotlinapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot

class SavedFragment : Fragment() {

    private lateinit var rvSaved: RecyclerView
    private lateinit var adapter: DocumentAdapter
    private val savedDocuments = mutableListOf<Document>()
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_saved, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        rvSaved = view.findViewById(R.id.rvSavedDocs)
        rvSaved.layoutManager = LinearLayoutManager(context)

        adapter = DocumentAdapter(savedDocuments) { document ->
            document.fileUrl?.let {
                startActivity(Intent(context, DocumentDetailActivity::class.java).apply {
                    putExtra("DOCUMENT_ID", document.id)
                    putExtra("DOCUMENT_URL", document.fileUrl)
                    putExtra("DOCUMENT_TITLE", document.title)
                    putExtra("UPLOADER_NAME", document.uploaderName)
                    putExtra("COURSE_CODE", document.courseCode)
                    putExtra("CATEGORY", document.category)
                })
            }
        }
        rvSaved.adapter = adapter

        fetchSavedDocuments()
    }

    private fun fetchSavedDocuments() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).collection("bookmarks")
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null || !isAdded) return@addSnapshotListener

                savedDocuments.clear()
                val tvSavedCount = view?.findViewById<TextView>(R.id.tvSavedCount)
                val emptyState = view?.findViewById<View>(R.id.emptyStateLayout)

                if (snapshots.isEmpty) {
                    tvSavedCount?.text = "0 documents saved"
                    emptyState?.visibility = View.VISIBLE
                    rvSaved.visibility = View.GONE
                    adapter.notifyDataSetChanged()
                    return@addSnapshotListener
                }

                var validCount = 0
                var processedCount = 0
                val totalBookmarks = snapshots.size()

                for (bookmarkSnap in snapshots) {
                    val docId = bookmarkSnap.id
                    db.collection("documents").document(docId).get()
                        .addOnSuccessListener { docSnap ->
                            processedCount++
                            if (docSnap.exists()) {
                                val doc = docSnap.toObject(Document::class.java)
                                if (doc != null) {
                                    savedDocuments.add(doc.copy(id = docSnap.id))
                                    validCount++
                                }
                            } else {
                                bookmarkSnap.reference.delete()
                            }

                            if (processedCount == totalBookmarks) {
                                tvSavedCount?.text = "$validCount documents saved"
                                if (validCount == 0) {
                                    emptyState?.visibility = View.VISIBLE
                                    rvSaved.visibility = View.GONE
                                } else {
                                    emptyState?.visibility = View.GONE
                                    rvSaved.visibility = View.VISIBLE
                                }
                                adapter.notifyDataSetChanged()
                            }
                        }
                }
            }
    }
}
