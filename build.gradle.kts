import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.buildconfig) apply false
    alias(libs.plugins.download) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.moko) apply false
    alias(libs.plugins.jte) apply false
}

allprojects {
    group = "suwayomi"

    repositories {
        mavenCentral()
        google()
        maven("https://github.com/Suwayomi/Suwayomi-Server/raw/android-jar/")
        maven("https://jitpack.io")
        maven("https://jogamp.org/deployment/maven")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }
}

// NOTE on `java` vs `java-library`: every module applies plain `java`, so all
// `implementation` dependencies leak onto consumers' compile classpaths via
// apiElements (e.g. AndroidCompat's polyfill/jcef and server-config's
// exposed/graphql reach :server's compile classpath). Switching to
// `java-library` and promoting the types each module actually exposes to `api`
// is a large, per-module audit and is left as a follow-up; the leaky
// convention is documented here so it is a known decision, not an accident.
subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            val javaVersion = JavaVersion.toVersion(libs.versions.jvmTarget.get())
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }
    }

    // Exempt: server-config & server-config-generate — pre-existing
    // violations need a dedicated formatting batch (tracked follow-up).
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
            // Formatting is an explicit, check-only concern: format via
            // `./gradlew ktlintFormat` and verify via `ktlintCheck`.
            // Compilation must never rewrite sources, so no ktlintFormat
            // dependency is wired into the compile tasks.
            compilerOptions {
                jvmTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())
                // Context parameters are enabled by default since Kotlin 2.4
            }
        }
    }
}
