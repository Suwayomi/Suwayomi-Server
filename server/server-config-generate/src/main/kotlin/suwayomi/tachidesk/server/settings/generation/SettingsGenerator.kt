package suwayomi.tachidesk.server.settings.generation

import com.typesafe.config.ConfigFactory
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.settings.SettingsRegistry
import suwayomi.tachidesk.server.util.ConfigTypeRegistration
import java.io.File
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * Utility to generate settings files from ServerConfig and SettingsRegistry
 * This can be run as a standalone main function to generate all required files
 */
object SettingsGenerator {
    init {
        // Register custom types for config serialization
        ConfigTypeRegistration.registerCustomTypes()
        triggerSettingRegistration()
    }

    /**
     * Force registration of all settings without full ServerConfig instantiation
     */
    private fun triggerSettingRegistration() {
        // This creates a minimal instance just to trigger delegate registration
        try {
            val mockConfig =
                ConfigFactory.parseString(
                    """
                    server {
                        ip = "0.0.0.0"
                        port = 4567
                    }
                    """.trimIndent(),
                )

            val tempConfig = ServerConfig { mockConfig.getConfig("server") }
            // Access all properties to trigger delegate registrations
            tempConfig::class.memberProperties.forEach { prop ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    (prop as KProperty1<ServerConfig, Any?>).get(tempConfig)
                } catch (e: Exception) {
                    // Ignore errors during registration
                }
            }
        } catch (e: Exception) {
            // Registration failed, but we tried
        }
    }

    fun generate(
        outputDir: File,
        testOutputDir: File,
        graphqlOutputDir: File,
        backupSettingsOutputDir: File,
        backupSettingsHandlerOutputDir: File,
    ) {
        val settings = SettingsRegistry.getAll()

        if (settings.isEmpty()) {
            println("Warning: No settings found in registry. Settings might not be initialized.")
            return
        }

        println(" - Total: ${settings.size}")
        println(" - Deprecated: ${settings.values.count { it.deprecated != null }}")
        println(" - Require restart: ${settings.values.count { it.requiresRestart }}")

        SettingsConfigFileGenerator.generate(outputDir, testOutputDir, settings)

        val settingsTypeFile = graphqlOutputDir.resolve("SettingsType.kt")
        SettingsGraphqlTypeGenerator.generate(settings, settingsTypeFile)

        val backupServerSettingsFile = backupSettingsOutputDir.resolve("BackupServerSettings.kt")
        SettingsBackupServerSettingsGenerator.generate(settings, backupServerSettingsFile)

        val backupSettingsHandlerFile = backupSettingsHandlerOutputDir.resolve("BackupSettingsHandler.kt")
        SettingsBackupSettingsHandlerGenerator.generate(settings, backupSettingsHandlerFile)
    }
}
