package com.gatekept.kotlinapp

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot

class ProfileFragment : Fragment() {

    private lateinit var tvInitials: TextView
    private lateinit var tvFullName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvUploadCount: TextView
    private lateinit var tvReputation: TextView
    private lateinit var tvTier: TextView
    private lateinit var rvMyUploads: RecyclerView

    private lateinit var adapter: DocumentAdapter
    private val myDocuments = mutableListOf<Document>()

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tvInitials = view.findViewById(R.id.tvInitials)
        tvFullName = view.findViewById(R.id.tvFullName)
        tvEmail = view.findViewById(R.id.tvEmail)
        tvUploadCount = view.findViewById(R.id.tvUploadCount)
        tvReputation = view.findViewById(R.id.tvReputation)
        tvTier = view.findViewById(R.id.tvTier)
        rvMyUploads = view.findViewById(R.id.rvMyUploads)

        view.findViewById<ImageView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(context, SettingsActivity::class.java))
        }

        val user = auth.currentUser ?: return
        val name = user.displayName ?: "Student"
        tvFullName.text = name
        tvEmail.text = user.email

        val parts = name.split(" ")
        val initials = if (parts.size >= 2) "${parts[0][0]}${parts[1][0]}" else "${parts[0][0]}"
        tvInitials.text = initials.uppercase()

        rvMyUploads.layoutManager = LinearLayoutManager(context)
        adapter = DocumentAdapter(myDocuments) { document ->
            document.fileUrl?.let {
                startActivity(Intent(context, DocumentDetailActivity::class.java).apply {
                    putExtra("DOCUMENT_ID", document.id)
                    putExtra("DOCUMENT_URL", document.fileUrl)
                })
            }
        }
        rvMyUploads.adapter = adapter

        fetchUserStats(name)
    }

    private fun fetchUserStats(userName: String) {
        db.collection("documents").whereEqualTo("uploaderName", userName)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null || !isAdded) return@addSnapshotListener

                var approvedCount = 0
                var totalUpvotes = 0
                myDocuments.clear()

                for (docSnap in snapshots) {
                    val doc = docSnap.toObject(Document::class.java).copy(id = docSnap.id)
                    val status = doc.status ?: "APPROVED"

                    if (status == "PENDING") {
                        myDocuments.add(doc.copy(title = "⏳ [IN REVIEW] ${doc.title}"))
                    } else if (status == "APPROVED") {
                        approvedCount++
                        totalUpvotes += docSnap.getLong("upvotes")?.toInt() ?: 0
                        myDocuments.add(doc)
                    } else {
                        myDocuments.add(doc)
                    }
                }

                myDocuments.sortByDescending { it.timestamp?.seconds }
                adapter.notifyDataSetChanged()

                val finalReputation = if (approvedCount > 0 || totalUpvotes > 0)
                    100 + (approvedCount * 45) + (totalUpvotes * 20) + (approvedCount * totalUpvotes * 2)
                else 0

                val tier = when {
                    finalReputation >= 5000 -> "★ Grandmaster"
                    finalReputation >= 2000 -> "★ Master Scholar"
                    finalReputation >= 1000 -> "★ Expert"
                    finalReputation >= 500  -> "★ Contributor"
                    else                    -> "★ Novice"
                }
                tvTier.text = tier
                animateCounter(tvUploadCount, approvedCount)
                animateCounter(tvReputation, finalReputation)
            }
    }

    private fun animateCounter(targetView: TextView, targetValue: Int) {
        ValueAnimator.ofInt(0, targetValue).apply {
            duration = 1500
            addUpdateListener { targetView.text = it.animatedValue.toString() }
            start()
        }
    }
}
