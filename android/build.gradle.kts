allprojects {
    repositories {
        google()
        mavenCentral()
        val props = java.util.Properties().apply {
            file("${rootDir}/local.properties").inputStream().use { load(it) }
        }
        val flutterSdk = props.getProperty("flutter.sdk")
        if (flutterSdk != null) {
            maven { url = uri("$flutterSdk/bin/cache/artifacts/engine/android") }
            maven { url = uri("https://storage.googleapis.com/download.flutter.io") }
        }
    }
}

// (your custom buildDir relocation + clean task can stay as-is)

val newBuildDir: Directory = rootProject.layout.buildDirectory.dir("../../build").get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
