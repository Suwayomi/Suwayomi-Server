package suwayomi.tachidesk.graphql.types

data class KoSyncStatusPayload(
    val isLoggedIn: Boolean,
    val username: String?,
)

data class KoSyncConnectPayload(
    val success: Boolean,
    val message: String?,
    val username: String?,
)
