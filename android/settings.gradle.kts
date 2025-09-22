pluginManagement {
    // Read flutter.sdk INSIDE this block
    val props = java.util.Properties().apply {
        val f = file("local.properties")
        check(f.exists()) { "local.properties not found at: ${f.absolutePath}" }
        f.inputStream().use { s -> this.load(s) }
    }
    val flutterSdk = props.getProperty("flutter.sdk")
        ?: error("flutter.sdk not set in local.properties")

    // Point Gradle to Flutter's build logic
    includeBuild(file("$flutterSdk/packages/flutter_tools/gradle").absolutePath)

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Flutter engine artifacts (embedding, shell, etc.)
        val engineDir = file("$flutterSdk/bin/cache/artifacts/engine/android")
        if (engineDir.exists()) {
            maven { url = engineDir.toURI() }   // handles Windows path with spaces
        }
        // Mirror used by some plugins
        maven { url = uri("https://storage.googleapis.com/download.flutter.io") }
    }
}

dependencyResolutionManagement {
    // repositoriesMode.set(org.gradle.api.initialization.resolve.RepositoriesMode.PREFER_SETTINGS) // optional
    repositories {
        google()
        mavenCentral()

        // Read flutter.sdk AGAIN in this block (independent scope)
        val props = java.util.Properties().apply {
            val f = file("local.properties")
            check(f.exists()) { "local.properties not found at: ${f.absolutePath}" }
            f.inputStream().use { s -> this.load(s) }
        }
        val flutterSdk = props.getProperty("flutter.sdk")
        if (flutterSdk != null) {
            val engineDir = file("$flutterSdk/bin/cache/artifacts/engine/android")
            if (engineDir.exists()) {
                maven { url = engineDir.toURI() }
            }
            maven { url = uri("https://storage.googleapis.com/download.flutter.io") }
        }
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}

include(":app")
