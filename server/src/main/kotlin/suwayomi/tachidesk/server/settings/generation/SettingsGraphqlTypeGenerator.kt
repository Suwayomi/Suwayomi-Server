package suwayomi.tachidesk.server.settings.generation

import suwayomi.tachidesk.server.settings.SettingsRegistry
import java.io.File
import kotlin.text.appendLine

private fun String.addIndentation(times: Int): String = this.prependIndent(" ".repeat(times))

object SettingsGraphqlTypeGenerator {
    fun generate(
        settings: Map<String, SettingsRegistry.SettingMetadata>,
        outputFile: File,
    ) {
        outputFile.parentFile.mkdirs()

        // Type only includes settings that can be mutated during runtime.
        val settingsToInclude = settings.values.filter { it.requiresRestart }

        if (settingsToInclude.isEmpty()) {
            println("Warning: No settings found to create graphql type from.")
            return
        }

        val groupedSettings = settingsToInclude.groupBy { it.group }

        outputFile.writeText(
            buildString {
                writeFileHeader()
                writeImports(groupedSettings.values.flatten())
                writeSettingsInterface(groupedSettings)
                writePartialSettingsType(groupedSettings)
                writeSettingsType(groupedSettings)
            },
        )

        println("Graphql type generated successfully! Total settings: ${settingsToInclude.size}")
    }

    private fun StringBuilder.writeFileHeader() {
        appendLine("@file:Suppress(\"ktlint\")")
        appendLine()
        appendLine("/*")
        appendLine(" * Copyright (C) Contributors to the Suwayomi project")
        appendLine(" *")
        appendLine(" * This Source Code Form is subject to the terms of the Mozilla Public")
        appendLine(" * License, v. 2.0. If a copy of the MPL was not distributed with this")
        appendLine(" * file, You can obtain one at https://mozilla.org/MPL/2.0/. */")
        appendLine()
        appendLine("package suwayomi.tachidesk.graphql.types")
        appendLine()
    }

    private fun StringBuilder.writeImports(settings: List<SettingsRegistry.SettingMetadata>) {
        appendLine("import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated")
        appendLine("import org.jetbrains.exposed.sql.SortOrder")
        appendLine("import suwayomi.tachidesk.graphql.server.primitives.Node")
        appendLine("import suwayomi.tachidesk.server.ServerConfig")
        appendLine("import suwayomi.tachidesk.server.serverConfig")
        appendLine("import suwayomi.tachidesk.server.settings.SettingsRegistry")

        settings
            .mapNotNull { it.typeInfo.imports }
            .flatten()
            .distinct()
            .forEach { appendLine("import $it") }

        appendLine()
    }

    private fun StringBuilder.writeSettingsInterface(groupedSettings: Map<String, List<SettingsRegistry.SettingMetadata>>) {
        appendLine("interface Settings : Node {")

        writeSettings(groupedSettings, indentation = 4, asType = true, isOverride = false, isNullable = true, isInterface = true)

        appendLine("}")
        appendLine()
    }

    private fun StringBuilder.writePartialSettingsType(groupedSettings: Map<String, List<SettingsRegistry.SettingMetadata>>) {
        appendLine("data class PartialSettingsType(")

        writeSettings(groupedSettings, indentation = 4, asType = true, isOverride = true, isNullable = true, isInterface = false)

        appendLine(") : Settings")
        appendLine()
    }

    private fun StringBuilder.writeSettingsType(groupedSettings: Map<String, List<SettingsRegistry.SettingMetadata>>) {
        appendLine("class SettingsType(")

        writeSettings(groupedSettings, indentation = 4, asType = true, isOverride = true, isNullable = false, isInterface = false)

        appendLine(") : Settings {")

        // Write secondary constructor
        val indentation = 4
        appendLine("@Suppress(\"UNCHECKED_CAST\")".addIndentation(indentation))
        appendLine("constructor(config: ServerConfig = serverConfig) : this(".addIndentation(indentation))

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
        if (!asType) {
            appendLine("${getConfigAccess(setting)},".addIndentation(indentation))
            return
        }

        if (setting.deprecated != null) {
            val replaceWithSuffix = setting.deprecated.replaceWith?.let { ", ReplaceWith(\"$it\")" } ?: ""
            appendLine(
                "@GraphQLDeprecated(\"${setting.deprecated.message}\"$replaceWithSuffix)".addIndentation(
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
