import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("org.jmailen.kotlinter") version "3.12.0"
    id("com.github.gmazzo.buildconfig") version "3.1.0" apply false
    id("de.undercouch.download") version "5.3.0"
}

allprojects {
    group = "suwayomi"

    version = "1.0"

    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io")
        maven("https://github.com/Suwayomi/Tachidesk-Server/raw/android-jar/")
    }
}

val projects = listOf(
        project(":AndroidCompat"),
        project(":AndroidCompat:Config"),
        project(":server")
)

configure(projects) {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "org.jmailen.kotlinter")

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    tasks {
        withType<KotlinCompile> {
            dependsOn(formatKotlin)
            kotlinOptions {
                jvmTarget = JavaVersion.VERSION_1_8.toString()
            }
        }

        withType<LintTask> {
            source(files("src/kotlin"))
        }

        withType<FormatTask> {
            source(files("src/kotlin"))
        }
    }


    dependencies {
        // Kotlin
        implementation(kotlin("stdlib-jdk8"))
        implementation(kotlin("reflect"))
        testImplementation(kotlin("test-junit5"))

        // coroutines
        val coroutinesVersion = "1.6.4"
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
        testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")

        val kotlinSerializationVersion = "1.4.1"
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinSerializationVersion")

        // Dependency Injection
        implementation("org.kodein.di:kodein-di-conf-jvm:7.15.0")

        // Logging
        // Stuck on old versions since
        // 1. Logback 1.3.0+ requires Java 9
        // 2. Slf4j 2.0.0+ doesn't register older versions of Logback
        // 3. Kotlin-logging 3.0.2+ requires Java 11, but this is probably a bug
        implementation("org.slf4j:slf4j-api:1.7.32")
        implementation("ch.qos.logback:logback-classic:1.2.6")
        implementation("io.github.microutils:kotlin-logging:2.1.21")

        // ReactiveX
        implementation("io.reactivex:rxjava:1.3.8")

        // dependency both in AndroidCompat and extensions, version locked by Tachiyomi app/extensions
        implementation("org.jsoup:jsoup:1.15.3")

        // dependency of :AndroidCompat:Config
        implementation("com.typesafe:config:1.4.2")
        implementation("io.github.config4k:config4k:0.5.0")

        // to get application content root
        implementation("net.harawata:appdirs:1.2.1")

        // dex2jar
        val dex2jarVersion = "v56"
        implementation("com.github.ThexXTURBOXx.dex2jar:dex-translator:$dex2jarVersion")
        implementation("com.github.ThexXTURBOXx.dex2jar:dex-tools:$dex2jarVersion")

        // APK parser
        implementation("net.dongliu:apk-parser:2.6.10")

        // dependency both in AndroidCompat and server, version locked by javalin
        implementation("com.fasterxml.jackson.core:jackson-annotations:2.12.4")
    }
}