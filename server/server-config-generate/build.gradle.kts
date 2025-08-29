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
    
    // Config handling
    implementation(libs.config)
    implementation(libs.config4k)
    
    // Logging
    implementation(libs.slf4japi)
    implementation(libs.kotlinlogging)
    
    // Serialization
    implementation(libs.serialization.json)
    implementation(libs.serialization.protobuf)
    
    // Depend on server-config module for access to ServerConfig and SettingsRegistry
    implementation(projects.server.serverConfig)
}

tasks {
    register<JavaExec>("generateSettings") {
        group = "build setup"
        description = "Generates settings from ServerConfig"

        dependsOn(compileKotlin)

        // Use this module's classpath which includes server-config as dependency
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