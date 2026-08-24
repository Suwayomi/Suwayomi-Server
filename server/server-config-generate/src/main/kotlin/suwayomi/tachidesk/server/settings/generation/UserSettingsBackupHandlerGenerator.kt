package suwayomi.tachidesk.server.settings.generation

import suwayomi.tachidesk.server.settings.UserSetting
import java.io.File

object UserSettingsBackupHandlerGenerator {
    fun generate(
        settings: Map<String, UserSetting<*>>,
        modelFile: File,
        outputFile: File,
    ) {
        outputFile.parentFile.mkdirs()

        val settingsToInclude = settings.values

        if (settingsToInclude.isEmpty()) {
            println("Warning: No user settings found to create BackupUserSettingsHandler from.")
            return
        }

        val groupedSettings = settingsToInclude.groupBy { it.group.value }

        outputFile.writeText(
            buildString {
                appendLine(KotlinFileGeneratorHelper.createFileHeader("suwayomi.tachidesk.manga.impl.backup.proto.handlers"))
                writeImports(groupedSettings.values.flatten())
                writeHandler(groupedSettings)
            },
        )

        println("BackupUserSettingsHandler generated successfully! Total user settings: ${settingsToInclude.size}")
    }

    private fun StringBuilder.writeImports(settings: List<UserSetting<*>>) {
        appendLine(
            KotlinFileGeneratorHelper.createUserSettingsImports(
                listOf(
                    "suwayomi.tachidesk.graphql.types.DownloadConversion",
                    "suwayomi.tachidesk.manga.impl.backup.BackupFlags",
                    "suwayomi.tachidesk.manga.impl.backup.proto.models.BackupServerSettings",
                    "suwayomi.tachidesk.manga.impl.backup.proto.models.BackupUserSettings",
                    "suwayomi.tachidesk.server.settings.UserSettingsRegistry",
                    "suwayomi.tachidesk.server.settings.userConfig",
                    "suwayomi.tachidesk.server.settings.userSettings",
                ),
                settings,
            ),
        )
    }

    private fun StringBuilder.writeHandler(groupedSettings: Map<String, List<UserSetting<*>>>) {
        appendLine("object BackupUserSettingsHandler {")

        writeBackupFunction(groupedSettings)
        appendLine()
        writeRestoreFunction(groupedSettings.values.flatten())

        appendLine("}")
        appendLine()
    }

    private fun StringBuilder.writeBackupFunction(groupedSettings: Map<String, List<UserSetting<*>>>) {
        val indentation = 4
        val contentIndentation = indentation * 2

        appendLine("fun backup(userId: Int): BackupUserSettings {".addIndentation(indentation))
        appendLine("return BackupUserSettings(".addIndentation(contentIndentation))
        writeSettings(groupedSettings, indentation * 3)
        appendLine(")".addIndentation(contentIndentation))
        appendLine("}".addIndentation(indentation))
    }

    private fun StringBuilder.writeRestoreFunction(settings: List<UserSetting<*>>) {
        val indentation = 4
        val contentIndentation = indentation * 2

        appendLine("fun restore(".addIndentation(indentation))
        appendLine("userId: Int,".addIndentation(contentIndentation))
        appendLine("userSettingsBackup: BackupUserSettings?,".addIndentation(contentIndentation))
        appendLine("legacyServerSettings: BackupServerSettings?,".addIndentation(contentIndentation))
        appendLine("flags: BackupFlags,".addIndentation(contentIndentation))
        appendLine(") {".addIndentation(indentation))

        settings.forEach { setting ->
            val needsConversion = setting.typeInfo?.restoreLegacy != null
            appendLine(
                "(userSettingsBackup?.${setting.key} ?: if (flags.includeServerSettings) legacyServerSettings?.${setting.key} else null)?.let {".addIndentation(
                    contentIndentation,
                ),
            )
            if (needsConversion) {
                appendLine(
                    "userSettings.set(userId, userConfig.${setting.key}, UserSettingsRegistry.get(\"${setting.key}\")!!.typeInfo!!.restoreLegacy!!(it) as ${setting.internalType ?: setting.type.simpleName})".addIndentation(
                        contentIndentation * 2,
                    ),
                )
            } else {
                appendLine(
                    "userSettings.set(userId, userConfig.${setting.key}, it)".addIndentation(
                        contentIndentation * 2,
                    ),
                )
            }
            appendLine("}".addIndentation(contentIndentation))
        }

        appendLine("}".addIndentation(indentation))
    }

    private fun StringBuilder.writeSettings(
        groupedSettings: Map<String, List<UserSetting<*>>>,
        indentation: Int,
    ) {
        groupedSettings.forEach { (group, settings) ->
            appendLine("// $group".addIndentation(indentation))
            settings.forEach { setting -> writeSetting(setting, indentation) }
        }
    }

    private fun StringBuilder.writeSetting(
        setting: UserSetting<*>,
        indentation: Int,
    ) {
        appendLine("${setting.key} = ${getConfigAccess(setting)},".addIndentation(indentation))
    }

    private fun getConfigAccess(setting: UserSetting<*>): String {
        if (setting.typeInfo?.convertToBackupType != null) {
            return "UserSettingsRegistry.get(\"${setting.key}\")!!.typeInfo!!.convertToBackupType!!(" +
                "userSettings.value(userId, userConfig.${setting.key})" +
                ") as? ${getBackupType(setting)}"
        }

        return "userSettings.value(userId, userConfig.${setting.key})"
    }

    private fun getBackupType(setting: UserSetting<*>): String =
        setting.typeInfo?.backupType
            ?: setting.typeInfo?.specificType
            ?: setting.type.simpleName
            ?: throw RuntimeException("Unknown user setting type: ${setting.typeInfo}")
}
