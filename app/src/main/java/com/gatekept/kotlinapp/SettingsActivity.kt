package com.gatekept.kotlinapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot
import android.graphics.Color
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.view.View

class SettingsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private var currentUser: FirebaseUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        db = FirebaseFirestore.getInstance()
        currentUser = FirebaseAuth.getInstance().currentUser

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnMyBounties).setOnClickListener { showMyBountiesDialog() }
        findViewById<View>(R.id.btnRequestFeature).setOnClickListener { showFeatureRequestDialog() }
        findViewById<View>(R.id.btnFeedback).setOnClickListener { showFeedbackDialog() }
        findViewById<View>(R.id.btnPrivacy).setOnClickListener {
            showLegalDialog("Privacy Policy",
                "Your privacy is important to us...\n\n1. Data Collection\nWe only collect data necessary for the app to function.\n\n2. Document Hosting\nDocuments uploaded to GateKept remain your intellectual responsibility.\n\n(This is a native privacy policy placeholder.)")
        }
        findViewById<View>(R.id.btnTerms).setOnClickListener {
            showLegalDialog("Terms of Service",
                "By using GateKept, you agree to...\n\n1. Academic Integrity\nDo not upload copyrighted material without permission.\n\n2. Bounty System\nBounties are community-driven and hold no real-world monetary value.\n\n(This is a native terms of service placeholder.)")
        }
        findViewById<View>(R.id.btnHelpSupport).setOnClickListener {
            startActivity(Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:amrutheshchiremathbtech24@rvu.edu.in")
                putExtra(Intent.EXTRA_SUBJECT, "Help & Support for GateKept App")
            }.let { Intent.createChooser(it, "Send Email") })
        }
        findViewById<View>(R.id.btnLogout).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private fun showLegalDialog(titleStr: String, contentStr: String) {
        val dialog = BottomSheetDialog(this)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0B131F")) }

        root.addView(TextView(this).apply {
            text = "GateKept"; setTextColor(Color.parseColor("#0Affffff"))
            textSize = 64f; setTypeface(null, Typeface.BOLD); rotation = -30f
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(60, 60, 60, 60)
        }
        layout.addView(TextView(this).apply {
            text = titleStr; setTextColor(Color.WHITE); textSize = 24f
            setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, 40)
        })
        layout.addView(TextView(this).apply {
            text = contentStr; setTextColor(Color.parseColor("#8B9EB7"))
            textSize = 16f; setLineSpacing(0f, 1.2f)
        })

        root.addView(ScrollView(this).apply { addView(layout) })
        dialog.setContentView(root)
        (root.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.show()
    }

    private fun showFeedbackDialog() {
        val dialog = BottomSheetDialog(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B131F")); setPadding(60, 60, 60, 60)
        }

        layout.addView(TextView(this).apply { text = "Rate GateKept"; setTextColor(Color.WHITE); textSize = 20f; setTypeface(null, Typeface.BOLD) })

        val ratingBar = RatingBar(this).apply {
            numStars = 5; stepSize = 1f; progress = 5
            progressTintList = ColorStateList.valueOf(Color.parseColor("#FFD700"))
        }
        layout.addView(ratingBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, 40, 0, 40)
        })

        val etComment = EditText(this).apply {
            hint = "Tell us what you love or what we can improve..."
            setHintTextColor(Color.parseColor("#8B9EB7")); setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#121B2A")); setPadding(40, 40, 40, 40)
            gravity = Gravity.TOP or Gravity.START; minLines = 4
        }
        layout.addView(etComment)

        val btnSubmit = MaterialButton(this).apply {
            text = "SUBMIT FEEDBACK"
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CA6FF"))
            setTextColor(Color.parseColor("#0B131F"))
        }
        layout.addView(btnSubmit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 160).apply { setMargins(0, 40, 0, 0) })

        btnSubmit.setOnClickListener {
            val user = currentUser ?: return@setOnClickListener
            btnSubmit.isEnabled = false; btnSubmit.text = "SENDING..."
            db.collection("feedback").add(mapOf(
                "uid" to user.uid, "rating" to ratingBar.progress,
                "comment" to etComment.text.toString(), "timestamp" to Timestamp.now()
            )).addOnSuccessListener {
                Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.setContentView(layout)
        dialog.show()
    }

    private fun showFeatureRequestDialog() {
        val dialog = BottomSheetDialog(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B131F")); setPadding(60, 60, 60, 60)
        }

        layout.addView(TextView(this).apply {
            text = "Request a Feature"; setTextColor(Color.WHITE); textSize = 20f
            setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, 40)
        })

        val etFeatureName = EditText(this).apply {
            hint = "Feature Name (e.g. Dark Mode Toggle)"
            setHintTextColor(Color.parseColor("#8B9EB7")); setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#121B2A")); setPadding(40, 40, 40, 40)
        }
        layout.addView(etFeatureName)

        val etDetails = EditText(this).apply {
            hint = "How would this feature help you?"; setHintTextColor(Color.parseColor("#8B9EB7"))
            setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#121B2A"))
            setPadding(40, 40, 40, 40); gravity = Gravity.TOP or Gravity.START; minLines = 4
        }
        layout.addView(etDetails, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 20, 0, 0) })

        val btnSubmit = MaterialButton(this).apply {
            text = "SUBMIT REQUEST"
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CA6FF"))
            setTextColor(Color.parseColor("#0B131F"))
        }
        layout.addView(btnSubmit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 160).apply { setMargins(0, 40, 0, 0) })

        btnSubmit.setOnClickListener {
            val user = currentUser ?: return@setOnClickListener
            if (etFeatureName.text.toString().trim().isEmpty()) return@setOnClickListener
            btnSubmit.isEnabled = false; btnSubmit.text = "SENDING..."
            db.collection("feature_requests").add(mapOf(
                "uid" to user.uid, "featureName" to etFeatureName.text.toString().trim(),
                "details" to etDetails.text.toString().trim(), "timestamp" to Timestamp.now()
            )).addOnSuccessListener {
                Toast.makeText(this, "Feature request submitted!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.setContentView(layout)
        dialog.show()
    }

    private fun showMyBountiesDialog() {
        val user = currentUser ?: return
        val dialog = BottomSheetDialog(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B131F")); setPadding(40, 40, 40, 40)
        }

        layout.addView(TextView(this).apply {
            text = "My Active Bounties"; setTextColor(Color.WHITE); textSize = 20f
            setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, 40)
        })

        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(ScrollView(this).apply { addView(listContainer) })

        dialog.setContentView(layout)
        dialog.show()

        db.collection("requests")
            .whereEqualTo("requesterUid", user.uid)
            .whereEqualTo("status", "OPEN")
            .get()
            .addOnSuccessListener { snapshots ->
                if (snapshots.isEmpty) {
                    listContainer.addView(TextView(this).apply {
                        text = "You have no active bounties."
                        setTextColor(Color.parseColor("#8B9EB7"))
                    })
                    return@addOnSuccessListener
                }

                for (snap in snapshots) {
                    val req = snap.toObject(DocumentRequest::class.java).copy(id = snap.id)
                    val card = MaterialCardView(this).apply {
                        setCardBackgroundColor(Color.parseColor("#121B2A"))
                        radius = 24f; strokeColor = Color.parseColor("#1E2A3C"); strokeWidth = 2
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) }
                    }
                    val cardLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40)
                    }
                    cardLayout.addView(TextView(this).apply {
                        text = req.requestedTopic; setTextColor(Color.WHITE)
                        textSize = 16f; setTypeface(null, Typeface.BOLD)
                    })
                    cardLayout.addView(TextView(this).apply {
                        text = req.details; setTextColor(Color.parseColor("#8B9EB7")); setPadding(0, 10, 0, 30)
                    })

                    val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

                    val btnEdit = MaterialButton(this).apply {
                        text = "Edit"; backgroundTintList = ColorStateList.valueOf(Color.parseColor("#222F43"))
                        setTextColor(Color.parseColor("#4CA6FF"))
                    }
                    btnRow.addView(btnEdit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 10, 0) })

                    val btnDelete = MaterialButton(this).apply {
                        text = "Delete"; backgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FF6B6B"))
                        setTextColor(Color.parseColor("#FF6B6B"))
                    }
                    btnRow.addView(btnDelete, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(10, 0, 0, 0) })

                    cardLayout.addView(btnRow)
                    card.addView(cardLayout)
                    listContainer.addView(card)

                    btnDelete.setOnClickListener {
                        db.collection("requests").document(req.id!!).delete().addOnSuccessListener {
                            listContainer.removeView(card)
                            Toast.makeText(this, "Bounty Deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                    btnEdit.setOnClickListener { dialog.dismiss(); showEditBountyDialog(req) }
                }
            }
    }

    private fun showEditBountyDialog(req: DocumentRequest) {
        val dialog = BottomSheetDialog(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B131F")); setPadding(60, 60, 60, 60)
        }

        layout.addView(TextView(this).apply {
            text = "Edit Bounty"; setTextColor(Color.WHITE); textSize = 20f
            setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, 40)
        })

        val etTopic = EditText(this).apply {
            setText(req.requestedTopic); setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#121B2A")); setPadding(40, 40, 40, 40)
        }
        layout.addView(etTopic)

        val etDetails = EditText(this).apply {
            setText(req.details); setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#121B2A")); setPadding(40, 40, 40, 40)
            minLines = 3; gravity = android.view.Gravity.TOP or android.view.Gravity.START
        }
        layout.addView(etDetails, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 20, 0, 0) })

        val btnUpdate = MaterialButton(this).apply {
            text = "SAVE CHANGES"
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CA6FF"))
            setTextColor(Color.parseColor("#0B131F"))
        }
        layout.addView(btnUpdate, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 160).apply { setMargins(0, 40, 0, 0) })

        btnUpdate.setOnClickListener {
            btnUpdate.isEnabled = false; btnUpdate.text = "SAVING..."
            db.collection("requests").document(req.id!!)
                .update("requestedTopic", etTopic.text.toString().trim(), "details", etDetails.text.toString().trim())
                .addOnSuccessListener {
                    Toast.makeText(this, "Bounty Updated!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    showMyBountiesDialog()
                }
        }

        dialog.setContentView(layout)
        dialog.show()
    }
}
