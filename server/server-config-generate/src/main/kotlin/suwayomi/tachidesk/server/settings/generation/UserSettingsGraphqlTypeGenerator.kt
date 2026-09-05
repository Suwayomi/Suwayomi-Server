package suwayomi.tachidesk.server.settings.generation

import suwayomi.tachidesk.server.settings.UserSetting
import java.io.File

object UserSettingsGraphqlTypeGenerator {
    fun generate(
        settings: Map<String, UserSetting<*>>,
        outputFile: File,
    ) {
        outputFile.parentFile.mkdirs()

        val settingsToInclude = settings.values

        if (settingsToInclude.isEmpty()) {
            println("Warning: No user settings found to create graphql type from.")
            return
        }

        val groupedSettings = settingsToInclude.groupBy { it.group.value }

        outputFile.writeText(
            buildString {
                appendLine(KotlinFileGeneratorHelper.createFileHeader("suwayomi.tachidesk.graphql.types"))
                writeImports(groupedSettings.values.flatten())
                writeUserSettingsInterface(groupedSettings)
                writePartialUserSettingsType(groupedSettings)
                writeUserSettingsType(groupedSettings)
            },
        )

        println("UserSettings graphql type generated successfully! Total user settings: ${settingsToInclude.size}")
    }

    private fun StringBuilder.writeImports(settings: List<UserSetting<*>>) {
        appendLine(
            KotlinFileGeneratorHelper.createUserSettingsImports(
                listOf(
                    "suwayomi.tachidesk.graphql.server.primitives.Node",
                    "suwayomi.tachidesk.server.settings.UserSettingsRegistry",
                    "suwayomi.tachidesk.server.settings.userConfig",
                    "suwayomi.tachidesk.server.settings.userSettings as globalUserSettings",
                ),
                settings,
            ),
        )
    }

    private fun StringBuilder.writeUserSettingsInterface(groupedSettings: Map<String, List<UserSetting<*>>>) {
        appendLine("interface UserSettings : Node {")

        writeSettings(groupedSettings, indentation = 4, asType = true, isOverride = false, isNullable = true, isInterface = true)

        appendLine("}")
        appendLine()
    }

    private fun StringBuilder.writePartialUserSettingsType(groupedSettings: Map<String, List<UserSetting<*>>>) {
        appendLine("data class PartialUserSettingsType(")

        writeSettings(groupedSettings, indentation = 4, asType = true, isOverride = true, isNullable = true, isInterface = false)

        appendLine(") : UserSettings")
        appendLine()
    }

    private fun StringBuilder.writeUserSettingsType(groupedSettings: Map<String, List<UserSetting<*>>>) {
        appendLine("class UserSettingsType(")

        writeSettings(groupedSettings, indentation = 4, asType = true, isOverride = true, isNullable = false, isInterface = false)

        appendLine(") : UserSettings {")

        // Write secondary constructor
        val indentation = 4
        appendLine("@Suppress(\"UNCHECKED_CAST\")".addIndentation(indentation))
        appendLine("constructor(userId: Int, userSettings: suwayomi.tachidesk.server.settings.UserSettings = globalUserSettings) : this(".addIndentation(indentation))

        writeSettings(
            groupedSettings,
            indentation = indentation * 2,
            asType = false,
            isOverride = false,
            isNullable = false,
            isInterface = false,
        )

        appendLine(")".addIndentation(indentation))

        appendLine("}")
        appendLine()
    }

    private fun StringBuilder.writeSettings(
        groupedSettings: Map<String, List<UserSetting<*>>>,
        indentation: Int,
        asType: Boolean,
        isOverride: Boolean,
        isNullable: Boolean,
        isInterface: Boolean,
    ) {
        groupedSettings.forEach { (group, settings) ->
            appendLine("// $group".addIndentation(indentation))
            settings.forEach { setting -> writeSetting(setting, indentation, asType, isOverride, isNullable, isInterface) }
        }
    }

    private fun StringBuilder.writeSetting(
        setting: UserSetting<*>,
        indentation: Int,
        asType: Boolean,
        isOverride: Boolean,
        isNullable: Boolean,
        isInterface: Boolean,
    ) {
        if (!asType) {
            appendLine("${getConfigAccess(setting)},".addIndentation(indentation))
            return
        }

        val overridePrefix = if (isOverride) "override " else ""
        val nullableSuffix = if (isNullable) "?" else ""
        val commaSuffix = if (isOverride) "," else ""
        appendLine(
            "${overridePrefix}val ${setting.key}: ${getGraphQLType(
                setting,
                isInterface,
            )}$nullableSuffix$commaSuffix".addIndentation(indentation),
        )
    }

    private fun getGraphQLType(
        setting: UserSetting<*>,
        isInterface: Boolean,
    ): String {
        val possibleType = setting.typeInfo?.specificType ?: setting.type.simpleName

        val exception = RuntimeException("Unknown user setting type: ${setting.typeInfo}")

        if (isInterface) {
            return setting.typeInfo?.interfaceType ?: possibleType ?: throw exception
        }

        return possibleType ?: throw exception
    }

    private fun getConfigAccess(setting: UserSetting<*>): String {
        if (setting.typeInfo?.convertToGqlType != null) {
            return "UserSettingsRegistry.get(\"${setting.key}\")!!.typeInfo!!.convertToGqlType!!(" +
                "userSettings.value(userId, userConfig.${setting.key})" +
                ") as ${getGraphQLType(setting, false)}"
        }

        return "userSettings.value(userId, userConfig.${setting.key})"
    }
}
