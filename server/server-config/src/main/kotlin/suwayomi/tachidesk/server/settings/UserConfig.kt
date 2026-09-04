package suwayomi.tachidesk.server.settings

import io.github.config4k.ClassContainer
import org.jetbrains.exposed.v1.core.SortOrder
import suwayomi.tachidesk.graphql.types.CbzMediaType
import suwayomi.tachidesk.graphql.types.DownloadConversion
import suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod
import suwayomi.tachidesk.graphql.types.KoreaderSyncConflictStrategy
import suwayomi.tachidesk.graphql.types.SettingsDownloadConversionHeaderType
import suwayomi.tachidesk.graphql.types.SettingsDownloadConversionType
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSettingsDownloadConversionHeaderType
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSettingsDownloadConversionType
import kotlin.reflect.KClass
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Descriptor for a per-user setting.
 *
 * @param key the setting key (matches the global setting name)
 * @param protoNumber explicit proto field number used in the generated [BackupUserSettings]
 * @param group the setting group
 * @param type the kclass type
 * @param defaultValue the default value
 * @param validator optional value validator
 * @param typeInfo optional GQL/backup type conversion info (needed for non-primitive types)
 */
class UserSetting<T : Any>(
    val key: String,
    val protoNumber: Int,
    val group: SettingGroup,
    val type: KClass<*>,
    val defaultValue: T,
    val validator: ((T) -> String?)? = null,
    val typeInfo: SettingsRegistry.PartialTypeInfo? = null,
    val internalType: String? = null,
    val typeArguments: Map<String, ClassContainer> = emptyMap(),
)

/**
 * Registry to track all per-user settings for code generation.
 */
object UserSettingsRegistry {
    private val settings = mutableMapOf<String, UserSetting<*>>()

    fun register(setting: UserSetting<*>) {
        settings.values.find { it.protoNumber == setting.protoNumber }?.let {
            throw IllegalStateException("User setting ${setting.key} uses protoNumber ${it.protoNumber} already used by ${it.key}")
        }
        settings[setting.key] = setting
    }

    fun get(key: String): UserSetting<*>? = settings[key]

    fun getAll(): Map<String, UserSetting<*>> = settings.toMap()
}

/**
 * Creates a [UserSetting], registers it in [UserSettingsRegistry], and returns it. Called from [UserConfig] property
 * initializers so that registration happens when [UserConfig] is constructed.
 */
private inline fun <reified T : Any> userSetting(
    key: String,
    protoNumber: Int,
    group: SettingGroup,
    defaultValue: T,
    noinline validator: ((T) -> String?)? = null,
    typeInfo: SettingsRegistry.PartialTypeInfo? = null,
    internalType: String? = null,
): UserSetting<T> {
    val kType = typeOf<T>()
    val setting = UserSetting(
        key = key,
        protoNumber = protoNumber,
        group = group,
        defaultValue = defaultValue,
        type = T::class,
        validator = validator,
        typeInfo = typeInfo,
        internalType = internalType,
        typeArguments = T::class.typeParameters.mapIndexed { index, parameter ->
            parameter.name to ClassContainer(kType.arguments[index].type?.classifier as KClass<*>)
        }.toMap()
    )
    UserSettingsRegistry.register(setting)
    return setting
}

class UserConfig {
    val excludeUnreadChapters: UserSetting<Boolean> = userSetting(
        key = "excludeUnreadChapters",
        protoNumber = 1,
        group = SettingGroup.LIBRARY_UPDATES,
        defaultValue = true,
    )

    val excludeNotStarted: UserSetting<Boolean> = userSetting(
        key = "excludeNotStarted",
        protoNumber = 2,
        group = SettingGroup.LIBRARY_UPDATES,
        defaultValue = true,
    )

    val excludeCompleted: UserSetting<Boolean> = userSetting(
        key = "excludeCompleted",
        protoNumber = 3,
        group = SettingGroup.LIBRARY_UPDATES,
        defaultValue = true,
    )

