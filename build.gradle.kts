import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.4.21" apply false // Also in buildSrc Config.kt
    id("java")
}

allprojects {
    group = "xyz.nulldev.ts"

    version = "1.0"

    repositories {
        jcenter()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://dl.bintray.com/inorichi/maven")
        maven("https://dl.google.com/dl/android/maven2/")
    }
}

val javaProjects = listOf(
        project(":AndroidCompat"),
        project(":AndroidCompat:Config"),
        project(":server")
)

configure(javaProjects) {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    dependencies {
        // Kotlin
        implementation(kotlin("stdlib", KotlinCompilerVersion.VERSION))
        implementation(kotlin("stdlib", KotlinCompilerVersion.VERSION))
        testImplementation(kotlin("test", version = "1.4.21"))
    }
}

configure(listOf(
        project(":AndroidCompat"),
        project(":server"),
        project(":AndroidCompat:Config")

)) {
    dependencies {
        // Dependency Injection
        implementation("org.kodein.di:kodein-di-conf-jvm:7.1.0")

        // Logging
        implementation("org.slf4j:slf4j-api:1.7.30")
        implementation("org.slf4j:slf4j-simple:1.7.30")
        implementation("io.github.microutils:kotlin-logging:2.0.3")

        // RxJava
        implementation("io.reactivex:rxjava:1.3.8")
        implementation("io.reactivex:rxkotlin:1.0.0")

        // JSoup
        implementation("org.jsoup:jsoup:1.13.1")

        // Kotlin
        implementation(kotlin("reflect", version = "1.4.21"))

        // dependency of :AndroidCompat:Config
        implementation("com.typesafe:config:1.4.0")
    }
}