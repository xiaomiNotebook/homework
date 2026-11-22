import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

plugins {
    id("com.android.application") version "8.7.3"
}

val compileLegacyApiStubs by tasks.registering(JavaCompile::class) {
    source("compat-stubs/src")
    classpath = files(android.bootClasspath)
    destinationDirectory.set(layout.buildDirectory.dir("generated/legacyApiStubs/classes"))
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
}

val legacyApiStubsJar by tasks.registering(Jar::class) {
    dependsOn(compileLegacyApiStubs)
    archiveFileName.set("legacy-api-stubs.jar")
    destinationDirectory.set(layout.buildDirectory.dir("generated/legacyApiStubs"))
    from(compileLegacyApiStubs.flatMap { it.destinationDirectory })
}

tasks.withType<JavaCompile>().configureEach {
    if (name != "compileLegacyApiStubs") {
        dependsOn(legacyApiStubsJar)
        doFirst {
            val androidBootClasspath = options.bootstrapClasspath
            options.bootstrapClasspath = files(legacyApiStubsJar.flatMap { it.archiveFile }, androidBootClasspath)
        }
    }
}

android {
    namespace = "net.micode.notes"
    compileSdk = 34

    defaultConfig {
        applicationId = "net.micode.notes"
        minSdk = 14
        targetSdk = 28
        versionCode = 1
        versionName = "0.1"
    }

    sourceSets {
        named("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.setSrcDirs(listOf("src"))
            res.setSrcDirs(listOf("res"))
        }
    }

    useLibrary("org.apache.http.legacy")
}
