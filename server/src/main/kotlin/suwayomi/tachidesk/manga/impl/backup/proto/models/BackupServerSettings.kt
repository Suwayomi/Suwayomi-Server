package suwayomi.tachidesk.manga.impl.backup.proto.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import org.jetbrains.exposed.sql.SortOrder
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod
import suwayomi.tachidesk.graphql.types.KoreaderSyncStrategy
import suwayomi.tachidesk.graphql.types.Settings
import suwayomi.tachidesk.graphql.types.SettingsDownloadConversion
import suwayomi.tachidesk.graphql.types.WebUIChannel
import suwayomi.tachidesk.graphql.types.WebUIFlavor
import suwayomi.tachidesk.graphql.types.WebUIInterface

@Serializable
data class BackupServerSettings(
    @ProtoNumber(1) override var ip: String,
    @ProtoNumber(2) override var port: Int,
    // socks
    @ProtoNumber(3) override var socksProxyEnabled: Boolean,
    @ProtoNumber(4) override var socksProxyVersion: Int,
    @ProtoNumber(5) override var socksProxyHost: String,
    @ProtoNumber(6) override var socksProxyPort: String,
    @ProtoNumber(7) override var socksProxyUsername: String,
    @ProtoNumber(8) override var socksProxyPassword: String,
    // webUI
    @ProtoNumber(9) override var webUIFlavor: WebUIFlavor,
    @ProtoNumber(10) override var initialOpenInBrowserEnabled: Boolean,
    @ProtoNumber(11) override var webUIInterface: WebUIInterface,
    @ProtoNumber(12) override var electronPath: String,
    @ProtoNumber(13) override var webUIChannel: WebUIChannel,
    @ProtoNumber(14) override var webUIUpdateCheckInterval: Double,
    // downloader
    @ProtoNumber(15) override var downloadAsCbz: Boolean,
    @ProtoNumber(16) override var downloadsPath: String,
    @ProtoNumber(17) override var autoDownloadNewChapters: Boolean,
    @ProtoNumber(18) override var excludeEntryWithUnreadChapters: Boolean,
    @ProtoNumber(19) override var autoDownloadAheadLimit: Int,
    @ProtoNumber(20) override var autoDownloadNewChaptersLimit: Int,
    @ProtoNumber(21) override var autoDownloadIgnoreReUploads: Boolean,
    @ProtoNumber(57) override val downloadConversions: List<BackupSettingsDownloadConversionType>?,
    // extension
    @ProtoNumber(22) override var extensionRepos: List<String>,
    // requests
    @ProtoNumber(23) override var maxSourcesInParallel: Int,
    // updater
    @ProtoNumber(24) override var excludeUnreadChapters: Boolean,
    @ProtoNumber(25) override var excludeNotStarted: Boolean,
    @ProtoNumber(26) override var excludeCompleted: Boolean,
    @ProtoNumber(27) override var globalUpdateInterval: Double,
    @ProtoNumber(28) override var updateMangas: Boolean,
    // Authentication
    @ProtoNumber(56) override var authMode: AuthMode,
    @ProtoNumber(29) override var basicAuthEnabled: Boolean?,
    @ProtoNumber(30) override var authUsername: String,
    @ProtoNumber(31) override var authPassword: String,
    // deprecated
    @ProtoNumber(99991) override var basicAuthUsername: String?,
    @ProtoNumber(99992) override var basicAuthPassword: String?,
    // misc
    @ProtoNumber(32) override var debugLogsEnabled: Boolean,
    @ProtoNumber(33) override var gqlDebugLogsEnabled: Boolean,
    @ProtoNumber(34) override var systemTrayEnabled: Boolean,
    @ProtoNumber(35) override var maxLogFiles: Int,
    @ProtoNumber(36) override var maxLogFileSize: String,
    @ProtoNumber(37) override var maxLogFolderSize: String,
    // backup
    @ProtoNumber(38) override var backupPath: String,
    @ProtoNumber(39) override var backupTime: String,
    @ProtoNumber(40) override var backupInterval: Int,
    @ProtoNumber(41) override var backupTTL: Int,
    // local source
    @ProtoNumber(42) override var localSourcePath: String,
    // cloudflare bypass
    @ProtoNumber(43) override var flareSolverrEnabled: Boolean,
    @ProtoNumber(44) override var flareSolverrUrl: String,
    @ProtoNumber(45) override var flareSolverrTimeout: Int,
    @ProtoNumber(46) override var flareSolverrSessionName: String,
    @ProtoNumber(47) override var flareSolverrSessionTtl: Int,
    @ProtoNumber(48) override var flareSolverrAsResponseFallback: Boolean,
    // opds
    @ProtoNumber(49) override var opdsUseBinaryFileSizes: Boolean,
    @ProtoNumber(50) override var opdsItemsPerPage: Int,
    @ProtoNumber(51) override var opdsEnablePageReadProgress: Boolean,
    @ProtoNumber(52) override var opdsMarkAsReadOnDownload: Boolean,
    @ProtoNumber(53) override var opdsShowOnlyUnreadChapters: Boolean,
    @ProtoNumber(54) override var opdsShowOnlyDownloadedChapters: Boolean,
    @ProtoNumber(55) override var opdsChapterSortOrder: SortOrder,
    // koreader sync
    @ProtoNumber(58) override var koreaderSyncEnabled: Boolean,
    @ProtoNumber(59) override var koreaderSyncServerUrl: String,
    @ProtoNumber(60) override var koreaderSyncUsername: String,
    @ProtoNumber(61) override var koreaderSyncUserkey: String,
    @ProtoNumber(62) override var koreaderSyncDeviceId: String,
    @ProtoNumber(63) override var koreaderSyncChecksumMethod: KoreaderSyncChecksumMethod,
    @ProtoNumber(64) override var koreaderSyncStrategy: KoreaderSyncStrategy,
) : Settings {
    @Serializable
    class BackupSettingsDownloadConversionType(
        @ProtoNumber(1) override val mimeType: String,
        @ProtoNumber(2) override val target: String,
        @ProtoNumber(3) override val compressionLevel: Double?,
    ) : SettingsDownloadConversion
}
