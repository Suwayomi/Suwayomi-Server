package suwayomi.tachidesk.server.settings

import io.github.config4k.ClassContainer
import kotlinx.coroutines.flow.MutableStateFlow
import suwayomi.tachidesk.graphql.types.CbzMediaType
import suwayomi.tachidesk.graphql.types.DownloadConversion
import suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod
import suwayomi.tachidesk.graphql.types.KoreaderSyncConflictStrategy
import suwayomi.tachidesk.graphql.types.SettingsDownloadConversionHeaderType
import suwayomi.tachidesk.graphql.types.SettingsDownloadConversionType
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSettingsDownloadConversionHeaderType
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSettingsDownloadConversionType
import suwayomi.tachidesk.server.serverConfig
import kotlin.reflect.KClass
import org.jetbrains.exposed.v1.core.SortOrder
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf

/**
 * Descriptor for a per-user setting.
 *
 * A per-user setting mirrors a global [suwayomi.tachidesk.server.ServerConfig] setting but is stored per user with the global value acting as a
 * fallback for users without an explicit override.
 *
 * @param key the setting key (matches the global setting name)
 * @param protoNumber explicit proto field number used in the generated [BackupUserSettings]
 * @param group the setting group
 * @param globalFlow accessor for the global fallback value
 * @param validator optional value validator
 * @param typeInfo optional GQL/backup type conversion info (needed for non-primitive types)
 */
