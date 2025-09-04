@file:JvmName("SettingsGeneratorKt")

package suwayomi.tachidesk.server.settings.generation

import java.io.File

/**
 * Main function to generate settings files from ServerConfig
 * This is called by the generateSettingsFiles Gradle task
 */
fun main() {
    println("Generating settings files from ServerConfig registry...")

    try {
        // Set output directories relative to the current working directory (server module)
        val outputDir = File("build/generated/src/main/resources")
        val testOutputDir = File("build/generated/src/test/resources")
        val graphqlOutputDir = File("build/generated/src/main/kotlin/suwayomi/tachidesk/graphql/types")
        val backupSettingsOutputDir = File("build/generated/src/main/kotlin/suwayomi/tachidesk/manga/impl/backup/proto/models")
        val backupSettingsHandlerOutputDir = File("build/generated/src/main/kotlin/suwayomi/tachidesk/manga/impl/backup/proto/handlers")

        SettingsGenerator.generate(
            outputDir = outputDir,
            testOutputDir = testOutputDir,
            graphqlOutputDir = graphqlOutputDir,
            backupSettingsOutputDir = backupSettingsOutputDir,
            backupSettingsHandlerOutputDir = backupSettingsHandlerOutputDir,
        )

        println("✅ Settings files generation completed successfully!")
    } catch (e: Exception) {
        println("❌ Error generating settings files: ${e.message}")
        e.printStackTrace()
        System.exit(1)
    }
}
