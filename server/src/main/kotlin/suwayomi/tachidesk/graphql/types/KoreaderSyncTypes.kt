package suwayomi.tachidesk.graphql.types

enum class KoreaderSyncChecksumMethod {
    BINARY,
    FILENAME,
}

enum class KoreaderSyncStrategy {
    PROMPT, // Ask on conflict
    SILENT, // Always use latest
    SEND, // Send changes only
    RECEIVE, // Receive changes only
    DISABLED,
}

data class KoSyncStatusPayload(
    val isLoggedIn: Boolean,
    val username: String?,
)

data class KoSyncConnectPayload(
    val clientMutationId: String?,
    val success: Boolean,
    val message: String?,
    val username: String?,
    val settings: SettingsType,
)

data class LogoutKoSyncAccountPayload(
    val clientMutationId: String?,
    val success: Boolean,
    val settings: SettingsType,
)
