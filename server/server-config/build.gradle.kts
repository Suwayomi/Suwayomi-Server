plugins {
    id(
        libs.plugins.kotlin.jvm
            .get()
            .pluginId,
    )
}

dependencies {
    // Core Kotlin
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    
    // Coroutines for MutableStateFlow
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.jdk8)
    
    // Config handling
    implementation(libs.config)
    implementation(libs.config4k)
    
    // Logging
    implementation(libs.slf4japi)
    implementation(libs.kotlinlogging)
    
    // Database (for SortOrder enum used in ServerConfig)
    implementation(libs.exposed.core)
    
    // GraphQL types used in ServerConfig
    implementation(libs.graphql.kotlin.scheme)
    
    // AndroidCompat for SystemPropertyOverridableConfigModule
    implementation(projects.androidCompat.config)
    
    // Serialization
    implementation(libs.serialization.json)
    implementation(libs.serialization.protobuf)
}

tasks {
    register<JavaExec>("generateSettings") {
        group = "build setup"
        description = "Generates settings from ServerConfig"

        dependsOn(compileKotlin)

        // Use this module's classpath which includes server as dependency
        classpath = sourceSets.main.get().runtimeClasspath
        
        mainClass.set("suwayomi.tachidesk.server.settings.generation.SettingsGeneratorKt")

        // Get reference to server project for file paths
        val serverProject = project(":server")
        
        // Set working directory to the server module directory
        workingDir = serverProject.projectDir
        
        inputs.files(
            serverProject.sourceSets.main.get().allSource.filter {
                it.name.contains("ServerConfig") || it.name.contains("Settings")
            },
        )
        
        outputs.files(
            serverProject.file("src/main/resources/server-reference.conf"),
            serverProject.file("src/test/resources/server-reference.conf"),
            serverProject.file("src/main/kotlin/suwayomi/tachidesk/graphql/types/SettingsType.kt"),
            serverProject.file("src/main/kotlin/suwayomi/tachidesk/manga/impl/backup/proto/models/BackupServerSettings.kt"),
            serverProject.file("src/main/kotlin/suwayomi/tachidesk/manga/impl/backup/proto/handlers/BackupSettingsHandler.kt"),
        )
    }
}