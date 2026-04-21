package com.gatekept.kotlinapp

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.barteksc.pdfviewer.PDFView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.format.DateUtils
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

class DocumentDetailActivity : AppCompatActivity() {

    private lateinit var pdfView: PDFView
    private lateinit var progressBarPdf: ProgressBar
    private lateinit var tvDetailTitle: TextView
    private lateinit var tvDetailCourse: TextView
    private lateinit var tvDetailUploader: TextView
    private lateinit var tvUploaderInitial: TextView
    private lateinit var cgMetadataTags: ChipGroup
    private lateinit var btnBack: ImageView
    private lateinit var btnBookmark: ImageView
    private lateinit var btnDownload: MaterialButton
    private lateinit var btnUpvote: MaterialButton
    private lateinit var btnComment: MaterialButton

    private var documentId: String? = null
    private var documentUrl: String? = null
    private var isBookmarked = false

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_detail)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        pdfView = findViewById(R.id.pdfView)
        progressBarPdf = findViewById(R.id.progressBarPdf)
        tvDetailTitle = findViewById(R.id.tvDetailTitle)
        tvDetailCourse = findViewById(R.id.tvDetailCourse)
        tvDetailUploader = findViewById(R.id.tvDetailUploader)
        tvUploaderInitial = findViewById(R.id.tvUploaderInitial)
        cgMetadataTags = findViewById(R.id.cgMetadataTags)
        btnBack = findViewById(R.id.btnBack)
        btnBookmark = findViewById(R.id.btnBookmarkTop)
        btnDownload = findViewById(R.id.btnDownload)
        btnUpvote = findViewById(R.id.btnUpvote)
        btnComment = findViewById(R.id.btnComment)

        documentId = intent.getStringExtra("DOCUMENT_ID")
        documentUrl = intent.getStringExtra("DOCUMENT_URL")

        documentId?.let { docId ->
            db.collection("documents").document(docId).get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) return@addOnSuccessListener
                val doc = snapshot.toObject(Document::class.java) ?: return@addOnSuccessListener

                tvDetailTitle.text = doc.title

                val dateStr = doc.timestamp?.toDate()?.let {
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it)
                } ?: ""
                tvDetailCourse.text = "${doc.courseName} (${doc.courseCode}) • Uploaded: $dateStr"

                val uploader = doc.uploaderName ?: "Anonymous"
                tvDetailUploader.text = "Uploaded by $uploader"
                tvUploaderInitial.text = uploader.firstOrNull()?.uppercaseChar()?.toString() ?: "A"

                cgMetadataTags.removeAllViews()
                doc.tags?.forEach { tag ->
                    if (tag.isNullOrEmpty() || tag == "N/A") return@forEach
                    val chip = Chip(this).apply {
                        text = tag
                        setTextColor(Color.parseColor("#4CA6FF"))
                        chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                        chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#4CA6FF"))
                        chipStrokeWidth = 2f
                        isClickable = false
                    }
                    cgMetadataTags.addView(chip)
                }
            }
        }

        btnBack.setOnClickListener { finish() }

        btnDownload.setOnClickListener {
            val url = documentUrl ?: return@setOnClickListener
            Toast.makeText(this, "Downloading PDF...", Toast.LENGTH_SHORT).show()
            val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(tvDetailTitle.text.toString())
                setDescription("Downloading file from The Vault")
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "${System.currentTimeMillis()}.pdf")
            }
            (getSystemService(DOWNLOAD_SERVICE) as? android.app.DownloadManager)?.enqueue(request)
        }

        val currentUser = auth.currentUser
        if (currentUser != null && documentId != null) {
            val uid = currentUser.uid
            db.collection("users").document(uid).collection("bookmarks").document(documentId!!)
                .get().addOnSuccessListener { doc ->
                    isBookmarked = doc.exists()
                    updateBookmarkIcon()
                }

            btnBookmark.setOnClickListener {
                isBookmarked = !isBookmarked
                updateBookmarkIcon()
                if (isBookmarked) {
                    db.collection("users").document(uid).collection("bookmarks")
                        .document(documentId!!).set(mapOf("timestamp" to Timestamp.now()))
                    Toast.makeText(this, "Saved to Bookmarks", Toast.LENGTH_SHORT).show()
                } else {
                    db.collection("users").document(uid).collection("bookmarks")
                        .document(documentId!!).delete()
                    Toast.makeText(this, "Removed from Bookmarks", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val prefs = getSharedPreferences("GateKeptPrefs", MODE_PRIVATE)
        val hasUpvoted = prefs.getBoolean("upvoted_$documentId", false)
        if (hasUpvoted) {
            btnUpvote.isEnabled = false
            btnUpvote.text = "Upvoted"
        }

        btnUpvote.setOnClickListener {
            val docId = documentId ?: return@setOnClickListener
            btnUpvote.isEnabled = false
            btnUpvote.text = "Upvoting..."
            db.collection("documents").document(docId)
                .update("upvotes", FieldValue.increment(1))
                .addOnSuccessListener {
                    prefs.edit().putBoolean("upvoted_$docId", true).apply()
                    btnUpvote.text = "Upvoted"
                }
                .addOnFailureListener {
                    btnUpvote.isEnabled = true
                    btnUpvote.text = "Upvote"
                }
        }

        btnComment.setOnClickListener { showCommentsBottomSheet() }

        documentUrl?.takeIf { it.isNotEmpty() }?.let { loadPdfFromUrl(it) }
    }

    private fun updateBookmarkIcon() {
        if (isBookmarked) {
            btnBookmark.setImageResource(R.drawable.ic_bookmark_filled)
            btnBookmark.setColorFilter(Color.parseColor("#FFD700"))
        } else {
            btnBookmark.setImageResource(R.drawable.ic_bookmark)
            btnBookmark.setColorFilter(Color.parseColor("#8B9EB7"))
        }
    }

    private fun showCommentsBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_comments, null)
        bottomSheetDialog.setContentView(sheetView)

        val rvComments = sheetView.findViewById<RecyclerView>(R.id.rvComments)
        val etNewComment = sheetView.findViewById<EditText>(R.id.etNewComment)
        val btnSendComment = sheetView.findViewById<ImageButton>(R.id.btnSendComment)

        rvComments.layoutManager = LinearLayoutManager(this)
        val commentList = mutableListOf<Comment>()
        val adapter = CommentAdapter(commentList)
        rvComments.adapter = adapter

        val docId = documentId ?: return

        db.collection("documents").document(docId).collection("comments")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { value, error ->
                if (error != null || value == null) return@addSnapshotListener
                commentList.clear()
                for (doc in value) {
                    val comment = doc.toObject(Comment::class.java).copy(id = doc.id)
                    commentList.add(comment)
                }
                adapter.notifyDataSetChanged()
                if (commentList.isNotEmpty()) rvComments.smoothScrollToPosition(commentList.size - 1)
            }

        btnSendComment.setOnClickListener {
            val text = etNewComment.text.toString().trim()
            val user = auth.currentUser
            if (text.isEmpty() || user == null) return@setOnClickListener

            val author = user.displayName ?: "Anonymous"
            val commentData = mapOf(
                "authorName" to author,
                "text" to text,
                "upvotes" to 0,
                "timestamp" to Timestamp.now()
            )
            db.collection("documents").document(docId).collection("comments")
                .add(commentData)
                .addOnSuccessListener { etNewComment.setText("") }
        }

        bottomSheetDialog.show()
    }

    private fun loadPdfFromUrl(pdfUrl: String) {
        Thread {
            try {
                val url = URL(pdfUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                val inputStream = BufferedInputStream(connection.inputStream)

                runOnUiThread {
                    progressBarPdf.visibility = View.GONE
                    pdfView.fromStream(inputStream)
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .defaultPage(0)
                        .spacing(4)
                        .load()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progressBarPdf.visibility = View.GONE
                    Toast.makeText(this, "Failed to load PDF.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // Inner adapter for comments
    private class CommentAdapter(private val comments: List<Comment>) :
        RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
            return CommentViewHolder(view)
        }

        override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
            val comment = comments[position]
            holder.tvAuthor.text = comment.authorName
            holder.tvText.text = comment.text

            comment.timestamp?.toDate()?.time?.let { time ->
                val timeAgo = DateUtils.getRelativeTimeSpanString(
                    time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                ).toString()
                holder.tvTime?.text = timeAgo
            }

            val initials = comment.authorName?.let { name ->
                val parts = name.split(" ")
                if (parts.size >= 2) "${parts[0][0].uppercaseChar()}${parts[1][0].uppercaseChar()}"
                else parts[0][0].uppercaseChar().toString()
            } ?: "U"
            holder.tvInitials.text = initials
        }

        override fun getItemCount(): Int = comments.size

        class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvAuthor: TextView = itemView.findViewById(R.id.tvCommentAuthor)
            val tvText: TextView = itemView.findViewById(R.id.tvCommentText)
            val tvInitials: TextView = itemView.findViewById(R.id.tvInitials)
            val tvTime: TextView? = itemView.findViewById(R.id.tvCommentTime)
        }
    }
}
