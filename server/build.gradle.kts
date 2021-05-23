import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask
import java.io.BufferedReader

plugins {
    application
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("org.jmailen.kotlinter") version "3.4.3"
    id("de.fuerstenau.buildconfig") version "1.1.8"
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

    // tray icon
    implementation("com.dorkbox:SystemTray:4.1")
    implementation("com.dorkbox:Utilities:1.9")


    // dependencies of Tachiyomi extensions, some are duplicate, keeping it here for reference
    implementation("com.github.inorichi.injekt:injekt-core:65b0440")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    implementation("io.reactivex:rxjava:1.3.8")
    implementation("org.jsoup:jsoup:1.13.1")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("com.github.salomonbrys.kotson:kotson:2.5.0")


    // Source models and interfaces from Tachiyomi 1.x
    // using source class from tachiyomi commit 9493577de27c40ce8b2b6122cc447d025e34c477 to not depend on tachiyomi.sourceapi
//    implementation("tachiyomi.sourceapi:source-api:1.1")

    // AndroidCompat
    implementation(project(":AndroidCompat"))
    implementation(project(":AndroidCompat:Config"))

    // uncomment to test extensions directly
//    implementation(fileTree("lib/"))

    // Testing
    testImplementation(kotlin("test-junit5"))
}

val MainClass = "ir.armor.tachidesk.MainKt"
application {
    mainClass.set(MainClass)
}

sourceSets {
    main {
        resources {
            srcDir("src/main/resources")
        }
    }
}

// should be bumped with each stable release
val tachideskVersion = "v0.3.8"

// counts commit count on master
val tachideskRevision = Runtime
        .getRuntime()
        .exec("git rev-list master --count")
        .let { process ->
            process.waitFor()
            val output = process.inputStream.use {
                it.bufferedReader().use(BufferedReader::readText)
            }
            process.destroy()
            "r" + output.trim()

        }

buildConfig {
    appName = rootProject.name
    clsName = "BuildConfig"
    packageName = "ir.armor.tachidesk.server"
    version = tachideskVersion


    buildConfigField("String", "name", rootProject.name) // alias for BuildConfig.NAME
    buildConfigField("String", "version", tachideskVersion) // alias for BuildConfig.VERSION
    buildConfigField("String", "revision", tachideskRevision)
    buildConfigField("boolean", "debug", project.hasProperty("debugApp").toString())
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
                    "-Xopt-in=kotlinx.coroutines.InternalCoroutinesApi"
            )
        }
    }

    test {
        useJUnit()
    }

    withType<ShadowJar> {
        destinationDirectory.set(File("$rootDir/server/build"))
        dependsOn("formatKotlin", "lintKotlin")
    }

    named("run") {
        dependsOn("formatKotlin", "lintKotlin")
    }

    named<Copy>("processResources") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mustRunAfter(":webUI:copyBuild")
    }

    withType<LintTask> {
        source(files("src/kotlin"))
    }

    withType<FormatTask> {
        source(files("src/kotlin"))
    }
}
