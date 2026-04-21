<h1 align="center">GateKept</h1>

<p align="center">
  <img src="https://drive.google.com/uc?export=view&id=1jWzDYLvz2OcBIvX-7o4S2jgf4pN7W8mF" 
       alt="GateKept Banner" 
       width="150"/>
</p>
<p align="center">
  <b>Secure • Serverless • Smart Document Vault</b>
</p>

GateKept is a native Android serverless application designed to act as a secure, centralized document vault for university campuses. It replaces fragmented file-sharing methods with a verified, gamified, and community-driven academic repository. 

---

## Table of Contents

* [Architecture Overview](#architecture-overview)
* [Core Features](#core-features)
* [Technology Stack](#technology-stack)
* [Project Structure](#project-structure)
* [Prerequisites](#prerequisites)
* [Local Setup & Installation](#local-setup--installation)
* [CI/CD Pipeline](#cicd-pipeline)
* [Contributing](#contributing)

---

## Architecture Overview

GateKept operates on a thick-client, serverless architecture:

1. **Blob Storage (AWS S3 / Cloudflare R2):** Handles the physical storage of PDF documents via a direct upload pipeline.
2. **NoSQL Database (Firebase Firestore):** Acts as the central nervous system, storing document metadata, user profiles, notifications, and campus bounties.
3. **Real-Time Synchronization:** Utilizes Firestore snapshot listeners to push state changes (e.g., a document moving from "PENDING" to "APPROVED") directly to the client without manual refreshing. 

---

## Core Features

### For Students

* **Smart Discovery:** Filter and search through verified course notes and past papers.
* **Campus Bounty Board:** Request specific academic materials and reward peers with reputation points for fulfilling them.
* **Scholar Leaderboard:** A real-time ranking system based on approved contributions and community upvotes.
* **In-App Notifications:** Receive alerts when an uploaded document is reviewed, approved, or rejected.
* **Personal Vault:** Bookmark essential documents and track personal upload history.

### For Moderators

* **Dedicated Admin HQ:** A secure routing pipeline separating standard users from administrators.
* **Moderation Queue:** Review pending document uploads before they are visible to the public campus feed.
* **One-Tap Actions:** Approve documents to push them live, or reject them with provided reasons (which triggers automated physical file deletion from the storage bucket). 

---

## Technology Stack

* **Platform:** Android (Java)
* **Minimum SDK:** 29
* **Target SDK:** 36
* **Identity:** Firebase Authentication (Google Sign-In & Email/Password)
* **Database:** Firebase Firestore
* **Storage:** Cloudflare R2 / AWS S3
* **Telemetry:** Firebase Crashlytics
* **UI Components:** Material Design 3, Konfetti (Animations), Android PDF Viewer 

---

## Project Structure

The codebase is currently organized in a simplified structure, with core components grouped under a single package.

```text
app/src/main/
├── java/com/gatekept/app/
│   ├── Comment.java
│   ├── Document.java
│   ├── DocumentAdapter.java
│   ├── DocumentDetailActivity.java
│   ├── DocumentRequest.java
│   ├── HomeFragment.java
│   ├── LoginActivity.java
│   ├── MainActivity.java
│   ├── ModeratorDashboardActivity.java
│   ├── ProfileFragment.java
│   ├── SavedFragment.java
│   ├── SearchFragment.java
│   ├── SettingsActivity.java
│   ├── SplashActivity.java
│   └── UploadFragment.java
│
├── res/
│   ├── color/
│   ├── drawable/
│   ├── font/
│   ├── layout/
│   ├── mipmap-*/
│   ├── values/
│   ├── values-night/
│   └── xml/
│
├── AndroidManifest.xml
│
├── test/                 # Unit tests
└── androidTest/          # Instrumented tests
```

---

## Prerequisites

To build and run this project locally, you will need:

* Android Studio (Latest stable version recommended)
* Java Development Kit (JDK) 11 or higher
* A Firebase Project (with Auth, Firestore, and Crashlytics enabled)
* An AWS S3 or Cloudflare R2 Bucket 

---

## Local Setup & Installation

### Clone the repository

```bash
git clone https://github.com/Amruth-hiremath/GateKept.git
```

### Add Firebase Configuration

Place your `google-services.json` file inside the `app/` directory.

### Configure Environment Secrets

Create a file named `local.properties` in the root directory of the project and add your storage credentials:

```properties
R2_ACCESS_KEY=your_access_key_here
R2_SECRET_KEY=your_secret_key_here
R2_ENDPOINT=your_endpoint_url_here
R2_PUBLIC_URL=your_public_url_here
```

### Build the Project

Open the project in Android Studio, allow Gradle to sync, and run the app configuration on an emulator or physical device. 

---

## CI/CD Pipeline

This project utilizes GitHub Actions for continuous integration. The workflow is defined in `.github/workflows/android-ci.yml`.

On every push or pull request to the `main` branch, the pipeline will:

* Spin up an Ubuntu runner
* Inject the necessary R2 secrets via GitHub Repository Secrets
* Build the Debug APK to ensure codebase stability 

---

## Contributing

Contributions are welcome. If you are developing a new feature or fixing a bug:

1. Create a feature branch:

   ```bash
   git checkout -b feature/your-feature-name
   ```

2. Ensure you have not committed `local.properties` or `google-services.json`.

3. Commit your changes with descriptive messages.

4. Push to your branch and open a Pull Request. 

---

**Good luck with your exams!**
