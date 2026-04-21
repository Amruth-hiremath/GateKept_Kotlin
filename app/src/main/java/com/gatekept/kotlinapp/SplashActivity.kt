package com.gatekept.kotlinapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser

        val intent = if (user != null) {
            val email = user.email
            if (email != null && email.endsWith("@gatekept.admin.edu")) {
                Intent(this, ModeratorDashboardActivity::class.java)
            } else {
                Intent(this, MainActivity::class.java)
            }
        } else {
            Intent(this, LoginActivity::class.java)
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(intent)
        finish()
    }
}
