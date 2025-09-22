plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")        
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.example.spam_analyzer_v6"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.spam_analyzer_v6"
        minSdk = 24
        targetSdk = 35
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true   // ✅ Kotlin DSL syntax
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Shizuku
   implementation("dev.rikka.shizuku:api:13.1.5")
   implementation("dev.rikka.shizuku:provider:13.1.5")

    implementation("com.google.mlkit:text-recognition:16.0.0")
}
