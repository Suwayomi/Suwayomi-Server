package suwayomi.tachidesk.server.settings.generation

import suwayomi.tachidesk.server.settings.SettingsRegistry
import suwayomi.tachidesk.server.settings.UserSettingsRegistry
import java.io.File

object SettingsGraphqlTypeGenerator {
    fun generate(
        settings: Map<String, SettingsRegistry.SettingMetadata>,
        outputFile: File,
    ) {
        outputFile.parentFile.mkdirs()

        val settingsToInclude = settings.values

        if (settingsToInclude.isEmpty()) {
            println("Warning: No settings found to create graphql type from.")
            return
        }

        val groupedSettings = settingsToInclude.groupBy { it.group }

        outputFile.writeText(
            buildString {
                appendLine(KotlinFileGeneratorHelper.createFileHeader("suwayomi.tachidesk.graphql.types"))
                writeImports(groupedSettings.values.flatten())
                writeSettingsInterface(groupedSettings)
                writePartialSettingsType(groupedSettings)
                writeSettingsType(groupedSettings)
            },
        )

        println("Graphql type generated successfully! Total settings: ${settingsToInclude.size}")
    }

    private fun StringBuilder.writeImports(settings: List<SettingsRegistry.SettingMetadata>) {
        appendLine(
            KotlinFileGeneratorHelper.createImports(
                listOf(
                    "com.expediagroup.graphql.generator.annotations.GraphQLDeprecated",
                    "com.expediagroup.graphql.generator.annotations.GraphQLIgnore",
                    "suwayomi.tachidesk.graphql.server.primitives.Node",
                    "suwayomi.tachidesk.server.ServerConfig",
                    "suwayomi.tachidesk.server.serverConfig",
                    "suwayomi.tachidesk.server.settings.SettingsRegistry",
                    "suwayomi.tachidesk.server.settings.UserSettingsRegistry",
                    "suwayomi.tachidesk.server.settings.userConfig",
                    "suwayomi.tachidesk.server.settings.userSettings",
                ),
                settings,
            ),
        )
    }

    private fun StringBuilder.writeSettingsInterface(groupedSettings: Map<String, List<SettingsRegistry.SettingMetadata>>) {
        appendLine("@Suppress(\"DEPRECATION\")")
        appendLine("interface Settings : Node {")

        writeSettings(groupedSettings, indentation = 4, asType = true, isOverride = false, isNullable = true, isInterface = true)

        appendLine("}")
        appendLine()
    }

    private fun StringBuilder.writePartialSettingsType(groupedSettings: Map<String, List<SettingsRegistry.SettingMetadata>>) {
        appendLine("@Suppress(\"DEPRECATION\")")
        appendLine("data class PartialSettingsType(")

        writeSettings(groupedSettings, indentation = 4, asType = true, isOverride = true, isNullable = true, isInterface = false)

        appendLine(") : Settings")
        appendLine()
    }

    private fun StringBuilder.writeSettingsType(groupedSettings: Map<String, List<SettingsRegistry.SettingMetadata>>) {
        appendLine("@Suppress(\"DEPRECATION\")")
        appendLine("class SettingsType(")

        writeSettings(groupedSettings, indentation = 4, asType = true, isOverride = true, isNullable = false, isInterface = false)

        appendLine(") : Settings {")

        // Write secondary constructor
        val indentation = 4
        appendLine("@Suppress(\"UNCHECKED_CAST\")".addIndentation(indentation))
        appendLine("constructor(userId: Int, config: ServerConfig = serverConfig) : this(".addIndentation(indentation))

        writeSettings(
            groupedSettings,
            indentation = indentation * 2,
            asType = false,
            isOverride = false,
            isNullable = false,
            isInterface = false,
        )

        appendLine(")".addIndentation(indentation))

        // Write defaults() factory
        appendLine("companion object {".addIndentation(indentation))
        appendLine("@Suppress(\"UNCHECKED_CAST\")".addIndentation(indentation * 2))
        appendLine("private fun <T> defaultValueOf(name: String): T =".addIndentation(indentation * 2))
        appendLine("SettingsRegistry.get(name)!!.defaultValue as T".addIndentation(indentation * 3))
        appendLine()
        appendLine("@Suppress(\"UNCHECKED_CAST\", \"RemoveExplicitTypeArguments\")".addIndentation(indentation * 2))
        appendLine("fun defaults(): SettingsType =".addIndentation(indentation * 2))
        appendLine("SettingsType(".addIndentation(indentation * 3))

        groupedSettings.forEach { (group, settings) ->
            appendLine("// $group".addIndentation(indentation * 4))
            settings.forEach { setting -> writeDefaultSetting(setting, indentation * 4) }
        }

        appendLine(")".addIndentation(indentation * 3))
        appendLine()

        // Write the masked(userId) factory
        appendLine("@Suppress(\"UNCHECKED_CAST\", \"RemoveExplicitTypeArguments\")".addIndentation(indentation * 2))
        appendLine("fun masked(userId: Int): SettingsType =".addIndentation(indentation * 2))
        appendLine("SettingsType(".addIndentation(indentation * 3))

        groupedSettings.forEach { (group, settings) ->
            appendLine("// $group".addIndentation(indentation * 4))
            settings.forEach { setting -> writeMaskedSetting(setting, indentation * 4) }
        }

        appendLine(")".addIndentation(indentation * 3))
        appendLine("}".addIndentation(indentation))

        appendLine("}")
        appendLine()
    }

