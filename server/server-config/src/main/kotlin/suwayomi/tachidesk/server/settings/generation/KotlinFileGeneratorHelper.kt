package suwayomi.tachidesk.server.settings.generation

import suwayomi.tachidesk.server.settings.SettingsRegistry

internal fun String.addIndentation(times: Int): String = this.prependIndent(" ".repeat(times))

object KotlinFileGeneratorHelper {
    fun createFileHeader(packageName: String): String =
        buildString {
            appendLine("@file:Suppress(\"ktlint\")")
            appendLine()
            appendLine("/*")
            appendLine(" * Copyright (C) Contributors to the Suwayomi project")
            appendLine(" *")
            appendLine(" * This Source Code Form is subject to the terms of the Mozilla Public")
            appendLine(" * License, v. 2.0. If a copy of the MPL was not distributed with this")
            appendLine(" * file, You can obtain one at https://mozilla.org/MPL/2.0/. */")
            appendLine()
            appendLine("package $packageName")
            appendLine()
        }

    fun createImports(
        staticImports: List<String>,
        settings: List<SettingsRegistry.SettingMetadata>,
    ): String =
        buildString {
            staticImports.forEach { appendLine("import $it") }
            settings
                .mapNotNull { it.typeInfo.imports }
                .flatten()
                .distinct()
                .forEach { appendLine("import $it") }

            appendLine()
        }
}
