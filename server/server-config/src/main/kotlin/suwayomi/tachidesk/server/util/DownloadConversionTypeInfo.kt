package suwayomi.tachidesk.server.util

import suwayomi.tachidesk.graphql.types.DownloadConversion
import suwayomi.tachidesk.graphql.types.SettingsDownloadConversionHeaderType
import suwayomi.tachidesk.graphql.types.SettingsDownloadConversionType
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSettingsDownloadConversionHeaderType
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSettingsDownloadConversionType
import suwayomi.tachidesk.server.settings.SettingsRegistry

internal fun downloadConversionTypeInfo(includeRestoreLegacy: Boolean = false) = SettingsRegistry.PartialTypeInfo(
    specificType = "List<SettingsDownloadConversionType>",
    interfaceType = "List<SettingsDownloadConversion>",
    backupType = "List<BackupSettingsDownloadConversionType>",
    imports =
        listOf(
            "suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSettingsDownloadConversionType",
        ),
    convertToGqlType = { value ->
        @Suppress("UNCHECKED_CAST")
        val castedValue = value as Map<String, DownloadConversion>

        castedValue.map {
            SettingsDownloadConversionType(
                it.key,
                it.value.target,
                it.value.compressionLevel,
                it.value.callTimeout,
                it.value.connectTimeout,
                it.value.headers?.map { header ->
                    SettingsDownloadConversionHeaderType(
                        header.key,
                        header.value,
                    )
                },
            )
        }
    },
    convertToInternalType = { list ->
        @Suppress("UNCHECKED_CAST")
        val castedList = list as List<SettingsDownloadConversionType>

        castedList.associate {
            it.mimeType to
                DownloadConversion(
                    target = it.target,
                    compressionLevel = it.compressionLevel,
                    callTimeout = it.callTimeout,
                    connectTimeout = it.connectTimeout,
                    headers = it.headers?.associate { header ->
                        header.name to header.value
                    },
                )
        }
    },
    convertToBackupType = { value ->
        @Suppress("UNCHECKED_CAST")
        val castedValue = value as Map<String, DownloadConversion>

        castedValue.map {
            BackupSettingsDownloadConversionType(
                it.key,
                it.value.target,
                it.value.compressionLevel,
                it.value.callTimeout,
                it.value.connectTimeout,
                it.value.headers?.map { header ->
                    BackupSettingsDownloadConversionHeaderType(
                        header.key,
                        header.value,
                    )
                },
            )
        }
    },
    restoreLegacy = if (includeRestoreLegacy) { backupValue ->
        @Suppress("UNCHECKED_CAST")
        (backupValue as? List<BackupSettingsDownloadConversionType>)?.associate {
            it.mimeType to
                DownloadConversion(
                    target = it.target,
                    compressionLevel = it.compressionLevel,
                    callTimeout = it.callTimeout,
                    connectTimeout = it.connectTimeout,
                    headers = it.headers?.associate { header ->
                        header.name to header.value
                    },
                )
        }
    } else null,
)
