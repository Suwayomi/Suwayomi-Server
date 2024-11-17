import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.buildconfig) apply false
    alias(libs.plugins.download)
}

allprojects {
    group = "suwayomi"

    version = "1.0"

    repositories {
        mavenCentral()
        google()
        maven("https://github.com/Suwayomi/Suwayomi-Server/raw/android-jar/")
        maven("https://jitpack.io")
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }

    plugins.withType<KtlintPlugin> {
        extensions.configure<KtlintExtension>("ktlint") {
            version.set(libs.versions.ktlint.get())
            filter {
                exclude("**/generated/**")
            }
        }
    }

    tasks {
        withType<KotlinJvmCompile> {
            dependsOn("ktlintFormat")
            compilerOptions {
                jvmTarget = JvmTarget.JVM_21
                freeCompilerArgs.add("-Xcontext-receivers")
            }
        }
    }
}
