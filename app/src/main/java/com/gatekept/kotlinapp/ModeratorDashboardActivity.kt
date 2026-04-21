package com.gatekept.kotlinapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.S3ClientOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot
import android.graphics.Color
import android.graphics.Typeface
import android.content.res.ColorStateList
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale

class ModeratorDashboardActivity : AppCompatActivity() {

    companion object {
        private const val BUCKET_NAME = "gatekept"
    }

    private lateinit var db: FirebaseFirestore
    private lateinit var s3Client: AmazonS3Client

    private lateinit var rvAdminDocs: RecyclerView
    private lateinit var docAdapter: AdminDocAdapter
    private lateinit var userAdapter: AdminUserAdapter

    private val currentDocList = mutableListOf<Document>()
    private val currentUserList = mutableListOf<Map<String, Any>>()

    private var currentView = "PENDING"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_moderator_dashboard)

        db = FirebaseFirestore.getInstance()

        val accessKey = BuildConfig.R2_ACCESS_KEY.replace(Regex("[\\s\\uFEFF\\u200B]+"), "")
        val secretKey = BuildConfig.R2_SECRET_KEY.replace(Regex("[\\s\\uFEFF\\u200B]+"), "")
        val endpoint  = BuildConfig.R2_ENDPOINT.replace(Regex("[\\s\\uFEFF\\u200B]+"), "")

        val credentials = BasicAWSCredentials(accessKey, secretKey)
        val clientConfig = com.amazonaws.ClientConfiguration().apply { signerOverride = "AWSS3V4SignerType" }

        s3Client = AmazonS3Client(credentials, clientConfig).also {
            it.setRegion(com.amazonaws.regions.Region.getRegion(com.amazonaws.regions.Regions.US_EAST_1))
            it.setEndpoint(endpoint)
            it.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).build())
        }

        rvAdminDocs = findViewById(R.id.rvAdminDocs)
        rvAdminDocs.layoutManager = LinearLayoutManager(this)

        docAdapter = AdminDocAdapter(currentDocList)
        userAdapter = AdminUserAdapter(currentUserList)
        rvAdminDocs.adapter = docAdapter

        findViewById<View>(R.id.btnAdminLogout).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupAdmin)
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.chipPending) -> { currentView = "PENDING"; rvAdminDocs.adapter = docAdapter; fetchAdminDocuments() }
                checkedIds.contains(R.id.chipApproved) -> { currentView = "APPROVED"; rvAdminDocs.adapter = docAdapter; fetchAdminDocuments() }
                checkedIds.contains(R.id.chipUsers) -> { currentView = "USERS"; rvAdminDocs.adapter = userAdapter; fetchUsers() }
            }
        }

        fetchAdminDocuments()
    }

    private fun fetchAdminDocuments() {
        db.collection("documents").whereEqualTo("status", currentView)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                currentDocList.clear()
                for (snap in snapshots) {
                    currentDocList.add(snap.toObject(Document::class.java).copy(id = snap.id))
                }
                currentDocList.sortByDescending { it.timestamp?.seconds }
                docAdapter.notifyDataSetChanged()
            }
    }

    private fun fetchUsers() {
        db.collection("users").addSnapshotListener { snapshots, error ->
            if (error != null || snapshots == null) return@addSnapshotListener
            currentUserList.clear()
            for (snap in snapshots) currentUserList.add(snap.data)
            userAdapter.notifyDataSetChanged()
        }
    }

    private fun sendNotification(uid: String?, title: String, message: String) {
        if (uid.isNullOrEmpty()) return
        db.collection("notifications").add(
            mapOf("uid" to uid, "title" to title, "message" to message,
                "timestamp" to com.google.firebase.Timestamp.now(), "isRead" to false)
        )
    }

    private fun showRejectDialog(doc: Document) {
        val dialog = BottomSheetDialog(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B131F"))
            setPadding(60, 60, 60, 60)
        }

        layout.addView(TextView(this).apply {
            text = "Reject Document"; setTextColor(Color.WHITE); textSize = 20f
            setTypeface(null, Typeface.BOLD)
        })

        val etReason = EditText(this).apply {
            hint = "Provide a reason (e.g. Blurry, Copyright, Spam)"
            setHintTextColor(Color.parseColor("#8B9EB7"))
            setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#121B2A"))
            setPadding(40, 40, 40, 40); minLines = 3
        }
        layout.addView(etReason, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 40, 0, 40) })

        val btnSubmit = MaterialButton(this).apply {
            text = "CONFIRM REJECTION"
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF6B6B"))
        }
        layout.addView(btnSubmit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 160))

        btnSubmit.setOnClickListener {
            val reason = etReason.text.toString().trim()
            if (reason.isEmpty()) return@setOnClickListener

            btnSubmit.isEnabled = false
            btnSubmit.text = "DELETING..."

            sendNotification(doc.uploaderUid, "Document Rejected ❌", "Your upload '${doc.title}' was rejected. Reason: $reason")

            Thread {
                try {
                    val fileUrl = doc.fileUrl
                    if (!fileUrl.isNullOrEmpty() && fileUrl.contains("/")) {
                        s3Client.deleteObject(BUCKET_NAME, fileUrl.substringAfterLast('/'))
                    }
                } catch (e: Exception) { e.printStackTrace() }

                runOnUiThread {
                    db.collection("documents").document(doc.id!!).delete()
                        .addOnSuccessListener {
                            dialog.dismiss()
                            Toast.makeText(this, "Document Rejected.", Toast.LENGTH_SHORT).show()
                        }
                }
            }.start()
        }

        dialog.setContentView(layout)
        dialog.show()
    }

    // --- Document Adapter ---
    private inner class AdminDocAdapter(private val docs: List<Document>) :
        RecyclerView.Adapter<AdminDocAdapter.AdminViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            AdminViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_admin_doc, parent, false))

        override fun onBindViewHolder(holder: AdminViewHolder, position: Int) {
            val doc = docs[position]
            holder.tvTitle.text = doc.title
            holder.tvUploader.text = "By: ${doc.uploaderName}"

            holder.cgTags.removeAllViews()
            doc.tags?.forEach { tag ->
                if (tag.isNullOrEmpty()) return@forEach
                holder.cgTags.addView(Chip(holder.itemView.context).apply {
                    text = tag; setTextColor(Color.WHITE)
                    setChipBackgroundColorResource(R.color.glass_button_body)
                })
            }

            holder.btnView.setOnClickListener {
                startActivity(Intent(this@ModeratorDashboardActivity, DocumentDetailActivity::class.java).apply {
                    putExtra("DOCUMENT_ID", doc.id)
                    putExtra("DOCUMENT_URL", doc.fileUrl)
                })
            }

            if (doc.status == "PENDING") {
                holder.tvStatusBadge.text = "NEEDS APPROVAL"
                holder.tvStatusBadge.setTextColor(Color.parseColor("#FFD700"))
                holder.btnReject.visibility = View.VISIBLE
                holder.btnReject.setOnClickListener { showRejectDialog(doc) }
                holder.btnAction.text = "APPROVE"
                holder.btnAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2ED573"))
                holder.btnAction.setOnClickListener {
                    db.collection("documents").document(doc.id!!).update("status", "APPROVED")
                    sendNotification(doc.uploaderUid, "Document Approved! 🎉", "Your upload '${doc.title}' is now live on GateKept!")
                    Toast.makeText(this@ModeratorDashboardActivity, "Approved", Toast.LENGTH_SHORT).show()
                }
            } else {
                holder.tvStatusBadge.text = "LIVE IN VAULT"
                holder.tvStatusBadge.setTextColor(Color.parseColor("#4CA6FF"))
                holder.btnReject.visibility = View.GONE
                holder.btnAction.text = "DELETE"
                holder.btnAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF6B6B"))
                holder.btnAction.setOnClickListener { showRejectDialog(doc) }
            }
        }

        override fun getItemCount() = docs.size

        inner class AdminViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.tvAdminDocTitle)
            val tvUploader: TextView = itemView.findViewById(R.id.tvAdminUploader)
            val tvStatusBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)
            val cgTags: ChipGroup = itemView.findViewById(R.id.cgAdminTags)
            val btnView: MaterialButton = itemView.findViewById(R.id.btnAdminView)
            val btnAction: MaterialButton = itemView.findViewById(R.id.btnAdminAction)
            val btnReject: MaterialButton = itemView.findViewById(R.id.btnAdminReject)
        }
    }

    // --- User Adapter ---
    private inner class AdminUserAdapter(private val users: List<Map<String, Any>>) :
        RecyclerView.Adapter<AdminUserAdapter.UserViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
            val card = MaterialCardView(parent.context).apply {
                setCardBackgroundColor(Color.parseColor("#121B2A"))
                radius = 24f; strokeColor = Color.parseColor("#1E2A3C"); strokeWidth = 2
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) }
            }
            val layout = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40)
            }
            val nameView = TextView(parent.context).apply {
                id = View.generateViewId(); setTextColor(Color.WHITE); textSize = 16f; setTypeface(null, Typeface.BOLD)
            }
            val emailView = TextView(parent.context).apply {
                id = View.generateViewId(); setTextColor(Color.parseColor("#8B9EB7")); setPadding(0, 8, 0, 0)
            }
            layout.addView(nameView)
            layout.addView(emailView)
            card.addView(layout)
            card.tag = intArrayOf(nameView.id, emailView.id)
            return UserViewHolder(card)
        }

        override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
            val user = users[position]
            val ids = holder.itemView.tag as IntArray
            holder.itemView.findViewById<TextView>(ids[0]).text = user["name"] as? String ?: ""
            holder.itemView.findViewById<TextView>(ids[1]).text = user["email"] as? String ?: ""
        }

        override fun getItemCount() = users.size

        inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    }
}
