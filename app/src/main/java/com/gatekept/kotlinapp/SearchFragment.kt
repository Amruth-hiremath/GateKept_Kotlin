// Replace your current SearchFragment.kt with this version

package com.gatekept.kotlinapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.firestore.ListenerRegistration

class SearchFragment : Fragment() {

    private lateinit var etSearchQuery: EditText
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var dropdownSort: AutoCompleteTextView
    private lateinit var dropdownFilterCategory: AutoCompleteTextView
    private lateinit var dropdownFilterSchool: AutoCompleteTextView
    private lateinit var dropdownFilterProgram: AutoCompleteTextView
    private lateinit var dropdownFilterYear: AutoCompleteTextView
    private lateinit var dropdownFilterExam: AutoCompleteTextView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var btnPostBounty: MaterialButton

    private lateinit var adapter: DocumentAdapter
    private val allDocuments = mutableListOf<Document>()
    private val filteredDocuments = mutableListOf<Document>()

    private var currentSort = "Newest First"
    private var currentCategory = "All Types"
    private var currentSchool = "All Schools"
    private var currentProgram = "All Programs"
    private var currentYear = "All Years"
    private var currentExam = "All Exams"

    private lateinit var db: FirebaseFirestore
    private lateinit var semanticEmbedder: SemanticEmbedder
    private lateinit var searchRepository: SemanticSearchRepository

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    // Track Firestore listener to remove it when view is destroyed
    private var firestoreListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etSearchQuery = view.findViewById(R.id.etSearchQuery)
        rvSearchResults = view.findViewById(R.id.rvSearchResults)
        dropdownSort = view.findViewById(R.id.dropdownSort)
        dropdownFilterCategory = view.findViewById(R.id.dropdownFilterCategory)
        dropdownFilterSchool = view.findViewById(R.id.dropdownFilterSchool)
        dropdownFilterProgram = view.findViewById(R.id.dropdownFilterProgram)
        dropdownFilterYear = view.findViewById(R.id.dropdownFilterYear)
        dropdownFilterExam = view.findViewById(R.id.dropdownFilterExam)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        btnPostBounty = view.findViewById(R.id.btnPostBounty)

        btnPostBounty.setOnClickListener { showBountyBottomSheet() }

        rvSearchResults.layoutManager = LinearLayoutManager(context)
        adapter = DocumentAdapter(filteredDocuments) { document ->
            document.fileUrl?.takeIf { it.isNotEmpty() }?.let {
                startActivity(Intent(context, DocumentDetailActivity::class.java).apply {
                    putExtra("DOCUMENT_ID", document.id)
                    putExtra("DOCUMENT_URL", document.fileUrl)
                })
            }
        }
        rvSearchResults.adapter = adapter

        setupDropdowns()

        semanticEmbedder = SemanticEmbedder(requireContext())
        searchRepository = SemanticSearchRepository(
            firestore = db,
            cache = EmbeddingCache(requireContext()),
            sharedPrefs = requireContext().getSharedPreferences("GateKeptSearch", Context.MODE_PRIVATE)
        )

