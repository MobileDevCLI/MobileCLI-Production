/**
 * MobileCLI Production - App Build Configuration
 *
 * CRITICAL REQUIREMENTS:
 * 1. Package name MUST be "com.termux" - Termux binaries have hardcoded RUNPATH
 *    "/data/data/com.termux/files/usr/lib". Any other package name breaks binaries.
 *
 * 2. targetSdkVersion MUST be 28 or lower - Android 10+ (API 29+) blocks exec()
 *    from app data directories via SELinux. This is how Termux works.
 *
 * These are not optional - they are fundamental technical requirements.
 */

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.termux"
    compileSdk = 34

    defaultConfig {
        // REQUIRED: Must match Termux RUNPATH
        applicationId = "com.termux"

        minSdk = 24

        // REQUIRED: Must be <= 28 for exec() to work
        // See: https://issuetracker.google.com/issues/128554619
        targetSdk = 28

        // Version 162 - Production rebuild
        versionCode = 162
        versionName = "2.0.0-production"

        // BuildConfig fields for configuration
        buildConfigField("String", "SUPABASE_URL", "\"https://mwxlguqukyfberyhtkmg.supabase.co\"")
        buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", "\"pk_test_placeholder\"")

        // Admin mode configuration
        buildConfigField("String", "ADMIN_PASSWORD_HASH", "\"81dc9bdb52d04dc20036dbd8313ed055\"") // MD5 of "1234"
        buildConfigField("int", "ADMIN_TAP_COUNT", "7")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../mobilecli-release.keystore")
            storePassword = "mobilecli2026"
            keyAlias = "mobilecli"
            keyPassword = "mobilecli2026"
        }
    }

    // Build flavors: user (production) and dev (development)
    flavorDimensions += "mode"
    productFlavors {
        create("user") {
            dimension = "mode"
            // Production build: dev mode OFF by default
            buildConfigField("boolean", "DEV_MODE_DEFAULT", "false")
            buildConfigField("boolean", "SHOW_DEV_TOOLS", "false")
        }
        create("dev") {
            dimension = "mode"
            // Development build: dev mode ON by default
            buildConfigField("boolean", "DEV_MODE_DEFAULT", "true")
            buildConfigField("boolean", "SHOW_DEV_TOOLS", "true")
            versionNameSuffix = "-dev"
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = null // Keep same ID for testing
        }
        release {
            isMinifyEnabled = false // Keep false for Termux compatibility
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/LICENSE.md"
        }
    }

    lint {
        // REQUIRED: We must use targetSdk 28 for Termux binaries
        // This is not a code quality issue - it's a technical necessity
        disable += "ExpiredTargetSdkVersion"
        disable += "OldTargetApi"
        abortOnError = false
    }
}

dependencies {
    // =====================================
    // Termux Terminal Libraries (Apache 2.0)
    // =====================================
    // These provide the core terminal emulation
    implementation("com.github.termux.termux-app:terminal-view:v0.118.0")
    implementation("com.github.termux.termux-app:terminal-emulator:v0.118.0")

    // =====================================
    // AndroidX Core Libraries
    // =====================================
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // =====================================
    // UI Components
    // =====================================
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // =====================================
    // Coroutines for async operations
    // =====================================
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // =====================================
    // Networking
    // =====================================
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // =====================================
    // JSON Parsing
    // =====================================
    implementation("com.google.code.gson:gson:2.10.1")

    // =====================================
    // Stripe Payment SDK
    // =====================================
    implementation("com.stripe:stripe-android:20.36.0")

    // =====================================
    // Supabase (for authentication)
    // =====================================
    // Using OkHttp for REST API calls to Supabase
    // No SDK needed - we use direct REST API

    // =====================================
    // Security
    // =====================================
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // =====================================
    // Testing (optional)
    // =====================================
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
