import java.util.Properties
import java.io.FileInputStream
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics") // Hardcoded the ID instead of using the alias
}

android {
    namespace = "com.gatekept.kotlinapp"
    compileSdk = 36

    androidResources {
        noCompress += "tflite"
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.gatekept.kotlinapp"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Reading local.properties for R2 Cloudflare Keys in Kotlin DSL
        val properties = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(FileInputStream(localPropertiesFile))
        }

        buildConfigField("String", "R2_ACCESS_KEY", "\"${properties.getProperty("R2_ACCESS_KEY", "MISSING")}\"")
        buildConfigField("String", "R2_SECRET_KEY", "\"${properties.getProperty("R2_SECRET_KEY", "MISSING")}\"")
        buildConfigField("String", "R2_ENDPOINT", "\"${properties.getProperty("R2_ENDPOINT", "MISSING")}\"")
        buildConfigField("String", "R2_PUBLIC_URL", "\"${properties.getProperty("R2_PUBLIC_URL", "MISSING")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Note: If this throws an error because it's not in your new TOML file,
    // comment it out and use this instead: implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-crashlytics")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Firebase Core, Auth, & Firestore
    implementation(platform("com.google.firebase:firebase-bom:34.9.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-auth:21.5.1")
    implementation("com.google.firebase:firebase-firestore")

    // Cloudflare R2 (via AWS S3 SDK)
    implementation("com.amazonaws:aws-android-sdk-s3:2.22.0")

    // Native In-App PDF Viewer
    implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.1")

    // Confetti Animations
    implementation("nl.dionsegijn:konfetti-xml:2.0.2")

    // Core TFLite
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    // The magic wrapper that handles Tokenization and Embedding automatically
    implementation("com.google.mediapipe:tasks-text:latest.release")
    //PDF parsing
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    //GSON
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}