        // Warm up cache
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) { searchRepository.initializeSearch() }
        }

        etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                Log.d("GateKeptSearch", "Text changed: '$s'")
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable { runSearch() }
                searchHandler.postDelayed(searchRunnable!!, 400)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Initial chronological feed
        fetchDocuments()
    }

    private fun setupDropdowns() {
        // ... (no changes, same as your existing code)
        val ctx = context ?: return

        val sorts = arrayOf("Newest First", "Oldest First", "Most Upvotes", "Least Upvotes", "A to Z", "Z to A")
        dropdownSort.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, sorts))
        dropdownSort.setText(currentSort, false)
        dropdownSort.setOnItemClickListener { parent, _, pos, _ ->
            currentSort = parent.getItemAtPosition(pos) as String; applyFiltersAndSort()
        }

        val categories = arrayOf("All Types", "Notes", "PYQ")
        dropdownFilterCategory.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, categories))
        dropdownFilterCategory.setText(currentCategory, false)
        dropdownFilterCategory.setOnItemClickListener { parent, _, pos, _ ->
            currentCategory = parent.getItemAtPosition(pos) as String; applyFiltersAndSort()
        }

        val schools = arrayOf("All Schools", "SoCSE", "SoL", "SoB", "SoDI", "SoFMCA", "SoEPP", "SoLAS", "SoAHP", "SCEPS")
        dropdownFilterSchool.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, schools))
        dropdownFilterSchool.setText(currentSchool, false)
        dropdownFilterSchool.setOnItemClickListener { parent, _, pos, _ ->
            currentSchool = parent.getItemAtPosition(pos) as String
            val programs = when (currentSchool) {
                "SoCSE" -> arrayOf("All Programs", "BTech", "BTech AIML", "BTech Data Science", "BTech Cloud Computing", "BTech Cybersecurity", "BSc", "BCA", "MTech")
                "SoB" -> arrayOf("All Programs", "BBA (Hons)", "BCom (Hons)", "MBA", "MCom")
                else -> arrayOf("All Programs")
            }
            dropdownFilterProgram.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, programs))
            currentProgram = "All Programs"
            dropdownFilterProgram.setText(currentProgram, false)
            applyFiltersAndSort()
        }

        dropdownFilterProgram.setText(currentProgram, false)
        dropdownFilterProgram.setOnItemClickListener { parent, _, pos, _ ->
            currentProgram = parent.getItemAtPosition(pos) as String; applyFiltersAndSort()
        }

        val years = arrayOf("All Years", "1st Year", "2nd Year", "3rd Year", "4th Year", "5th Year")
        dropdownFilterYear.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, years))
        dropdownFilterYear.setText(currentYear, false)
        dropdownFilterYear.setOnItemClickListener { parent, _, pos, _ ->
            currentYear = parent.getItemAtPosition(pos) as String; applyFiltersAndSort()
        }

        val exams = arrayOf("All Exams", "Notes", "CIE-1", "CIE-2", "CIE-3", "SEE")
        dropdownFilterExam.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, exams))
        dropdownFilterExam.setText(currentExam, false)
        dropdownFilterExam.setOnItemClickListener { parent, _, pos, _ ->
            currentExam = parent.getItemAtPosition(pos) as String; applyFiltersAndSort()
        }
    }

    private fun applyFiltersAndSort() {
        filteredDocuments.clear()
        val query = etSearchQuery.text.toString().lowercase()

        allDocuments.forEach { doc ->
            val matchesSearch = query.isBlank() ||
                    doc.title?.contains(query, ignoreCase = true) == true ||
                    doc.courseCode?.contains(query, ignoreCase = true) == true

            val matchesCategory = currentCategory == "All Types" || doc.category?.equals(currentCategory, ignoreCase = true) == true
            val matchesSchool = currentSchool == "All Schools" || doc.school?.equals(currentSchool, ignoreCase = true) == true
            val matchesProgram = currentProgram == "All Programs" || doc.program?.equals(currentProgram, ignoreCase = true) == true
            val matchesYear = currentYear == "All Years" || doc.academicYear?.equals(currentYear, ignoreCase = true) == true
            val matchesExam = currentExam == "All Exams" || doc.examType?.equals(currentExam, ignoreCase = true) == true

            if (matchesSearch && matchesCategory && matchesSchool && matchesProgram && matchesYear && matchesExam) {
                filteredDocuments.add(doc)
            }
        }

        filteredDocuments.sortWith(Comparator { d1, d2 ->
            when (currentSort) {
                "Oldest First" -> d1.timestamp?.compareTo(d2.timestamp!!) ?: 0
                "Most Upvotes" -> d2.upvotes.compareTo(d1.upvotes)
                "Least Upvotes" -> d1.upvotes.compareTo(d2.upvotes)
                "A to Z" -> d1.title?.compareTo(d2.title ?: "", ignoreCase = true) ?: 0
                "Z to A" -> d2.title?.compareTo(d1.title ?: "", ignoreCase = true) ?: 0
                else -> d2.timestamp?.compareTo(d1.timestamp!!) ?: 0
            }
        })

        adapter.notifyDataSetChanged()
        updateEmptyStateVisibility()
    }

    private fun updateEmptyStateVisibility() {
        if (filteredDocuments.isEmpty()) {
            rvSearchResults.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        } else {
            rvSearchResults.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
        }
    }

    private fun showBountyBottomSheet() { /* ... unchanged ... */ }

    private fun fetchDocuments() {
        // Remove previous listener to avoid stacking
        firestoreListener?.remove()

        firestoreListener = db.collection("documents")
            .whereEqualTo("status", "APPROVED")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null || !isAdded) return@addSnapshotListener
                allDocuments.clear()
                for (snap in snapshots) {
                    val doc = snap.toObject(Document::class.java).copy(id = snap.id)
                    allDocuments.add(doc)
                }
                applyFiltersAndSort()
            }
    }

    private fun runSearch() {
        val query = etSearchQuery.text.toString().trim()
        Log.d("GateKeptSearch", "runSearch called with query='$query'")
        if (query.isBlank()) {
            fetchDocuments()   // back to chronological feed
            return
        }

        // Always use hybrid search (semantic + exact boosts)
        Log.d("GateKeptSearch", "Using hybrid search")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val orderedIds = withContext(Dispatchers.IO) {
                    val queryVector = semanticEmbedder.embedQuery(query)
                    if (queryVector.isEmpty()) {
                        Log.w("GateKeptSearch", "Empty query vector → fallback to exact")
                        return@withContext emptyList<String>()
                    }
                    val results = searchRepository.search(queryVector, query)
                    Log.d("GateKeptSearch", "Hybrid search returned ${results.size} docs")
                    results.map { it.id }
                }

                if (orderedIds.isNotEmpty()) {
                    loadDocumentsByIds(orderedIds)
                } else {
                    // Nothing found → revert to simple filtering (safety net)
                    applyFiltersAndSort()
                }
            } catch (e: Exception) {
                Log.e("GateKeptSearch", "Hybrid search failed", e)
                applyFiltersAndSort()
            }
        }
    }

    private fun loadDocumentsByIds(ids: List<String>) {
        if (ids.isEmpty()) {
            allDocuments.clear()
            applyFiltersAndSort()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val docs = mutableListOf<Document>()
            for (id in ids) {
                try {
                    val snap = db.collection("documents").document(id).get().await()
                    if (snap.exists()) {
                        snap.toObject(Document::class.java)
                            ?.copy(id = snap.id)
                            ?.let { docs.add(it) }
                    }
                } catch (_: Exception) { }
            }

            // Apply filters without reordering
            val filtered = docs.filter { doc ->
                (currentCategory == "All Types" || doc.category?.equals(currentCategory, ignoreCase = true) == true) &&
                        (currentSchool == "All Schools" || doc.school?.equals(currentSchool, ignoreCase = true) == true) &&
                        (currentProgram == "All Programs" || doc.program?.equals(currentProgram, ignoreCase = true) == true) &&
                        (currentYear == "All Years" || doc.academicYear?.equals(currentYear, ignoreCase = true) == true) &&
                        (currentExam == "All Exams" || doc.examType?.equals(currentExam, ignoreCase = true) == true)
            }

            withContext(Dispatchers.Main) {
                allDocuments.clear()
                allDocuments.addAll(filtered)
                filteredDocuments.clear()
                filteredDocuments.addAll(filtered)
                adapter.notifyDataSetChanged()
                updateEmptyStateVisibility()
            }
        }
    }

    override fun onDestroyView() {
        firestoreListener?.remove()
        super.onDestroyView()
    }
}