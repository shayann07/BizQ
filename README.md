# BizQ

Android business-profile builder for registering a service business, defining its offerings and hours, and maintaining a shareable profile backed by Firebase and Room.

## Overview

BizQ is a Kotlin Android application focused on business-owner onboarding. A user creates or signs in to a Firebase account, completes a six-step setup flow, and manages a profile containing business details, industry, services, weekly availability, logo, Instagram link, and description.

The app writes cloud data to Firestore and Firebase Storage while mirroring core records in a local Room database. Its interface supports English and Hebrew and includes profile actions for calling, opening an address in a maps application, and opening Instagram.

## Features

- Firebase email/password registration and login
- Six-step business onboarding with durable local and cloud progress
- Business name, phone, address, industry, and description management
- Service creation, editing, deletion, duration, pricing in NIS, and descriptions
- Weekly working-hours editor with per-day availability
- Business logo selection and Firebase Storage upload
- Instagram handle/link normalization and profile launch action
- Business profile actions for phone calls and map navigation
- Room-first reads with Firestore synchronization for business data
- Brand-color suggestions from The Color API with local fallback colors
- English and Hebrew language switching
- Resume routing for completed onboarding and cleanup of abandoned registration state

## Tech Stack

- Kotlin and Android SDK
- XML layouts and View Binding
- Jetpack Navigation
- ViewModel, LiveData, and Kotlin coroutines
- Hilt dependency injection
- Room persistence
- Firebase Authentication, Cloud Firestore, Storage, and Analytics
- Retrofit, OkHttp, Moshi, and Gson
- Glide
- Material Components

## Architecture

BizQ uses an MVVM-style structure with repositories coordinating cloud and local storage:

1. Fragments render registration, profile, service, and availability screens.
2. ViewModels validate input and coordinate asynchronous work.
3. Repository interfaces separate authentication, business, service, availability, and onboarding operations.
4. Firestore is the cloud store, while Room caches users, businesses, services, availability, drafts, and onboarding state.
5. Hilt modules provide repositories, DAOs, Firebase clients, and the color API client.

The authenticated Firebase UID also acts as the business identifier throughout the current data model.

## Project Structure

```text
app/src/main/java/com/example/finalproject/
|-- colorsApi/       # Color API models, adapter, and repository
|-- data/
|   |-- loca_db/     # Room database and DAOs
|   |-- models/      # Business, service, user, and availability models
|   |-- remote/      # Retrofit color API definitions
|   `-- repository/  # Local/cloud repository implementations
|-- di/              # Hilt modules
|-- ui/               # Home, auth, onboarding, profile, services, and hours
|-- utils/            # Resource wrappers and fragment helpers
|-- App.kt
`-- MainActivity.kt
```

## Getting Started

### Prerequisites

- Android Studio with a JDK compatible with Android Gradle Plugin 8.12.2
- Android SDK 35
- An Android 7.0 (API 24) or newer device or emulator
- A Firebase project with Authentication, Firestore, and Storage configured

### Build

```bash
git clone https://github.com/shayann07/BizQ.git
cd BizQ
./gradlew assembleDebug
```

On Windows PowerShell, use `./gradlew.bat assembleDebug`. Configure `ANDROID_HOME` or add a local `sdk.dir` entry before running Gradle. Replace the included project-specific Firebase configuration when connecting the app to another Firebase project.

## Current Status and Limitations

- The implemented flow is for business owners; customer discovery, booking, calendars, and appointment management are not implemented.
- The repository does not document Firestore/Storage rules, schema deployment, Firebase setup, or release distribution.
- An abandoned partial onboarding flow can delete the user's cloud records and attempt to delete the Firebase Authentication account.
- A Kotlin source file is also tracked directly under `app/src/main/res`, outside the normal source tree.
- A release APK, baseline profile artifacts, and Firebase configuration are committed to the repository.
- Only generated example unit and instrumentation tests are present.
- A local build attempt on June 12, 2026 could not start because the Android SDK path was not configured in the environment.
- No license file is included.
