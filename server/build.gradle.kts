
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask
import java.time.Instant

plugins {
    application
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("org.jmailen.kotlinter") version "3.5.0"
    id("com.github.gmazzo.buildconfig") version "3.0.2"
}

repositories {
    maven {
        url = uri("https://repo1.maven.org/maven2/")
    }
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    // okhttp
    val okhttpVersion = "4.9.1" // version is locked by Tachiyomi extensions
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:$okhttpVersion")
    implementation("com.squareup.okio:okio:2.10.0")

    // Javalin api
    implementation("io.javalin:javalin:3.13.6")
    // jackson version is tied to javalin, ref: `io.javalin.core.util.OptionalDependency`
    implementation("com.fasterxml.jackson.core:jackson-databind:2.10.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.3")

    // Exposed ORM
    val exposedVersion = "0.31.1"
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    // current database driver
    implementation("com.h2database:h2:1.4.200")

    // Exposed Migrations
    val exposedMigrationsVersion = "3.1.1"
    implementation("com.github.Suwayomi:exposed-migrations:$exposedMigrationsVersion")

    // tray icon
    implementation("com.dorkbox:SystemTray:4.1")
    implementation("com.dorkbox:Utilities:1.9")


    // dependencies of Tachiyomi extensions, some are duplicate, keeping it here for reference
    implementation("com.github.inorichi.injekt:injekt-core:65b0440")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    implementation("io.reactivex:rxjava:1.3.8")
    implementation("org.jsoup:jsoup:1.14.1")
    implementation("com.google.code.gson:gson:2.8.7")
    implementation("com.github.salomonbrys.kotson:kotson:2.5.0")


    // asm for ByteCodeEditor(fixing SimpleDateFormat) (must match Dex2Jar version)
    implementation("org.ow2.asm:asm:9.2")

    // extracting zip files
    implementation("net.lingala.zip4j:zip4j:2.9.0")

    // CloudflareInterceptor
    implementation("net.sourceforge.htmlunit:htmlunit:2.52.0")

    // Source models and interfaces from Tachiyomi 1.x
    // using source class from tachiyomi commit 9493577de27c40ce8b2b6122cc447d025e34c477 to not depend on tachiyomi.sourceapi
//    implementation("tachiyomi.sourceapi:source-api:1.1")

    // AndroidCompat
    implementation(project(":AndroidCompat"))
    implementation(project(":AndroidCompat:Config"))

    // uncomment to test extensions directly
//    implementation(fileTree("lib/"))
}

application {
    mainClass.set(MainClass)

    // uncomment for testing electron
//    applicationDefaultJvmArgs = listOf(
//            "-Dsuwayomi.tachidesk.config.server.webUIInterface=electron",
//            "-Dsuwayomi.tachidesk.config.server.electronPath=/usr/bin/electron"
//    )
}

sourceSets {
    main {
        resources {
            srcDir("src/main/resources")
        }
    }
}

buildConfig {
    className("BuildConfig")
    packageName("suwayomi.tachidesk.server")

    useKotlinOutput()

    fun quoteWrap(obj: Any): String = """"$obj""""

    buildConfigField("String", "NAME", quoteWrap(rootProject.name))
    buildConfigField("String", "VERSION", quoteWrap(tachideskVersion))
    buildConfigField("String", "REVISION", quoteWrap(tachideskRevision))
    buildConfigField("String", "BUILD_TYPE", quoteWrap(if (System.getenv("ProductBuildType") == "Stable") "Stable" else "Preview"))
    buildConfigField("long", "BUILD_TIME", Instant.now().epochSecond.toString())


    buildConfigField("String", "WEBUI_REPO", quoteWrap("https://github.com/Suwayomi/Tachidesk-WebUI-preview"))
    buildConfigField("String", "WEBUI_TAG", quoteWrap(webUIRevisionTag))


    buildConfigField("String", "GITHUB", quoteWrap("https://github.com/Suwayomi/Tachidesk"))
    buildConfigField("String", "DISCORD", quoteWrap("https://discord.gg/DDZdqZWaHA"))
}

tasks {
    shadowJar {
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to MainClass,
                    "Implementation-Title" to rootProject.name,
                    "Implementation-Vendor" to "The Suwayomi Project",
                    "Specification-Version" to tachideskVersion,
                    "Implementation-Version" to tachideskRevision
                )
            )
        }
        archiveBaseName.set(rootProject.name)
        archiveVersion.set(tachideskVersion)
        archiveClassifier.set(tachideskRevision)
    }
    withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-Xopt-in=kotlinx.coroutines.InternalCoroutinesApi",
                "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi",
            )
        }
    }

    test {
        useJUnit()
    }

    withType<ShadowJar> {
        destinationDirectory.set(File("$rootDir/server/build"))
    }

    named("run") {
        dependsOn("formatKotlin", "lintKotlin", "downloadWebUI")
    }

    named<Copy>("processResources") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mustRunAfter("downloadWebUI")
    }

    register<de.undercouch.gradle.tasks.download.Download>("downloadWebUI") {
        src("https://github.com/Suwayomi/Tachidesk-WebUI-preview/releases/download/$webUIRevisionTag/Tachidesk-WebUI-$webUIRevisionTag.zip")
        dest("src/main/resources/WebUI.zip")


        fun shouldOverwrite(): Boolean {
            val zipPath = project.projectDir.absolutePath + "/src/main/resources/WebUI.zip"
            val zipFile =  net.lingala.zip4j.ZipFile(zipPath)

            var shouldOverwrite = true
            if (zipFile.isValidZipFile) {
                val zipRevision = zipFile.getInputStream(zipFile.getFileHeader("revision")).bufferedReader().use {
                    it.readText().trim()
                }

                if (zipRevision == webUIRevisionTag)
                    shouldOverwrite = false
            }

            return shouldOverwrite
        }

        overwrite(shouldOverwrite())
    }

    withType<LintTask> {
        source(files("src/kotlin"))
    }

    withType<FormatTask> {
        source(files("src/kotlin"))
    }
}
