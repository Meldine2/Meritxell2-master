plugins {
    alias(libs.plugins.androidApplication) // Apply Android Application plugin
    alias(libs.plugins.jetbrainsKotlinAndroid) // Apply Kotlin plugin for Android
    id("com.google.gms.google-services")  // Apply Firebase plugin at the app level
    id("kotlin-kapt")
}

android {
    namespace = "com.example.meritxell"
    compileSdk = 35  // Set to SDK version 35 as recommended

    defaultConfig {
        applicationId = "com.example.meritxell"
        minSdk = 24
        targetSdk = 35  // Updated to SDK version 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Support all common architectures
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true  // Keep Compose enabled as per your configuration
        dataBinding = true // Keep Data Binding enabled as per your configuration
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"  // Keep this version as per your error message
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"  // Exclude unnecessary resources
        }
    }
    
    // Temporarily removing splits due to packaging issues
    // splits {
    //     abi {
    //         isEnable = true
    //         reset()
    //         include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    //         isUniversalApk = true
    //     }
    // }

    lint {
        baseline = file("lint-baseline.xml")
        disable += "MissingClass"
    }
}

dependencies {
    // --- START: Firebase Dependencies (Managed by BoM) ---
    // Import the Firebase BoM (Bill of Materials) to manage your Firebase library versions.
    // ALWAYS use the latest stable version available. Check https://firebase.google.com/docs/android/setup#available-libraries
    // As of my last update, a recent stable version is 33.0.0. You might want to check for newer!
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))

    // Firebase libraries (versions are now managed by the BoM, so no version numbers here)
    implementation("com.google.firebase:firebase-auth-ktx")       // Kotlin extensions for Auth
    implementation("com.google.firebase:firebase-firestore-ktx")  // Kotlin extensions for Firestore
    implementation("com.google.firebase:firebase-functions-ktx")  // ADDED: Kotlin extensions for Cloud Functions
    implementation("com.google.firebase:firebase-database-ktx")   // Kotlin extensions for Realtime Database
    implementation("com.google.firebase:firebase-storage-ktx")    // Kotlin extensions for Cloud Storage
    implementation("com.google.firebase:firebase-messaging-ktx")  // Kotlin extensions for Cloud Messaging
    implementation("com.google.firebase:firebase-analytics-ktx")  // Replaces firebase-core in newer setups
    implementation("com.google.firebase:firebase-appcheck-ktx")   // ADDED: App Check for security
    implementation("com.google.firebase:firebase-appcheck-debug") // ADDED: App Check debug provider
    // --- END: Firebase Dependencies ---

    // AndroidX and Jetpack libraries
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Testing libraries
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Add Glide dependency
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // Add Picasso dependency
    implementation("com.squareup.picasso:picasso:2.71828")

    // Material Components library for NavigationView (already present, version 1.10.0 is newer than 1.3.0)
    implementation("androidx.drawerlayout:drawerlayout:1.1.1")  // For DrawerLayout

    implementation("androidx.viewpager2:viewpager2:1.0.0")

    implementation("com.google.android.gms:play-services-base:18.2.0")

    implementation("androidx.navigation:navigation-fragment-ktx:2.4.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.4.0")

    // --- START: Added/Updated Jetpack Compose Dependencies ---
    // Import the Compose BOM to manage Compose library versions
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))  // Using a stable BOM version

    // Add core Compose UI libraries
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")  // Modern Material Design for Compose

    // For Compose tooling (e.g., previews in Android Studio)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // For Compose integration with Activities
    implementation("androidx.activity:activity-compose:1.8.2")

    // For ViewModel integration with Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    // --- END: Added/Updated Jetpack Compose Dependencies ---

    implementation ("com.google.firebase:firebase-functions:20.1.0")
}