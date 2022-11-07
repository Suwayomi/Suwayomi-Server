import de.undercouch.gradle.tasks.download.Download
import java.time.Instant

plugins {
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.github.gmazzo.buildconfig")
}

dependencies {
    // okhttp
    val okhttpVersion = "4.10.0" // Major version is locked by Tachiyomi extensions
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:$okhttpVersion")
    implementation("com.squareup.okio:okio:3.2.0")

    // Javalin api
    // Javalin 5.0.0+ requires Java 11
    implementation("io.javalin:javalin:4.6.6")
    implementation("io.javalin:javalin-openapi:4.6.6")
    // jackson version locked by javalin, ref: `io.javalin.core.util.OptionalDependency`
    val jacksonVersion = "2.13.3"
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    // Exposed ORM
    val exposedVersion = "0.40.1"
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    // current database driver, can't update to h2 v2 without sql migration
    implementation("com.h2database:h2:1.4.200")

    // Exposed Migrations
    implementation("com.github.Suwayomi:exposed-migrations:3.2.0")

    // tray icon
    implementation("com.dorkbox:SystemTray:4.1")
    implementation("com.dorkbox:Utilities:1.9") // version locked by SystemTray

    // dependencies of Tachiyomi extensions, some are duplicate, keeping it here for reference
    implementation("com.github.inorichi.injekt:injekt-core:65b0440")
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("io.reactivex:rxjava:1.3.8")
    implementation("org.jsoup:jsoup:1.15.3")

    // Sort
    implementation("com.github.gpanther:java-nat-sort:natural-comparator-1.1")

    // asm for ByteCodeEditor(fixing SimpleDateFormat) (must match Dex2Jar version)
    implementation("org.ow2.asm:asm:9.4")

    // Disk & File
    implementation("net.lingala.zip4j:zip4j:2.11.2")
    implementation("com.github.junrar:junrar:7.5.3")

    // CloudflareInterceptor
    implementation("net.sourceforge.htmlunit:htmlunit:2.65.1")

    // AES/CBC/PKCS7Padding Cypher provider for zh.copymanga
    implementation("org.bouncycastle:bcprov-jdk18on:1.72")

    // Source models and interfaces from Tachiyomi 1.x
    // using source class from tachiyomi commit 9493577de27c40ce8b2b6122cc447d025e34c477 to not depend on tachiyomi.sourceapi
//    implementation("tachiyomi.sourceapi:source-api:1.1")

    // AndroidCompat
    implementation(project(":AndroidCompat"))
    implementation(project(":AndroidCompat:Config"))

    // uncomment to test extensions directly
//    implementation(fileTree("lib/"))
    implementation(kotlin("script-runtime"))

    testImplementation("io.mockk:mockk:1.13.2")
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