    private fun StringBuilder.writeDefaultSetting(
        setting: SettingsRegistry.SettingMetadata,
        indentation: Int,
    ) {
        val gqlType = getGraphQLType(setting, false)
        if (setting.typeInfo.convertToGqlType != null) {
            appendLine(
                ("SettingsRegistry.get(\"${setting.name}\")!!.typeInfo.convertToGqlType!!(" +
                    "defaultValueOf<Any>(\"${setting.name}\")) as $gqlType,").addIndentation(indentation),
            )
        } else {
            appendLine(
                "defaultValueOf<$gqlType>(\"${setting.name}\"),".addIndentation(indentation),
            )
        }
    }

    private fun StringBuilder.writeMaskedSetting(
        setting: SettingsRegistry.SettingMetadata,
        indentation: Int,
    ) {
        // A setting is "moved to per-user" if it is deprecated and has a matching user setting
        val userSetting = UserSettingsRegistry.get(setting.name)
        if (setting.deprecated != null && userSetting != null) {
            val gqlType = getGraphQLType(setting, false)
            if (userSetting.typeInfo?.convertToGqlType != null) {
                appendLine(
                    ("UserSettingsRegistry.get(\"${setting.name}\")!!.typeInfo!!.convertToGqlType!!(" +
                        "userSettings.value(userId, userConfig.${setting.name})) as $gqlType,").addIndentation(indentation),
                )
            } else {
                appendLine(
                    "userSettings.value(userId, userConfig.${setting.name}),".addIndentation(indentation),
                )
            }
            return
        }

        // not moved to per-user: show the default value
        val gqlType = getGraphQLType(setting, false)
        if (setting.typeInfo.convertToGqlType != null) {
            appendLine(
                ("SettingsRegistry.get(\"${setting.name}\")!!.typeInfo.convertToGqlType!!(" +
                    "defaultValueOf<Any>(\"${setting.name}\")) as $gqlType,").addIndentation(indentation),
            )
        } else {
            appendLine(
                "defaultValueOf<$gqlType>(\"${setting.name}\"),".addIndentation(indentation),
            )
        }
    }

    private fun StringBuilder.writeSettings(
        groupedSettings: Map<String, List<SettingsRegistry.SettingMetadata>>,
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
        setting: SettingsRegistry.SettingMetadata,
        indentation: Int,
        asType: Boolean,
        isOverride: Boolean,
        isNullable: Boolean,
        isInterface: Boolean,
    ) {
        val deprecated = setting.deprecated
        if (!asType) {
            if (deprecated != null) {
                // A setting is "moved to per-user" if it is deprecated and has a matching user setting
                val userSetting = UserSettingsRegistry.get(setting.name)
                if (userSetting != null) {
                    val gqlType = getGraphQLType(setting, false)
                    if (userSetting.typeInfo?.convertToGqlType != null) {
                        appendLine(
                            ("UserSettingsRegistry.get(\"${setting.name}\")!!.typeInfo!!.convertToGqlType!!(" +
                                "userSettings.value(userId, userConfig.${setting.name})) as $gqlType,").addIndentation(indentation),
                        )
                    } else {
                        appendLine(
                            "userSettings.value(userId, userConfig.${setting.name}),".addIndentation(indentation),
                        )
                    }
                    return
                }
            }
            appendLine("${getConfigAccess(setting)},".addIndentation(indentation))
            return
        }

        if (setting.requiresRestart) {
            appendLine("@GraphQLIgnore".addIndentation(indentation))
        }

        if (deprecated != null) {
            val replaceWithSuffix = if (deprecated is SettingsRegistry.SettingDeprecated.Migrate) {
                deprecated.replaceWith.let { ", ReplaceWith(\"$it\")" }
            } else {
                ""
            }

            appendLine(
                "@GraphQLDeprecated(\"${deprecated.message}\"$replaceWithSuffix)".addIndentation(
                    indentation,
                ),
            )
        }

        val overridePrefix = if (isOverride) "override " else ""
        val nullableSuffix = if (isNullable) "?" else ""
        val commaSuffix = if (isOverride) "," else ""
        appendLine(
            "${overridePrefix}val ${setting.name}: ${getGraphQLType(
                setting,
                isInterface,
            )}$nullableSuffix$commaSuffix".addIndentation(indentation),
        )
    }

    private fun getGraphQLType(
        setting: SettingsRegistry.SettingMetadata,
        isInterface: Boolean,
    ): String {
        val possibleType = setting.typeInfo.specificType ?: setting.typeInfo.type.simpleName

        val exception = RuntimeException("Unknown setting type: ${setting.typeInfo}")

        if (isInterface) {
            return setting.typeInfo.interfaceType ?: possibleType ?: throw exception
        }

        return possibleType ?: throw exception
    }

    private fun getConfigAccess(setting: SettingsRegistry.SettingMetadata): String {
        if (setting.typeInfo.convertToGqlType != null) {
            return "SettingsRegistry.get(\"${setting.name}\")!!.typeInfo.convertToGqlType!!(" +
                "config.${setting.name}.value" +
                ") as ${getGraphQLType(setting, false)}"
        }

        return "config.${setting.name}.value"
    }
}
