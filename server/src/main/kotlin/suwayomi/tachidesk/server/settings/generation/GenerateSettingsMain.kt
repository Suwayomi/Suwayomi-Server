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
        // Set output directories relative to the current working directory
        val outputDir = File("src/main/resources")
        val testOutputDir = File("src/test/resources")
        val graphqlOutputDir = File("src/main/kotlin/suwayomi/tachidesk/graphql/types")

        SettingsGenerator.generate(
            outputDir = outputDir,
            testOutputDir = testOutputDir,
            graphqlOutputDir = graphqlOutputDir,
        )

        println("✅ Settings files generation completed successfully!")
    } catch (e: Exception) {
        println("❌ Error generating settings files: ${e.message}")
        e.printStackTrace()
        System.exit(1)
    }
}
