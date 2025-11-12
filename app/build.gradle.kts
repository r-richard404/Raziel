plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.raziel"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.raziel"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.ext.junit)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // Core Tink for custom crypto operations and old devices
    implementation("com.google.crypto.tink:tink-android:1.15.0")
    // AndroidX Security for better key management
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Concurrent operations
    implementation("androidx.concurrent:concurrent-futures:1.1.0")
    // Benchmarking
    implementation("androidx.benchmark:benchmark-junit4:1.2.1")
    implementation("androidx.work:work-runtime:2.9.0")
    implementation("androidx.core:core:1.12.0")
}