class UserSetting<T : Any>(
    val key: String,
    val protoNumber: Int,
    val group: SettingGroup,
    val type: KClass<*>,
    val globalFlow: () -> MutableStateFlow<T>,
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
    noinline globalFlow: () -> MutableStateFlow<T>,
    noinline validator: ((T) -> String?)? = null,
    typeInfo: SettingsRegistry.PartialTypeInfo? = null,
    internalType: String? = null,
): UserSetting<T> {
    val kType = typeOf<T>()
    val setting = UserSetting(
        key = key,
        protoNumber = protoNumber,
        group = group,
        type = T::class,
        globalFlow = globalFlow,
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

/**
 * Per-user settings. [suwayomi.tachidesk.server.ServerConfig] is used for a global value acting as a fallback.
 */
class UserConfig {
    // Library updates
    val excludeUnreadChapters: UserSetting<Boolean> = userSetting(
        key = "excludeUnreadChapters",
        protoNumber = 1,
        group = SettingGroup.LIBRARY_UPDATES,
        globalFlow = { serverConfig.excludeUnreadChapters },
    )

    val excludeNotStarted: UserSetting<Boolean> = userSetting(
        key = "excludeNotStarted",
        protoNumber = 2,
        group = SettingGroup.LIBRARY_UPDATES,
        globalFlow = { serverConfig.excludeNotStarted },
    )

    val excludeCompleted: UserSetting<Boolean> = userSetting(
        key = "excludeCompleted",
        protoNumber = 3,
        group = SettingGroup.LIBRARY_UPDATES,
        globalFlow = { serverConfig.excludeCompleted },
    )

    val updateMangas: UserSetting<Boolean> = userSetting(
        key = "updateMangas",
        protoNumber = 4,
        group = SettingGroup.LIBRARY_UPDATES,
        globalFlow = { serverConfig.updateMangas },
    )

    // Auto-download
    val autoDownloadNewChapters: UserSetting<Boolean> = userSetting(
        key = "autoDownloadNewChapters",
        protoNumber = 5,
        group = SettingGroup.DOWNLOADER,
        globalFlow = { serverConfig.autoDownloadNewChapters },
    )

    val excludeEntryWithUnreadChapters: UserSetting<Boolean> = userSetting(
        key = "excludeEntryWithUnreadChapters",
        protoNumber = 6,
        group = SettingGroup.DOWNLOADER,
        globalFlow = { serverConfig.excludeEntryWithUnreadChapters },
    )

    val autoDownloadNewChaptersLimit: UserSetting<Int> = userSetting(
        key = "autoDownloadNewChaptersLimit",
        protoNumber = 7,
        group = SettingGroup.DOWNLOADER,
        globalFlow = { serverConfig.autoDownloadNewChaptersLimit },
        validator = { value ->
            if (value < 0) "Value must be at least 0" else null
        },
    )

    val autoDownloadIgnoreReUploads: UserSetting<Boolean> = userSetting(
        key = "autoDownloadIgnoreReUploads",
        protoNumber = 8,
        group = SettingGroup.DOWNLOADER,
        globalFlow = { serverConfig.autoDownloadIgnoreReUploads },
    )

    // OPDS
    val opdsItemsPerPage: UserSetting<Int> = userSetting(
        key = "opdsItemsPerPage",
        protoNumber = 9,
        group = SettingGroup.OPDS,
        globalFlow = { serverConfig.opdsItemsPerPage },
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
        globalFlow = { serverConfig.opdsShowOnlyUnreadChapters },
    )

    val opdsShowOnlyDownloadedChapters: UserSetting<Boolean> = userSetting(
        key = "opdsShowOnlyDownloadedChapters",
        protoNumber = 11,
        group = SettingGroup.OPDS,
        globalFlow = { serverConfig.opdsShowOnlyDownloadedChapters },
    )

    val opdsChapterSortOrder: UserSetting<SortOrder> = userSetting(
        key = "opdsChapterSortOrder",
        protoNumber = 12,
        group = SettingGroup.OPDS,
        globalFlow = { serverConfig.opdsChapterSortOrder },
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("org.jetbrains.exposed.v1.core.SortOrder")),
    )

    val opdsMarkAsReadOnDownload: UserSetting<Boolean> = userSetting(
        key = "opdsMarkAsReadOnDownload",
        protoNumber = 13,
        group = SettingGroup.OPDS,
        globalFlow = { serverConfig.opdsMarkAsReadOnDownload },
    )

    val opdsEnablePageReadProgress: UserSetting<Boolean> = userSetting(
        key = "opdsEnablePageReadProgress",
        protoNumber = 14,
        group = SettingGroup.OPDS,
        globalFlow = { serverConfig.opdsEnablePageReadProgress },
    )

    val opdsUseBinaryFileSizes: UserSetting<Boolean> = userSetting(
        key = "opdsUseBinaryFileSizes",
        protoNumber = 15,
        group = SettingGroup.OPDS,
        globalFlow = { serverConfig.opdsUseBinaryFileSizes },
    )

    val opdsCbzMimetype: UserSetting<CbzMediaType> = userSetting(
        key = "opdsCbzMimetype",
        protoNumber = 16,
        group = SettingGroup.OPDS,
        globalFlow = { serverConfig.opdsCbzMimetype },
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("suwayomi.tachidesk.graphql.types.CbzMediaType")),
    )

    val opdsSkipChapterMetadataFeed: UserSetting<Boolean> = userSetting(
        key = "opdsSkipChapterMetadataFeed",
        protoNumber = 17,
        group = SettingGroup.OPDS,
        globalFlow = { serverConfig.opdsSkipChapterMetadataFeed },
    )

    // KOReader sync behavior
    val koreaderSyncChecksumMethod: UserSetting<KoreaderSyncChecksumMethod> = userSetting(
        key = "koreaderSyncChecksumMethod",
        protoNumber = 18,
        group = SettingGroup.KOREADER_SYNC,
        globalFlow = { serverConfig.koreaderSyncChecksumMethod },
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod")),
    )

    val koreaderSyncStrategyForward: UserSetting<KoreaderSyncConflictStrategy> = userSetting(
        key = "koreaderSyncStrategyForward",
        protoNumber = 19,
        group = SettingGroup.KOREADER_SYNC,
        globalFlow = { serverConfig.koreaderSyncStrategyForward },
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("suwayomi.tachidesk.graphql.types.KoreaderSyncConflictStrategy")),
    )

    val koreaderSyncStrategyBackward: UserSetting<KoreaderSyncConflictStrategy> = userSetting(
        key = "koreaderSyncStrategyBackward",
        protoNumber = 20,
        group = SettingGroup.KOREADER_SYNC,
        globalFlow = { serverConfig.koreaderSyncStrategyBackward },
        typeInfo = SettingsRegistry.PartialTypeInfo(imports = listOf("suwayomi.tachidesk.graphql.types.KoreaderSyncConflictStrategy")),
    )

    val koreaderSyncPercentageTolerance: UserSetting<Double> = userSetting(
        key = "koreaderSyncPercentageTolerance",
        protoNumber = 21,
        group = SettingGroup.KOREADER_SYNC,
        globalFlow = { serverConfig.koreaderSyncPercentageTolerance },
        validator = { value ->
            when {
                value < 0.000000000000001 -> "Value must be at least 0.000000000000001"
                value > 1.0 -> "Value must not exceed 1.0"
                else -> null
            }
        },
    )

    // Download conversions served to clients
    val serveConversions: UserSetting<Map<String, DownloadConversion>> = userSetting(
        key = "serveConversions",
        protoNumber = 22,
        group = SettingGroup.DOWNLOADER,
        globalFlow = { serverConfig.serveConversions },
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
}

val userConfig: UserConfig by lazy { UserConfig() }
