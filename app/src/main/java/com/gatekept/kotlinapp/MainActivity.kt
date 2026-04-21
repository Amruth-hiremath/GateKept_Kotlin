package com.gatekept.kotlinapp

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    private lateinit var navHome: ImageView
    private lateinit var navSearch: ImageView
    private lateinit var navUpload: ImageView
    private lateinit var navSaved: ImageView
    private lateinit var navProfile: ImageView

    private var currentTab = "HOME"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        setContentView(R.layout.activity_main)

        navHome = findViewById(R.id.navHome)
        navSearch = findViewById(R.id.navSearch)
        navUpload = findViewById(R.id.navUpload)
        navSaved = findViewById(R.id.navSaved)
        navProfile = findViewById(R.id.navProfile)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTab != "HOME") {
                    resetSelection()
                    navHome.isSelected = true
                    currentTab = "HOME"
                    switchFragment(HomeFragment())
                } else {
                    finish()
                }
            }
        })

        navHome.setOnClickListener {
            resetSelection()
            navHome.isSelected = true
            currentTab = "HOME"
            switchFragment(HomeFragment())
        }

        navSearch.setOnClickListener {
            resetSelection()
            navSearch.isSelected = true
            currentTab = "SEARCH"
            switchFragment(SearchFragment())
        }

        navUpload.setOnClickListener {
            currentTab = "UPLOAD"
            switchFragment(UploadFragment())
        }

        navSaved.setOnClickListener {
            resetSelection()
            navSaved.isSelected = true
            currentTab = "SAVED"
            switchFragment(SavedFragment())
        }

        navProfile.setOnClickListener {
            resetSelection()
            navProfile.isSelected = true
            currentTab = "PROFILE"
            switchFragment(ProfileFragment())
        }

        if (savedInstanceState == null) {
            currentTab = "HOME"
            navHome.isSelected = true
            switchFragment(HomeFragment())
        }
    }

    private fun resetSelection() {
        navHome.isSelected = false
        navSearch.isSelected = false
        navSaved.isSelected = false
        navProfile.isSelected = false
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
