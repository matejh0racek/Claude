plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val mapboxVersion = "3.30.7"

android {
    namespace = "com.matejhoracek.gtinav"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.matejhoracek.gtinav"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // Phones (arm64) and the Android Studio emulator on Intel/AMD Macs and PCs (x86_64).
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = false
    }
    androidResources {
        // The car model is already compressed enough; keep it readable straight from the APK.
        noCompress += "glb"
    }
}

dependencies {
    implementation("com.mapbox.navigationcore:android:$mapboxVersion")
    implementation("com.mapbox.navigationcore:ui-components:$mapboxVersion")
    implementation("com.mapbox.navigationcore:android-auto-components:$mapboxVersion")
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
