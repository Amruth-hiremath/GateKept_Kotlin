package com.gatekept.kotlinapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.amazonaws.HttpMethod
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.S3ClientOptions
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.xml.KonfettiView
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

class UploadFragment : Fragment() {

    companion object {
        private const val BUCKET_NAME = "gatekept"
    }

    private lateinit var cardDropZone: MaterialCardView
    private lateinit var tvDropZoneText: TextView
    private lateinit var etCourseName: TextInputEditText
    private lateinit var etCourseCode: TextInputEditText
    private lateinit var etCustomTitle: TextInputEditText
    private lateinit var chipGroupMaterial: ChipGroup
    private lateinit var dropdownSchool: AutoCompleteTextView
    private lateinit var dropdownProgram: AutoCompleteTextView
    private lateinit var dropdownYear: AutoCompleteTextView
    private lateinit var dropdownExam: AutoCompleteTextView
    private lateinit var dropdownPaperYear: AutoCompleteTextView
    private lateinit var btnPublish: MaterialButton
    private lateinit var progressBarUpload: ProgressBar
    private lateinit var konfettiView: KonfettiView

    private var selectedFileUri: Uri? = null
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var s3Client: AmazonS3Client

    private var activeBountyId: String? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            selectedFileUri = result.data!!.data
            if (selectedFileUri != null) {
                tvDropZoneText.text = getFileName(selectedFileUri!!)
                tvDropZoneText.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_upload, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

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

        cardDropZone = view.findViewById(R.id.cardDropZone)
        tvDropZoneText = view.findViewById(R.id.tvDropZoneText)
        etCourseName = view.findViewById(R.id.etCourseName)
        etCourseCode = view.findViewById(R.id.etCourseCode)
        etCustomTitle = view.findViewById(R.id.etCustomTitle)
        chipGroupMaterial = view.findViewById(R.id.chipGroupMaterial)
        dropdownSchool = view.findViewById(R.id.dropdownSchool)
        dropdownProgram = view.findViewById(R.id.dropdownProgram)
        dropdownYear = view.findViewById(R.id.dropdownYear)
        dropdownExam = view.findViewById(R.id.dropdownExam)
        dropdownPaperYear = view.findViewById(R.id.dropdownPaperYear)
        btnPublish = view.findViewById(R.id.btnPublish)
        progressBarUpload = view.findViewById(R.id.progressBarUpload)
        konfettiView = view.findViewById(R.id.konfettiView)

        setupDropdowns()

        cardDropZone.setOnClickListener {
            filePickerLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/pdf" })
        }

        btnPublish.setOnClickListener { validateAndUpload() }
    }

    override fun onResume() {
        super.onResume()
        val prefs = requireContext().getSharedPreferences("BountyPrefs", Context.MODE_PRIVATE)
        val pendingBountyTopic = prefs.getString("BOUNTY_TOPIC", null)
        val pendingBountyId = prefs.getString("BOUNTY_ID", null)

        if (pendingBountyTopic != null && pendingBountyId != null) {
            etCustomTitle.setText(pendingBountyTopic)
            activeBountyId = pendingBountyId
            Toast.makeText(context, "Fulfilling Bounty: $pendingBountyTopic", Toast.LENGTH_SHORT).show()
            prefs.edit().clear().apply()
        }
    }

