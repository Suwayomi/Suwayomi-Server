package suwayomi.tachidesk.server.settings.generation

import suwayomi.tachidesk.server.settings.SettingsRegistry
import java.io.File
import kotlin.text.appendLine

object SettingsBackupServerSettingsGenerator {
    fun generate(
        settings: Map<String, SettingsRegistry.SettingMetadata>,
        outputFile: File,
    ) {
        outputFile.parentFile.mkdirs()

        val settingsToInclude = settings.values

        if (settingsToInclude.isEmpty()) {
            println("Warning: No settings found to create BackupServerSettings from.")
            return
        }

        val sortedSettings = settingsToInclude.sortedBy { it.protoNumber }

        outputFile.writeText(
            buildString {
                appendLine(KotlinFileGeneratorHelper.createFileHeader("suwayomi.tachidesk.manga.impl.backup.proto.models"))
                writeImports(sortedSettings)
                writeClass(sortedSettings)
            },
        )

        println("BackupServerSettingsGenerator generated successfully! Total settings: ${settingsToInclude.size}")
    }

    private fun StringBuilder.writeImports(settings: List<SettingsRegistry.SettingMetadata>) {
        appendLine(
            KotlinFileGeneratorHelper.createImports(
                listOf(
                    "kotlinx.serialization.Serializable",
                    "kotlinx.serialization.protobuf.ProtoNumber",
                    "suwayomi.tachidesk.graphql.types.Settings",
                ),
                settings,
            ),
        )
    }

    private fun StringBuilder.writeClass(sortedSettings: List<SettingsRegistry.SettingMetadata>) {
        appendLine("@Serializable")
        appendLine("data class BackupServerSettings(")

        writeSettings(sortedSettings, indentation = 4)

        appendLine(") : Settings")
        appendLine()
    }

    private fun StringBuilder.writeSettings(
        sortedSettings: List<SettingsRegistry.SettingMetadata>,
        indentation: Int,
    ) {
        sortedSettings.forEach { setting ->
            val deprecated = setting.deprecated
            if (deprecated != null) {
                val replaceWithSuffix = deprecated.replaceWith?.let { ", ReplaceWith(\"$it\")" } ?: ""
                appendLine(
                    "@Deprecated(\"${deprecated.message}\"$replaceWithSuffix)".addIndentation(
                        indentation,
                    ),
                )
            }

            appendLine(
                "@ProtoNumber(${setting.protoNumber}) override var ${setting.name}: ${getSettingType(setting)}? = null,"
                    .addIndentation(indentation),
            )
        }
    }

    private fun getSettingType(setting: SettingsRegistry.SettingMetadata): String =
        setting.typeInfo.backupType
            ?: setting.typeInfo.specificType
            ?: setting.typeInfo.type.simpleName
            ?: throw RuntimeException("Unknown setting type: ${setting.typeInfo}")
}
