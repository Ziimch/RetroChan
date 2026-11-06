plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.retrochan.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.retrochan.app"
        minSdk = 26
        targetSdk = 34
        // versionCode naik otomatis tiap build di GitHub -> update tanpa install ulang
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Kunci yang SAMA dipakai terus (dari GitHub Secrets) -> APK bisa di-update tanpa uninstall
    signingConfigs {
        create("release") {
            val path = System.getenv("KEYSTORE_PATH")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    lint { checkReleaseBuilds = false; abortOnError = false }

    packaging { jniLibs { useLegacyPackaging = true } } // core harus ter-extract
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("com.github.Swordfish90:LibretroDroid:0.13.0")
}
