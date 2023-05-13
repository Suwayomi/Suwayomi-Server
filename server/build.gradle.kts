import de.undercouch.gradle.tasks.download.Download
import java.time.Instant

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.kotlin.jvm.get().pluginId)
    id(libs.plugins.kotlin.serialization.get().pluginId)
    id(libs.plugins.kotlinter.get().pluginId)
    application
    alias(libs.plugins.shadowjar)
    id(libs.plugins.buildconfig.get().pluginId)
}

dependencies {
    // Shared
    implementation(libs.bundles.shared)
    testImplementation(libs.bundles.sharedTest)

    // OkHttp
    implementation(libs.bundles.okhttp)
    implementation(libs.okio)

    // Javalin api
    implementation(libs.bundles.javalin)
    implementation(libs.bundles.jackson)

    // Exposed ORM
    implementation(libs.bundles.exposed)
    implementation(libs.h2)

    // Exposed Migrations
    implementation(libs.exposed.migrations)

    // tray icon
    implementation(libs.bundles.systemtray)

    // dependencies of Tachiyomi extensions, some are duplicate, keeping it here for reference
    implementation(libs.injekt)
    implementation(libs.okhttp.core)
    implementation(libs.rxjava)
    implementation(libs.jsoup)

    // Sort
    implementation(libs.sort)

    // asm for ByteCodeEditor(fixing SimpleDateFormat) (must match Dex2Jar version)
    implementation(libs.asm)

    // Disk & File
    implementation(libs.zip4j)
    implementation(libs.junrar)

    // CloudflareInterceptor
    implementation(libs.playwright)

    // AES/CBC/PKCS7Padding Cypher provider for zh.copymanga
    implementation(libs.bouncycastle)

    // AndroidCompat
    implementation(projects.androidCompat)
    implementation(projects.androidCompat.config)

    // uncomment to test extensions directly
//    implementation(fileTree("lib/"))
    implementation(kotlin("script-runtime"))

    implementation("com.expediagroup:graphql-kotlin-server:6.4.0")
    implementation("com.expediagroup:graphql-kotlin-schema-generator:6.4.0")
    implementation("com.graphql-java:graphql-java-extended-scalars:20.0")

    testImplementation(libs.mockk)
}

application {
    applicationDefaultJvmArgs = listOf(
        "-Djunrar.extractor.thread-keep-alive-seconds=30"
    )
    mainClass.set(MainClass)
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


    buildConfigField("String", "GITHUB", quoteWrap("https://github.com/Suwayomi/Tachidesk-Server"))
    buildConfigField("String", "DISCORD", quoteWrap("https://discord.gg/DDZdqZWaHA"))
}

tasks {
    shadowJar {
        manifest {
            attributes(
                "Main-Class" to MainClass,
                "Implementation-Title" to rootProject.name,
                "Implementation-Vendor" to "The Suwayomi Project",
                "Specification-Version" to tachideskVersion,
                "Implementation-Version" to tachideskRevision
            )
        }
        archiveBaseName.set(rootProject.name)
        archiveVersion.set(tachideskVersion)
        archiveClassifier.set(tachideskRevision)
        destinationDirectory.set(File("$rootDir/server/build"))
    }

    test {
        useJUnitPlatform()
        testLogging {
            showStandardStreams = true
            events("passed", "skipped", "failed")
        }
    }

    named<Copy>("processResources") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mustRunAfter("downloadWebUI")
    }

    register<Download>("downloadWebUI") {
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

                if (zipRevision == webUIRevisionTag) {
                    shouldOverwrite = false
                }
            }

            return shouldOverwrite
        }

        overwrite(shouldOverwrite())
    }

    register("runElectron") {
        group = "application"
        finalizedBy(run)
        doFirst {
            application.applicationDefaultJvmArgs = listOf(
                "-Dsuwayomi.tachidesk.config.server.webUIInterface=electron",
                // Change this to the installed electron application
                "-Dsuwayomi.tachidesk.config.server.electronPath=/usr/bin/electron"
            )
        }
    }
}
