import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.32"
}

allprojects {
    group = "ir.armor.tachidesk"

    version = "1.0"

    repositories {
        mavenCentral()
        maven("https://maven.google.com/")
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://dl.google.com/dl/android/maven2/")
    }
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("1.4.32")
        }
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
        testImplementation(kotlin("test"))

        // coroutines
        val coroutinesVersion = "1.4.3"
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")


        // Dependency Injection
        implementation("org.kodein.di:kodein-di-conf-jvm:7.5.0")

        // Logging
        implementation("org.slf4j:slf4j-api:1.7.30")
        implementation("ch.qos.logback:logback-classic:1.2.3")
        implementation("io.github.microutils:kotlin-logging:2.0.6")

        // RxJava
        implementation("io.reactivex:rxjava:1.3.8")
        implementation("io.reactivex:rxkotlin:1.0.0")

        // JSoup
        implementation("org.jsoup:jsoup:1.13.1")


        // dependency of :AndroidCompat:Config
        implementation("com.typesafe:config:1.4.1")
        implementation("io.github.config4k:config4k:0.4.2")

        // to get application content root
        implementation("net.harawata:appdirs:1.2.1")

        // dex2jar: https://github.com/DexPatcher/dex2jar/releases/tag/v2.1-20190905-lanchon
        implementation("com.github.DexPatcher.dex2jar:dex-tools:v2.1-20190905-lanchon")

        // APK parser
        implementation("net.dongliu:apk-parser:2.6.10")
    }
}