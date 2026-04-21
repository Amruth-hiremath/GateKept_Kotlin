package com.gatekept.kotlinapp

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot
import android.graphics.Color
import android.graphics.Typeface

class HomeFragment : Fragment(), SensorEventListener {

    private lateinit var tvUserName: TextView
    private lateinit var tvTotalDocs: TextView
    private lateinit var tvTotalUsers: TextView
    private lateinit var parallaxContainer: LinearLayout
    private lateinit var btnGoSearch: MaterialButton
    private lateinit var btnGoSaved: MaterialButton
    private lateinit var rvHomeTrending: RecyclerView
    private lateinit var rvLeaderboard: RecyclerView
    private lateinit var rvBounties: RecyclerView
    private lateinit var tvNoBounties: TextView
    private lateinit var notificationBadge: View

    private lateinit var adapter: DocumentAdapter
    private val trendingDocuments = mutableListOf<Document>()

    private lateinit var bountyAdapter: BountyAdapter
    private val bountyList = mutableListOf<DocumentRequest>()

    private lateinit var db: FirebaseFirestore
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null

    private val notificationList = mutableListOf<Map<String, Any>>()
    private val unreadNotificationIds = mutableListOf<String>()

    // --- Scholar Model ---
    data class Scholar(
        val name: String,
        var contributions: Int = 0,
        var upvotes: Int = 0,
        var reputation: Int = 0
    ) {
        fun calculateReputation() {
            reputation = if (contributions == 0 && upvotes == 0) 0
            else 100 + (contributions * 45) + (upvotes * 20) + (contributions * upvotes * 2)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()

        tvUserName = view.findViewById(R.id.tvUserName)
        tvTotalDocs = view.findViewById(R.id.tvTotalDocs)
        tvTotalUsers = view.findViewById(R.id.tvTotalUsers)
        parallaxContainer = view.findViewById(R.id.parallaxContainer)
        btnGoSearch = view.findViewById(R.id.btnGoSearch)
        btnGoSaved = view.findViewById(R.id.btnGoSaved)
        rvHomeTrending = view.findViewById(R.id.rvHomeTrending)
        rvLeaderboard = view.findViewById(R.id.rvLeaderboard)
        rvBounties = view.findViewById(R.id.rvBounties)
        tvNoBounties = view.findViewById(R.id.tvNoBounties)
        notificationBadge = view.findViewById(R.id.notificationBadge)

        view.findViewById<View>(R.id.btnNotifications).setOnClickListener { showNotificationsDialog() }
        fetchNotifications()

        rvLeaderboard.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvHomeTrending.layoutManager = LinearLayoutManager(context)
        rvBounties.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        rvHomeTrending.isNestedScrollingEnabled = false
        rvLeaderboard.isNestedScrollingEnabled = false
        rvBounties.isNestedScrollingEnabled = false

        bountyAdapter = BountyAdapter(bountyList) { bounty ->
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            if (bounty.requesterUid != null && bounty.requesterUid == currentUserId) {
                Toast.makeText(context, "You cannot fulfill your own bounty!", Toast.LENGTH_SHORT).show()
                return@BountyAdapter
            }

            requireContext().getSharedPreferences("BountyPrefs", Context.MODE_PRIVATE).edit()
                .putString("BOUNTY_ID", bounty.id)
                .putString("BOUNTY_TOPIC", bounty.requestedTopic)
                .apply()

            val uploadBtn = requireActivity().findViewById<View>(R.id.customNavbar)?.findViewById<View>(R.id.navUpload)
            uploadBtn?.performClick() ?: Toast.makeText(context, "Upload tab not found", Toast.LENGTH_SHORT).show()
        }
        rvBounties.adapter = bountyAdapter
        fetchOpenBounties()

        FirebaseAuth.getInstance().currentUser?.displayName?.split(" ")?.firstOrNull()?.let {
            tvUserName.text = "$it!"
        }

        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        // Stats + Leaderboard
        db.collection("documents").whereEqualTo("status", "APPROVED").get().addOnSuccessListener { snapshots ->
            if (!isAdded) return@addOnSuccessListener
            animateCounter(tvTotalDocs, snapshots.size())

            val scholarMap = mutableMapOf<String, Scholar>()
            for (doc in snapshots) {
                val uploader = doc.getString("uploaderName") ?: continue
                val upvotes = doc.getLong("upvotes") ?: 0L
                val scholar = scholarMap.getOrPut(uploader) { Scholar(uploader) }
                scholar.contributions++
                scholar.upvotes += upvotes.toInt()
            }

            val scholars = scholarMap.values.toMutableList()
            scholars.forEach { it.calculateReputation() }
            scholars.sortByDescending { it.reputation }

            rvLeaderboard.adapter = LeaderboardAdapter(scholars)
            animateCounter(tvTotalUsers, scholars.size + 15)
        }

        btnGoSearch.setOnClickListener {
            requireActivity().findViewById<View>(R.id.customNavbar)?.findViewById<View>(R.id.navSearch)?.performClick()
        }
        btnGoSaved.setOnClickListener {
            requireActivity().findViewById<View>(R.id.customNavbar)?.findViewById<View>(R.id.navSaved)?.performClick()
        }

        // Trending
        adapter = DocumentAdapter(trendingDocuments) { document ->
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
        rvHomeTrending.adapter = adapter

        db.collection("documents").whereEqualTo("status", "APPROVED").get()
            .addOnSuccessListener { snapshots ->
                if (!isAdded) return@addOnSuccessListener
                trendingDocuments.clear()
                for (docSnap in snapshots) {
                    val doc = docSnap.toObject(Document::class.java).copy(id = docSnap.id)
                    trendingDocuments.add(doc)
                }
                trendingDocuments.sortByDescending { it.upvotes }
                if (trendingDocuments.size > 5) trendingDocuments.subList(5, trendingDocuments.size).clear()
                adapter.notifyDataSetChanged()
            }
    }

    private fun fetchNotifications() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        db.collection("notifications").whereEqualTo("uid", user.uid)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null || !isAdded) return@addSnapshotListener

                notificationList.clear()
                unreadNotificationIds.clear()

                for (snap in snapshots) {
                    val notif = snap.data.toMutableMap()
                    notif["docId"] = snap.id
                    notificationList.add(notif)
                    if (notif["isRead"] == false) unreadNotificationIds.add(snap.id)
                }

                notificationList.sortByDescending { n ->
                    (n["timestamp"] as? com.google.firebase.Timestamp)?.seconds
                }

                notificationBadge.visibility = if (unreadNotificationIds.isEmpty()) View.GONE else View.VISIBLE
            }
    }

    private fun showNotificationsDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B131F"))
            setPadding(60, 60, 60, 60)
        }

        layout.addView(TextView(context).apply {
            text = "Inbox"; setTextColor(Color.WHITE); textSize = 24f
            setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, 40)
        })

        if (notificationList.isEmpty()) {
            layout.addView(TextView(context).apply {
                text = "You have no new notifications."
                setTextColor(Color.parseColor("#8B9EB7"))
            })
        } else {
            val listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            notificationList.forEach { notif ->
                val card = MaterialCardView(requireContext()).apply {
                    setCardBackgroundColor(Color.parseColor("#121B2A"))
                    radius = 24f; strokeColor = Color.parseColor("#1E2A3C"); strokeWidth = 2
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) }
                }
                val cardLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40)
                }
                cardLayout.addView(TextView(context).apply {
                    text = notif["title"] as? String ?: ""
                    setTextColor(Color.WHITE); textSize = 16f; setTypeface(null, Typeface.BOLD)
                })
                cardLayout.addView(TextView(context).apply {
                    text = notif["message"] as? String ?: ""
                    setTextColor(Color.parseColor("#8B9EB7")); setPadding(0, 16, 0, 0)
                })
                card.addView(cardLayout)
                listContainer.addView(card)
            }
            layout.addView(ScrollView(context).apply { addView(listContainer) })
        }

        dialog.setContentView(layout)
        (layout.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.show()

        unreadNotificationIds.forEach { docId ->
            db.collection("notifications").document(docId).update("isRead", true)
        }
        notificationBadge.visibility = View.GONE
    }

    private fun fetchOpenBounties() {
        db.collection("requests").whereEqualTo("status", "OPEN").limit(50)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null || !isAdded) return@addSnapshotListener

                bountyList.clear()
                val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
                val now = System.currentTimeMillis()

                for (snap in snapshots) {
                    val req = snap.toObject(DocumentRequest::class.java).copy(id = snap.id)
                    val reqTime = req.timestamp?.toDate()?.time ?: 0L
                    if (now - reqTime > thirtyDaysMs) {
                        db.collection("requests").document(req.id!!).delete()
                        continue
                    }
                    bountyList.add(req)
                }
                bountyList.sortByDescending { it.timestamp?.seconds }

                val header = view?.findViewById<View>(R.id.bountyHeaderLayout)
                if (bountyList.isEmpty()) {
                    header?.visibility = View.VISIBLE
                    rvBounties.visibility = View.GONE
                    tvNoBounties.visibility = View.VISIBLE
                } else {
                    header?.visibility = View.VISIBLE
                    rvBounties.visibility = View.VISIBLE
                    tvNoBounties.visibility = View.GONE
                }
                bountyAdapter.notifyDataSetChanged()
            }
    }

    private fun animateCounter(view: TextView, value: Int) {
        ValueAnimator.ofInt(0, value).apply {
            duration = 1500
            addUpdateListener { view.text = it.animatedValue.toString() }
            start()
        }
    }

    override fun onResume() {
        super.onResume()
        // Safely register the local rotationSensor variable
        sensorManager?.let { sm ->
            rotationSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
    }
    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            parallaxContainer.rotationX = event.values[0] * 25f
            parallaxContainer.rotationY = event.values[1] * -25f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    // --- Leaderboard Adapter ---
    private class LeaderboardAdapter(private val scholars: List<Scholar>) :
        RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_leaderboard, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val s = scholars[position]
            holder.tvName.text = s.name
            holder.tvScore.text = "${s.reputation} pts"
            holder.tvRank.text = "#${position + 1}"
            val parts = s.name.split(" ")
            val initials = if (parts.size >= 2) "${parts[0][0]}${parts[1][0]}" else s.name[0].toString()
            holder.tvInitials.text = initials.uppercase()
        }

        override fun getItemCount() = minOf(scholars.size, 10)

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvRank: TextView = v.findViewById(R.id.tvRank)
            val tvInitials: TextView = v.findViewById(R.id.tvInitials)
            val tvName: TextView = v.findViewById(R.id.tvScholarName)
            val tvScore: TextView = v.findViewById(R.id.tvScholarScore)
        }
    }

    // --- Bounty Adapter ---
    private class BountyAdapter(
        private val requests: List<DocumentRequest>,
        private val onClick: (DocumentRequest) -> Unit
    ) : RecyclerView.Adapter<BountyAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_bounty, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val req = requests[position]
            holder.tvRequestedTopic.text = req.requestedTopic
            if (!req.details.isNullOrEmpty()) {
                holder.tvBountyDetails.text = req.details
                holder.tvBountyDetails.visibility = View.VISIBLE
            } else {
                holder.tvBountyDetails.visibility = View.GONE
            }
            holder.tvRequesterName.text = "Requested by ${req.requesterName?.split(" ")?.firstOrNull() ?: ""}"
            holder.tvBountyPoints.text = "+${req.bountyPoints} Rep"
            holder.itemView.setOnClickListener { onClick(req) }
        }

        override fun getItemCount() = requests.size

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvRequestedTopic: TextView = v.findViewById(R.id.tvRequestedTopic)
            val tvBountyDetails: TextView = v.findViewById(R.id.tvBountyDetails)
            val tvRequesterName: TextView = v.findViewById(R.id.tvRequesterName)
            val tvBountyPoints: TextView = v.findViewById(R.id.tvBountyPoints)
        }
    }
}
