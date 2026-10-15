import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    // buildSrc's runtime classpath is visible to the main build's .gradle.kts
    // scripts: zip4j backs server/downloadWebUI's revision check and gson backs
    // server:i18n/generateLocales. Declare them here instead of relying on
    // kotlin-gradle-plugin's transitive classpath.
    implementation(libs.zip4j)
    implementation(libs.gson)
}

java {
    val javaVersion = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())
}
