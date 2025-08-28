package suwayomi.tachidesk.server.settings.generation

import com.typesafe.config.ConfigRenderOptions
import io.github.config4k.toConfig
import suwayomi.tachidesk.server.settings.SettingsRegistry
import java.io.File

object SettingsConfigFileGenerator {
    private const val SERVER_PREFIX = "server."

    fun generate(
        outputDir: File,
        testOutputDir: File,
        settings: Map<String, SettingsRegistry.SettingMetadata>,
    ) {
        // Config files only include up-to-date settings.
        val settingsToInclude = settings.filterValues { it.deprecated == null }

        if (settingsToInclude.isEmpty()) {
            println("Warning: No settings found to write to config files.")
            return
        }

        generateServerReferenceConf(settingsToInclude, outputDir)
        generateServerReferenceConf(settingsToInclude, testOutputDir)

        println("Settings config file generated successfully! Total settings: ${settingsToInclude.size}")
        println("- Main config: ${outputDir.resolve("server-reference.conf").absolutePath}")
        println("- Test config: ${testOutputDir.resolve("server-reference.conf").absolutePath}")
    }

    private fun generateServerReferenceConf(
        settings: Map<String, SettingsRegistry.SettingMetadata>,
        outputDir: File,
    ) {
        outputDir.mkdirs()
        val outputFile = outputDir.resolve("server-reference.conf")

        val groupedSettings = settings.values.groupBy { it.group }

        // Write the config with comments
        outputFile.writeText(
            buildString {
                writeSettings(groupedSettings)
            },
        )
    }

    private fun StringBuilder.writeSettings(groupedSettings: Map<String, List<SettingsRegistry.SettingMetadata>>) {
        val renderOptions =
            ConfigRenderOptions
                .defaults()
                .setOriginComments(false)
                .setComments(false)
                .setFormatted(true)
                .setJson(false)

        var isFirstGroup = true
        groupedSettings.forEach { (groupName, groupSettings) ->
            // Prevent empty line at start of the file
            if (!isFirstGroup) {
                appendLine()
            }
            isFirstGroup = false

            appendLine("# $groupName")

            groupSettings.forEach { setting ->
                writeSetting(setting, renderOptions)
            }
        }
    }

    private fun StringBuilder.writeSetting(
        setting: SettingsRegistry.SettingMetadata,
        renderOptions: ConfigRenderOptions,
    ) {
        val key = "$SERVER_PREFIX${setting.name}"

        val configValue = setting.defaultValue.toConfig("internal").getValue("internal")
        var renderedValue = configValue.render(renderOptions)

        // Force quotes on all string values for consistency
        // Check if it's a string value that's not already quoted
        if (setting.defaultValue is String && !renderedValue.startsWith("\"")) {
            renderedValue = "\"$renderedValue\""
        }

        val settingString = "$key = $renderedValue"

        val description = setting.description
        if (description != null) {
            val descriptionLines = description.split("\n")

            if (descriptionLines.isEmpty()) {
                return
            }

            appendLine("$settingString # ${descriptionLines[0]}")
            descriptionLines.drop(1).forEach { line ->
                appendLine("# $line")
            }

            return
        }

        appendLine(settingString)
    }
}
