package suwayomi.tachidesk.server.settings.generation

import suwayomi.tachidesk.server.settings.SettingsRegistry
import java.io.File
import kotlin.text.appendLine

object SettingsBackupSettingsHandlerGenerator {
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

        val groupedSettings = settingsToInclude.groupBy { it.group }

        outputFile.writeText(
            buildString {
                appendLine(KotlinFileGeneratorHelper.createFileHeader("suwayomi.tachidesk.manga.impl.backup.proto.handlers"))
                writeImports(groupedSettings.values.flatten())
                writeHandler(groupedSettings)
            },
        )

        println("BackupServerSettings generated successfully! Total settings: ${settingsToInclude.size}")
    }

    private fun StringBuilder.writeImports(settings: List<SettingsRegistry.SettingMetadata>) {
        appendLine(
            KotlinFileGeneratorHelper.createImports(
                listOf(
                    "suwayomi.tachidesk.graphql.mutations.SettingsMutation",
                    "suwayomi.tachidesk.manga.impl.backup.BackupFlags",
                    "suwayomi.tachidesk.manga.impl.backup.proto.models.BackupServerSettings",
                    "suwayomi.tachidesk.server.serverConfig",
                    "suwayomi.tachidesk.server.settings.SettingsRegistry",
                ),
                settings,
            ),
        )
    }

    private fun StringBuilder.writeHandler(groupedSettings: Map<String, List<SettingsRegistry.SettingMetadata>>) {
        appendLine("object BackupSettingsHandler {")

        writeBackupFunction(groupedSettings)
        appendLine()
        writeRestoreFunction(groupedSettings.values.flatten())

        appendLine("}")
        appendLine()
    }

    private fun StringBuilder.writeBackupFunction(groupedSettings: Map<String, List<SettingsRegistry.SettingMetadata>>) {
        val indentation = 4
        val contentIndentation = indentation * 2

        appendLine("fun backup(flags: BackupFlags): BackupServerSettings? {".addIndentation(indentation))
        appendLine("if (!flags.includeServerSettings) { return null }".addIndentation(contentIndentation))
        appendLine()
        appendLine("return BackupServerSettings(".addIndentation(contentIndentation))
        writeSettings(groupedSettings, indentation * 3)
        appendLine(")".addIndentation(contentIndentation))
        appendLine("}".addIndentation(indentation))
    }

    private fun StringBuilder.writeRestoreFunction(settings: List<SettingsRegistry.SettingMetadata>) {
        val indentation = 4
        val contentIndentation = indentation * 2

        appendLine("fun restore(backupServerSettings: BackupServerSettings?) {".addIndentation(indentation))
        appendLine("if (backupServerSettings == null) { return }".addIndentation(contentIndentation))
        appendLine()
        appendLine("SettingsMutation().updateSettings(".addIndentation(contentIndentation))
        appendLine("backupServerSettings.copy(".addIndentation(indentation * 3))

        val deprecatedSettings = settings.filter { it.typeInfo.restoreLegacy != null }
        deprecatedSettings.forEach { setting ->
            appendLine(
                "${setting.name} = SettingsRegistry.get(\"${setting.name}\")!!.typeInfo.restoreLegacy!!(".addIndentation(indentation * 4) +
                    "backupServerSettings.${setting.name}" +
                    ") as? ${getSettingType(setting, false)},",
            )
        }
        val excludedSettings = settings.filter { it.excludeFromBackup == true }
        excludedSettings.forEach { setting ->
            appendLine(
                "${setting.name} = null,".addIndentation(indentation * 4),
            )
        }
        appendLine("),".addIndentation(indentation * 3))
        appendLine(")".addIndentation(contentIndentation))
        appendLine("}".addIndentation(indentation))
    }

    private fun StringBuilder.writeSettings(
        groupedSettings: Map<String, List<SettingsRegistry.SettingMetadata>>,
        indentation: Int,
    ) {
        groupedSettings.forEach { (group, settings) ->
            appendLine("// $group".addIndentation(indentation))
            settings.forEach { setting -> writeSetting(setting, indentation) }
        }
    }

    private fun StringBuilder.writeSetting(
        setting: SettingsRegistry.SettingMetadata,
        indentation: Int,
    ) {
        appendLine("${setting.name} = ${getConfigAccess(setting)},".addIndentation(indentation))
    }

    private fun getSettingType(
        setting: SettingsRegistry.SettingMetadata,
        asBackup: Boolean,
    ): String {
        val possibleType = setting.typeInfo.specificType ?: setting.typeInfo.type.simpleName

        val exception = RuntimeException("Unknown setting type: ${setting.typeInfo}")

        if (asBackup) {
            return setting.typeInfo.backupType ?: possibleType ?: throw exception
        }

        return possibleType ?: throw exception
    }

    private fun getConfigAccess(setting: SettingsRegistry.SettingMetadata): String {
        if (setting.excludeFromBackup == true) {
            return "null"
        }
        if (setting.typeInfo.convertToBackupType != null) {
            return "SettingsRegistry.get(\"${setting.name}\")!!.typeInfo.convertToBackupType!!(" +
                "serverConfig.${setting.name}.value" +
                ") as? ${getSettingType(setting, true)}"
        }

        return "serverConfig.${setting.name}.value"
    }
}