    val updateMangas: UserSetting<Boolean> = userSetting(
        key = "updateMangas",
        protoNumber = 4,
        group = SettingGroup.LIBRARY_UPDATES,
        defaultValue = false,
    )

    val autoDownloadNewChapters: UserSetting<Boolean> = userSetting(
        key = "autoDownloadNewChapters",
        protoNumber = 5,
        group = SettingGroup.DOWNLOADER,
        defaultValue = false,
    )

    val excludeEntryWithUnreadChapters: UserSetting<Boolean> = userSetting(
        key = "excludeEntryWithUnreadChapters",
        protoNumber = 6,
        group = SettingGroup.DOWNLOADER,
        defaultValue = true,
    )

    val autoDownloadNewChaptersLimit: UserSetting<Int> = userSetting(
        key = "autoDownloadNewChaptersLimit",
        protoNumber = 7,
        group = SettingGroup.DOWNLOADER,
        defaultValue = 0,
        validator = { value ->
            if (value < 0) "Value must be at least 0" else null
        },
    )

    val autoDownloadIgnoreReUploads: UserSetting<Boolean> = userSetting(
        key = "autoDownloadIgnoreReUploads",
        protoNumber = 8,
        group = SettingGroup.DOWNLOADER,
        defaultValue = false,
    )

    val opdsItemsPerPage: UserSetting<Int> = userSetting(
        key = "opdsItemsPerPage",
        protoNumber = 9,
        group = SettingGroup.OPDS,
        defaultValue = 100,
        validator = { value ->
            when {
                value < 10 -> "Value must be at least 10"
                value > 5000 -> "Value must not exceed 5000"
                else -> null
            }
        },
    )

    val opdsShowOnlyUnreadChapters: UserSetting<Boolean> = userSetting(
        key = "opdsShowOnlyUnreadChapters",
        protoNumber = 10,
        group = SettingGroup.OPDS,
        defaultValue = false,
    )

    val opdsShowOnlyDownloadedChapters: UserSetting<Boolean> = userSetting(
        key = "opdsShowOnlyDownloadedChapters",
        protoNumber = 11,
        group = SettingGroup.OPDS,
        defaultValue = false,
    )

