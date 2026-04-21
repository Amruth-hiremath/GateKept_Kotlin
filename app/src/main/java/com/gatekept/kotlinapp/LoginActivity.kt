package com.gatekept.kotlinapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GoogleAuth"
    }

    private lateinit var mAuth: FirebaseAuth
    private lateinit var mGoogleSignInClient: GoogleSignInClient

    private lateinit var progressBar: ProgressBar
    private lateinit var btnGoogleSignIn: MaterialButton
    private lateinit var btnEmailSignIn: MaterialButton
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d(TAG, "firebaseAuthWithGoogle: ${account.id}")
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w(TAG, "Google sign in failed", e)
                Toast.makeText(this, "Google sign in failed.", Toast.LENGTH_SHORT).show()
                resetLoginUI()
            }
        } else {
            resetLoginUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        progressBar = findViewById(R.id.progressBar)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        btnEmailSignIn = findViewById(R.id.btnEmailSignIn)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)

        mAuth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        btnGoogleSignIn.setOnClickListener { signIn() }
        btnEmailSignIn.setOnClickListener { signInWithEmail() }
    }

    private fun signInWithEmail() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter your email and password.", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnGoogleSignIn.isEnabled = false
        btnEmailSignIn.isEnabled = false

        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    routeUser()
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(this, "Authentication failed.", Toast.LENGTH_LONG).show()
                    resetLoginUI()
                }
            }
    }

    private fun signIn() {
        progressBar.visibility = View.VISIBLE
        btnGoogleSignIn.isEnabled = false
        btnEmailSignIn.isEnabled = false
        googleSignInLauncher.launch(mGoogleSignInClient.signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    routeUser()
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
                    resetLoginUI()
                }
            }
    }

    private fun resetLoginUI() {
        progressBar.visibility = View.GONE
        btnGoogleSignIn.isEnabled = true
        btnEmailSignIn.isEnabled = true
    }

    private fun routeUser() {
        val user = mAuth.currentUser ?: return
        val email = user.email
        val db = FirebaseFirestore.getInstance()

        val userMap = mapOf(
            "uid" to user.uid,
            "name" to (user.displayName ?: "Student"),
            "email" to email
        )

        db.collection("users").document(user.uid).set(userMap, SetOptions.merge())
            .addOnSuccessListener {
                val intent = if (email != null && email.endsWith("@gatekept.admin.edu")) {
                    Intent(this, ModeratorDashboardActivity::class.java)
                } else {
                    Intent(this, MainActivity::class.java)
                }
                startActivity(intent)
                finish()
            }
    }
}
