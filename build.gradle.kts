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
        maven("https://github.com/Suwayomi/Tachidesk-Server/raw/android-jar/")
        maven("https://jitpack.io")
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
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
            kotlinOptions {
                jvmTarget = JavaVersion.VERSION_1_8.toString()

                freeCompilerArgs += listOf(
                    "-Xcontext-receivers",
                )
            }
        }
    }
}