    val opdsChapterSortOrder: UserSetting<SortOrder> = userSetting(
        key = "opdsChapterSortOrder",
        protoNumber = 12,
        group = SettingGroup.OPDS,
        defaultValue = SortOrder.DESC,
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("org.jetbrains.exposed.v1.core.SortOrder")),
    )

    val opdsMarkAsReadOnDownload: UserSetting<Boolean> = userSetting(
        key = "opdsMarkAsReadOnDownload",
        protoNumber = 13,
        group = SettingGroup.OPDS,
        defaultValue = false,
    )

    val opdsEnablePageReadProgress: UserSetting<Boolean> = userSetting(
        key = "opdsEnablePageReadProgress",
        protoNumber = 14,
        group = SettingGroup.OPDS,
        defaultValue = true,
    )

    val opdsUseBinaryFileSizes: UserSetting<Boolean> = userSetting(
        key = "opdsUseBinaryFileSizes",
        protoNumber = 15,
        group = SettingGroup.OPDS,
        defaultValue = false,
    )

    val opdsCbzMimetype: UserSetting<CbzMediaType> = userSetting(
        key = "opdsCbzMimetype",
        protoNumber = 16,
        group = SettingGroup.OPDS,
        defaultValue = CbzMediaType.MODERN,
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("suwayomi.tachidesk.graphql.types.CbzMediaType")),
    )

    val opdsSkipChapterMetadataFeed: UserSetting<Boolean> = userSetting(
        key = "opdsSkipChapterMetadataFeed",
        protoNumber = 17,
        group = SettingGroup.OPDS,
        defaultValue = false,
    )

    val koreaderSyncChecksumMethod: UserSetting<KoreaderSyncChecksumMethod> = userSetting(
        key = "koreaderSyncChecksumMethod",
        protoNumber = 18,
        group = SettingGroup.KOREADER_SYNC,
        defaultValue = KoreaderSyncChecksumMethod.BINARY,
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod")),
    )

    val koreaderSyncStrategyForward: UserSetting<KoreaderSyncConflictStrategy> = userSetting(
        key = "koreaderSyncStrategyForward",
        protoNumber = 19,
        group = SettingGroup.KOREADER_SYNC,
        defaultValue = KoreaderSyncConflictStrategy.PROMPT,
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("suwayomi.tachidesk.graphql.types.KoreaderSyncConflictStrategy")),
    )

    val koreaderSyncStrategyBackward: UserSetting<KoreaderSyncConflictStrategy> = userSetting(
        key = "koreaderSyncStrategyBackward",
        protoNumber = 20,
        group = SettingGroup.KOREADER_SYNC,
        defaultValue = KoreaderSyncConflictStrategy.DISABLED,
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("suwayomi.tachidesk.graphql.types.KoreaderSyncConflictStrategy")),
    )

    val koreaderSyncPercentageTolerance: UserSetting<Double> = userSetting(
        key = "koreaderSyncPercentageTolerance",
        protoNumber = 21,
        group = SettingGroup.KOREADER_SYNC,
        defaultValue = 0.000000000000001,
        validator = { value ->
            when {
                value < 0.000000000000001 -> "Value must be at least 0.000000000000001"
                value > 1.0 -> "Value must not exceed 1.0"
                else -> null
            }
        },
    )

    val serveConversions: UserSetting<Map<String, DownloadConversion>> = userSetting(
        key = "serveConversions",
        protoNumber = 22,
        group = SettingGroup.DOWNLOADER,
        defaultValue = emptyMap(),
        typeInfo =
            SettingsRegistry.PartialTypeInfo(
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
                restoreLegacy = { backupValue ->
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
                },
            ),
        internalType = "Map<String, DownloadConversion>",
    )

    val syncYomiEnabled: UserSetting<Boolean> = userSetting(
        key = "syncYomiEnabled",
        protoNumber = 23,
        group = SettingGroup.SYNCYOMI,
        defaultValue = false,
    )

    val syncInterval: UserSetting<Duration> = userSetting(
        key = "syncInterval",
        protoNumber = 24,
        group = SettingGroup.SYNCYOMI,
        defaultValue = 0.seconds,
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("kotlin.time.Duration")),
    )

    val syncYomiHost: UserSetting<String> = userSetting(
        key = "syncYomiHost",
        protoNumber = 25,
        group = SettingGroup.SYNCYOMI,
        defaultValue = "",
    )

    val syncYomiApiKey: UserSetting<String> = userSetting(
        key = "syncYomiApiKey",
        protoNumber = 26,
        group = SettingGroup.SYNCYOMI,
        defaultValue = "",
    )

    val syncDataManga: UserSetting<Boolean> = userSetting(
        key = "syncDataManga",
        protoNumber = 27,
        group = SettingGroup.SYNCYOMI,
        defaultValue = true,
    )

    val syncDataChapters: UserSetting<Boolean> = userSetting(
        key = "syncDataChapters",
        protoNumber = 28,
        group = SettingGroup.SYNCYOMI,
        defaultValue = true,
    )

    val syncDataTracking: UserSetting<Boolean> = userSetting(
        key = "syncDataTracking",
        protoNumber = 29,
        group = SettingGroup.SYNCYOMI,
        defaultValue = true,
    )

    val syncDataHistory: UserSetting<Boolean> = userSetting(
        key = "syncDataHistory",
        protoNumber = 30,
        group = SettingGroup.SYNCYOMI,
        defaultValue = true,
    )

    val syncDataCategories: UserSetting<Boolean> = userSetting(
        key = "syncDataCategories",
        protoNumber = 31,
        group = SettingGroup.SYNCYOMI,
        defaultValue = true,
    )
}

val userConfig: UserConfig by lazy { UserConfig() }
