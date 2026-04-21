package com.gatekept.kotlinapp

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class DocumentAdapter(
    private val documentList: List<Document>,
    private val listener: OnDocumentClickListener? = null
) : RecyclerView.Adapter<DocumentAdapter.DocumentViewHolder>() {

    fun interface OnDocumentClickListener {
        fun onDocumentClick(document: Document)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_document, parent, false)
        return DocumentViewHolder(view)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        val doc = documentList[position]

        val dateStr = doc.timestamp?.toDate()?.let {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it)
        } ?: "Unknown Date"

        if (doc.status == "PENDING") {
            holder.tvDocTitle.text = "⏳ [IN REVIEW] ${doc.title}"
            holder.tvDocTitle.setTextColor(Color.parseColor("#FFD700"))
        } else {
            holder.tvDocTitle.text = doc.title
            holder.tvDocTitle.setTextColor(Color.WHITE)
        }

        holder.tvCourseCode.text = "${doc.courseCode} • ${doc.category} • $dateStr"
        holder.tvUploader.text = doc.uploaderName
        holder.tvUpvotes.text = doc.upvotes.toString()

        val bgColors = arrayOf("#2A1E29", "#1A2634", "#29241B", "#1C2A24", "#2A1C24")
        val iconColors = arrayOf("#FF6B6B", "#4CA6FF", "#FFB03A", "#2ED573", "#E056FD")
        val hashIndex = doc.id?.let { Math.abs(it.hashCode()) % bgColors.size } ?: 0

        val bgShape = holder.itemView.findViewById<View>(R.id.iconBg).background.mutate() as GradientDrawable
        bgShape.setColor(Color.parseColor(bgColors[hashIndex]))

        holder.ivFileType.setImageResource(R.drawable.ic_pdf)
        holder.ivFileType.setColorFilter(Color.parseColor(iconColors[hashIndex]))

        holder.itemView.setOnClickListener {
            listener?.onDocumentClick(doc)
        }
    }

    override fun getItemCount(): Int = documentList.size

    class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDocTitle: TextView = itemView.findViewById(R.id.tvDocTitle)
        val tvCourseCode: TextView = itemView.findViewById(R.id.tvCourseCode)
        val tvUploader: TextView = itemView.findViewById(R.id.tvUploaderName)
        val tvUpvotes: TextView = itemView.findViewById(R.id.tvUpvoteCount)
        val ivFileType: ImageView = itemView.findViewById(R.id.ivFileType)
    }
}
