plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signatur-Schlüssel kommt ausschließlich aus GitHub Secrets (nie aus dem Repository).
val signingStoreFile = System.getenv("SIGNING_KEYSTORE_FILE")
val signingPassword = System.getenv("SIGNING_PASSWORD")
val hasSigningKey = !signingStoreFile.isNullOrBlank() && !signingPassword.isNullOrBlank() && file(signingStoreFile).exists()

android {
    namespace = "com.postmen1971.privatbrowser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.postmen1971.privatbrowser"
        minSdk = 22
        targetSdk = 35
        versionCode = 5
        versionName = "0.2.0"
    }

    signingConfigs {
        if (hasSigningKey) {
            create("privat") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingPassword
                keyAlias = "privatbrowser"
                keyPassword = signingPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasSigningKey) signingConfigs.getByName("privat") else signingConfigs.getByName("debug")
        }
        debug {
            if (hasSigningKey) signingConfig = signingConfigs.getByName("privat")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
}
