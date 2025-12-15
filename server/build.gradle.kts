import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.time.Instant

plugins {
    id(
        libs.plugins.kotlin.jvm
            .get()
            .pluginId,
    )
    id(
        libs.plugins.kotlin.serialization
            .get()
            .pluginId,
    )
    id(
        libs.plugins.ktlint
            .get()
            .pluginId,
    )
    application
    alias(libs.plugins.shadowjar)
    id(
        libs.plugins.buildconfig
            .get()
            .pluginId,
    )
    id(
        libs.plugins.jte
            .get()
            .pluginId,
    )
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

    // GraphQL
    implementation(libs.graphql.kotlin.server)
    implementation(libs.graphql.kotlin.scheme)
    implementation(libs.graphql.java.scalars)

    // Exposed ORM
    implementation(libs.bundles.exposed)
    implementation(libs.postgres)
    implementation(libs.h2)
    implementation(libs.hikaricp)

    // Exposed Migrations
    implementation(libs.exposed.migrations)

    // tray icon
    implementation(libs.bundles.systemtray)

    // dependencies of Mihon (Tachiyomi) extensions, some are duplicate, keeping it here for reference
    implementation(libs.injekt)
    implementation(libs.okhttp.core)
    implementation(libs.rxjava)
    implementation(libs.jsoup)

    // ComicInfo
    implementation(libs.serialization.xml.core)
    implementation(libs.serialization.xml)

    // Sort
    implementation(libs.sort)

    // asm for ByteCodeEditor(fixing SimpleDateFormat) (must match Dex2Jar version)
    implementation(libs.asm)

    // Disk & File
    implementation(libs.cache4k)
    implementation(libs.zip4j)
    implementation(libs.commonscompress)
    implementation(libs.junrar)

    // AES/CBC/PKCS7Padding Cypher provider for zh.copymanga
    implementation(libs.bouncycastle)

    // AndroidCompat
    implementation(projects.androidCompat)
    implementation(projects.androidCompat.config)

    // i18n
    implementation(projects.server.i18n)

    // Settings module
    implementation(projects.server.serverConfig)

    // uncomment to test extensions directly
//    implementation(fileTree("lib/"))
    implementation(kotlin("script-runtime"))

    testImplementation(libs.mockk)

    implementation(libs.cron4j)

    implementation(libs.cronUtils)

    implementation(libs.jwt)
    implementation(libs.datetime)

    compileOnly(libs.kte)
    // IReader extension dependencies - these must be provided by the server
    // Extensions are compiled with compileOnly and expect the runtime to provide these
    implementation(libs.coroutines.core)
    implementation(libs.datetime)
    implementation(libs.serialization.json)
    implementation(libs.ktor.core)
    implementation(libs.ktor.core.cio) // CIO engine for Ktor
    implementation(libs.ktor.contentNegotiation)
    implementation(libs.ktor.contentNegotiation.kotlinx)
    // Ksoup - KMP-compatible HTML parser (replaces Jsoup for iOS)
    implementation(libs.ksoup)
    implementation(libs.ksoup.network)
    implementation(libs.ktor.okhttp)
    implementation(libs.ktor.contentNegotiation.gson)
}

jte {
    generate()
}

application {
    applicationDefaultJvmArgs =
        listOf(
            "-Djunrar.extractor.thread-keep-alive-seconds=30",
        )
    mainClass.set(MainClass)
}

sourceSets {
    main {
        resources {
            srcDir("src/main/resources")
            srcDir("build/generated/src/main/resources")
        }
        kotlin {
            srcDir("build/generated/src/main/kotlin")
        }
    }
    test {
        resources {
            srcDir("build/generated/src/test/resources")
        }
    }
}

buildConfig {
    className("BuildConfig")
    packageName("suwayomi.tachidesk.server.generated")

    useKotlinOutput()

    fun quoteWrap(obj: Any): String = """"$obj""""

    buildConfigField("String", "NAME", quoteWrap(rootProject.name))
    buildConfigField("String", "VERSION", quoteWrap(getTachideskVersion()))
    buildConfigField("String", "REVISION", quoteWrap(getTachideskRevision()))
    buildConfigField("String", "BUILD_TYPE", quoteWrap(if (System.getenv("ProductBuildType") == "Stable") "Stable" else "Preview"))
    buildConfigField("long", "BUILD_TIME", Instant.now().epochSecond.toString())

    buildConfigField("String", "WEBUI_TAG", quoteWrap(webUIRevisionTag))

    buildConfigField("String", "GITHUB", quoteWrap("https://github.com/Suwayomi/Suwayomi-Server"))
    buildConfigField("String", "DISCORD", quoteWrap("https://discord.gg/DDZdqZWaHA"))
}

tasks {
    shadowJar {
        isZip64 = true
        manifest {
            attributes(
                "Main-Class" to MainClass,
                "Implementation-Title" to rootProject.name,
                "Implementation-Vendor" to "The Suwayomi Project",
                "Specification-Version" to getTachideskVersion(),
                "Implementation-Version" to getTachideskRevision(),
            )
        }
        archiveBaseName.set(rootProject.name)
        archiveVersion.set(getTachideskVersion())
        archiveClassifier.set("")
        destinationDirectory.set(File("$rootDir/server/build"))
        mergeServiceFiles()
    }

    test {
        useJUnitPlatform()
        testLogging {
            showStandardStreams = true
            events("passed", "skipped", "failed")
        }
    }

    withType<KotlinJvmCompile> {
        compilerOptions {
            freeCompilerArgs.add(
                "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            )
        }
    }

    named<Copy>("processResources") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mustRunAfter("downloadWebUI")
    }

    register<Download>("downloadWebUI") {
        src("https://github.com/Suwayomi/Suwayomi-WebUI-preview/releases/download/$webUIRevisionTag/Suwayomi-WebUI-$webUIRevisionTag.zip")
        dest("src/main/resources/WebUI.zip")

        fun shouldOverwrite(): Boolean {
            val zipPath = project.projectDir.absolutePath + "/src/main/resources/WebUI.zip"
            val zipFile = net.lingala.zip4j.ZipFile(zipPath)

            var shouldOverwrite = true
            if (zipFile.isValidZipFile) {
                val zipRevision =
                    zipFile.getInputStream(zipFile.getFileHeader("revision")).bufferedReader().use {
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
            application.applicationDefaultJvmArgs =
                listOf(
                    "-Dsuwayomi.tachidesk.config.server.webUIInterface=electron",
                    // Change this to the installed electron application
                    "-Dsuwayomi.tachidesk.config.server.electronPath=/usr/bin/electron",
                )
        }
    }

    runKtlintCheckOverMainSourceSet {
        mustRunAfter(generateJte)
        dependsOn(":server:server-config-generate:generateSettings")
    }

    compileKotlin {
        dependsOn(":server:server-config-generate:generateSettings")
    }

    processResources {
        dependsOn(":server:server-config-generate:generateSettings")
    }

    processTestResources {
        dependsOn(":server:server-config-generate:generateSettings")
    }
}