    private fun setupDropdowns() {
        val ctx = context ?: return

        val schools = arrayOf("SoCSE", "SoL", "SoB", "SoDI", "SoFMCA", "SoEPP", "SoLAS", "SoAHP", "SCEPS")
        dropdownSchool.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, schools))

        dropdownSchool.setOnItemClickListener { parent, _, position, _ ->
            val selectedSchool = parent.getItemAtPosition(position) as String
            val programs = when (selectedSchool) {
                "SoCSE" -> arrayOf("BTech", "BTech AIML", "BTech Data Science", "BTech Cloud Computing", "BTech Cybersecurity", "BSc", "BCA", "MTech")
                "SoL"   -> arrayOf("BA LLB (Hons)", "BBA LLB (Hons)", "LLM IP & Tech Law", "LLM Corporate Law")
                "SoB"   -> arrayOf("BBA (Hons)", "BCom (Hons)", "MBA", "MCom")
                "SoDI"  -> arrayOf("BDes", "MDes")
                "SoFMCA"-> arrayOf("BA Media & Journalism", "BSc Filmmaking", "BSc Animation & VFX", "BA Acting")
                "SoEPP" -> arrayOf("BA (Hons) Economics", "MA Economics")
                "SoLAS" -> arrayOf("BA (Hons) Liberal Arts")
                "SoAHP" -> arrayOf("Healthcare Professions")
                "SCEPS" -> arrayOf("PG Diploma", "Certificate Program", "Executive Ed")
                else    -> arrayOf("General Program")
            }
            dropdownProgram.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, programs))
            dropdownProgram.setText("")
        }

        dropdownYear.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line,
            arrayOf("1st Year", "2nd Year", "3rd Year", "4th Year")))

        dropdownExam.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line,
            arrayOf("Notes", "CIE-1", "CIE-2", "CIE-3", "SEE")))

        val currentY = Calendar.getInstance().get(Calendar.YEAR)
        val paperYears = Array(6) { (currentY - it).toString() }
        dropdownPaperYear.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, paperYears))
    }

    private fun validateAndUpload() {
        val cName    = etCourseName.text?.toString()?.trim() ?: ""
        val cCode    = etCourseCode.text?.toString()?.trim() ?: ""
        val school   = dropdownSchool.text.toString()
        val program  = dropdownProgram.text.toString()
        val year     = dropdownYear.text.toString()
        val exam     = dropdownExam.text.toString()
        val paperYear = dropdownPaperYear.text.toString()

        if (selectedFileUri == null || cName.isEmpty() || cCode.isEmpty() ||
            school.isEmpty() || program.isEmpty() || year.isEmpty() || exam.isEmpty() || paperYear.isEmpty()) {
            Toast.makeText(context, "Please fill out all fields and select a file.", Toast.LENGTH_SHORT).show()
            return
        }
        uploadDocumentToR2(cName, cCode, school, program, year, exam, paperYear)
    }

    private fun uploadDocumentToR2(
        courseName: String, courseCode: String, school: String,
        program: String, academicYear: String, examType: String, paperYear: String
    ) {
        setUploadingState(true)

        val rawFileName = getFileName(selectedFileUri!!)
        val safeFileName = rawFileName?.replace(Regex("[^a-zA-Z0-9._-]"), "_") ?: "document.pdf"
        val uniqueFileName = "${System.currentTimeMillis()}_$safeFileName"

        Thread {
            try {
                // PHASE 2: AI TEXT EXTRACTION & EMBEDDING
                val documentEmbedding = try {

                    val extractedText =
                        PdfTextExtractor(requireContext())
                            .extractTextFromUri(selectedFileUri!!)

                    val semanticEmbedder =
                        SemanticEmbedder(requireContext())

                    semanticEmbedder.embedDocument(extractedText)

                } catch (e: Exception) {

                    android.util.Log.e(
                        "GateKeptAI",
                        "Embedding failed — continuing upload",
                        e
                    )

                    emptyList()
                }

                val fileBytes: ByteArray = requireContext().contentResolver
                    .openInputStream(selectedFileUri!!)
                    ?.use { inputStream ->
                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        var bytesRead: Int
                        while (inputStream.read(chunk).also { bytesRead = it } != -1) {
                            buffer.write(chunk, 0, bytesRead)
                        }
                        buffer.toByteArray()
                    } ?: throw Exception("Cannot open file stream")

                val expiration = Date(System.currentTimeMillis() + (1000L * 60 * 15))
                val urlRequest = GeneratePresignedUrlRequest(BUCKET_NAME, uniqueFileName)
                    .withMethod(HttpMethod.PUT)
                    .withExpiration(expiration)
                    .apply { contentType = "application/pdf" }

                val presignedUrl = s3Client.generatePresignedUrl(urlRequest)

                val connection = (presignedUrl.openConnection() as HttpURLConnection).apply {
                    doOutput = true
                    requestMethod = "PUT"
                    setRequestProperty("Content-Type", "application/pdf")
                    setFixedLengthStreamingMode(fileBytes.size)
                }

                connection.outputStream.use { it.write(fileBytes); it.flush() }

                if (connection.responseCode !in 200..299) {
                    throw Exception("HTTP ${connection.responseCode}")
                }

                val downloadUrl = "${BuildConfig.R2_PUBLIC_URL.trim()}/$uniqueFileName"
                requireActivity().runOnUiThread {
                    saveMetadataToFirestore(courseName, courseCode, school, program, academicYear, examType, paperYear, downloadUrl, documentEmbedding)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "Upload Error: ${e.message}", Toast.LENGTH_LONG).show()
                    setUploadingState(false)
                }
            }
        }.start()
    }

    private fun saveMetadataToFirestore(
        courseName: String, courseCode: String, school: String,
        program: String, academicYear: String, examType: String,
        paperYear: String, fileUrl: String, embedding: List<Float>
    ) {
        val category = if (chipGroupMaterial.checkedChipId == R.id.chipPyq) "PYQ" else "Notes"
        val generatedDescription = "$category for $courseName - $program $academicYear | $paperYear"

        val customTitle = etCustomTitle.text?.toString()?.trim() ?: ""
        val finalTitle = if (customTitle.isEmpty()) "$courseName $examType $paperYear" else customTitle

        val tags = listOf(school, program, academicYear, category, examType, courseCode.uppercase(), paperYear)

        val currentUser = auth.currentUser
        val uploaderName = currentUser?.displayName ?: "Anonymous"

        val newDoc = Document(
            title = finalTitle,
            description = generatedDescription,
            courseCode = courseCode.uppercase(),
            courseName = courseName,
            category = category,
            examType = examType,
            school = school,
            program = program,
            academicYear = academicYear,
            paperYear = paperYear,
            tags = tags,
            status = "PENDING",
            fileType = "PDF",
            fileUrl = fileUrl,
            uploaderName = uploaderName,
            upvotes = 0,
            timestamp = Timestamp.now(),
            uploaderUid = currentUser?.uid ?: "",
            embedding = embedding // THIS PASSES THE MATH TO FIRESTORE
        )

        db.collection("documents").add(newDoc).addOnSuccessListener {
            setUploadingState(false)

            activeBountyId?.let { bountyId ->
                db.collection("requests").document(bountyId).update("status", "FULFILLED")
                    .addOnSuccessListener {
                        Toast.makeText(context, "Bounty Fulfilled! +100 Bonus Rep!", Toast.LENGTH_LONG).show()
                        activeBountyId = null
                    }
            }

            currentUser?.let { user ->
                db.collection("notifications").add(mapOf(
                    "uid" to user.uid,
                    "title" to "Document in Review ⏳",
                    "message" to "Your upload '$finalTitle' has been sent to moderators for approval.",
                    "timestamp" to Timestamp.now(),
                    "isRead" to false
                ))
            }

            // Confetti
            val emitterConfig = Emitter(300L, TimeUnit.MILLISECONDS).max(300)
            konfettiView.start(
                nl.dionsegijn.konfetti.core.PartyFactory(emitterConfig)
                    .spread(360)
                    .shapes(listOf(Shape.Square, Shape.Circle))
                    .colors(listOf(0x4CA6FF, 0xFFD700, 0xFFFFFF))
                    .setSpeedBetween(0f, 15f)
                    .position(Position.Relative(0.0, 0.0).between(Position.Relative(1.0, 0.0)))
                    .build()
            )

            // Reset form
            etCourseName.setText("")
            etCourseCode.setText("")
            etCustomTitle.setText("")
            dropdownSchool.setText("")
            dropdownProgram.setText("")
            dropdownYear.setText("")
            dropdownExam.setText("")
            dropdownPaperYear.setText("")
            tvDropZoneText.text = "Tap to browse PDF"
            selectedFileUri = null

        }.addOnFailureListener { e ->
            setUploadingState(false)
            Toast.makeText(context, "DB Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setUploadingState(isUploading: Boolean) {
        btnPublish.text = if (isUploading) "Uploading..." else "CONFIRM UPLOAD"
        btnPublish.isEnabled = !isUploading
        progressBarUpload.visibility = if (isUploading) View.VISIBLE else View.GONE
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            result = result?.substringAfterLast('/')
        }
        return result
    }
}