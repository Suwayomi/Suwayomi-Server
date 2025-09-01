package suwayomi.tachidesk.graphql.types

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
