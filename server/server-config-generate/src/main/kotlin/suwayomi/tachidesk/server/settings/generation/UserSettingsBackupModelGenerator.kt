package suwayomi.tachidesk.server.settings.generation

import suwayomi.tachidesk.server.settings.UserSetting
import java.io.File

object UserSettingsBackupModelGenerator {
    fun generate(
        settings: Map<String, UserSetting<*>>,
        outputFile: File,
    ) {
        outputFile.parentFile.mkdirs()

        val settingsToInclude = settings.values

        if (settingsToInclude.isEmpty()) {
            println("Warning: No user settings found to create BackupUserSettings from.")
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

        println("BackupUserSettings generated successfully! Total user settings: ${settingsToInclude.size}")
    }

    private fun StringBuilder.writeImports(settings: List<UserSetting<*>>) {
        appendLine(
            KotlinFileGeneratorHelper.createUserSettingsImports(
                listOf(
                    "kotlinx.serialization.Serializable",
                    "kotlinx.serialization.protobuf.ProtoNumber",
                    "suwayomi.tachidesk.graphql.types.UserSettings",
                ),
                settings,
            ),
        )
    }

    private fun StringBuilder.writeClass(sortedSettings: List<UserSetting<*>>) {
        appendLine("@Serializable")
        appendLine("data class BackupUserSettings(")

        writeSettings(sortedSettings, indentation = 4)

        appendLine(") : UserSettings")
        appendLine()
    }

    private fun StringBuilder.writeSettings(
        sortedSettings: List<UserSetting<*>>,
        indentation: Int,
    ) {
        sortedSettings.forEach { setting ->
            appendLine(
                "@ProtoNumber(${setting.protoNumber}) override var ${setting.key}: ${getSettingType(setting)}? = null,"
                    .addIndentation(indentation),
            )
        }
    }

    private fun getSettingType(setting: UserSetting<*>): String =
        setting.typeInfo?.backupType
            ?: setting.typeInfo?.specificType
            ?: setting.type.simpleName
            ?: throw RuntimeException("Unknown user setting type: ${setting.typeInfo}")
}
