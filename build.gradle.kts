import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
}

allprojects {
    group = "suwayomi"

    version = "1.0"

    repositories {
        mavenCentral()
        maven("https://maven.google.com/")
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://dl.google.com/dl/android/maven2/")
    }
}

val projects = listOf(
        project(":AndroidCompat"),
        project(":AndroidCompat:Config"),
        project(":server")
)

configure(projects) {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8.toString()
        }
    }

    dependencies {
        // Kotlin
        implementation(kotlin("stdlib-jdk8"))
        implementation(kotlin("reflect"))
        testImplementation(kotlin("test-junit5"))

        // coroutines
        val coroutinesVersion = "1.5.1"
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")

        val kotlinSerializationVersion = "1.3.0-RC"
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinSerializationVersion")


        // Dependency Injection
        implementation("org.kodein.di:kodein-di-conf-jvm:7.7.0")

        // Logging
        implementation("org.slf4j:slf4j-api:1.7.30")
        implementation("ch.qos.logback:logback-classic:1.2.3")
        implementation("io.github.microutils:kotlin-logging:2.0.6")

        // ReactiveX
        implementation("io.reactivex:rxjava:1.3.8")
        implementation("io.reactivex:rxkotlin:1.0.0")
        implementation("com.jakewharton.rxrelay:rxrelay:1.2.0")

        // dependency both in AndroidCompat and extensions, version locked by Tachiyomi app/extensions
        implementation("org.jsoup:jsoup:1.14.1")

        // dependency of :AndroidCompat:Config
        implementation("com.typesafe:config:1.4.1")
        implementation("io.github.config4k:config4k:0.4.2")

        // to get application content root
        implementation("net.harawata:appdirs:1.2.1")

        // dex2jar
        val dex2jarVersion = "v26"
        implementation("com.github.ThexXTURBOXx.dex2jar:dex-translator:$dex2jarVersion")
        implementation("com.github.ThexXTURBOXx.dex2jar:dex-tools:$dex2jarVersion")

        // APK parser
        implementation("net.dongliu:apk-parser:2.6.10")


        // dependency both in AndroidCompat and server, version locked by javalin
        implementation("com.fasterxml.jackson.core:jackson-annotations:2.10.3")
    }
}