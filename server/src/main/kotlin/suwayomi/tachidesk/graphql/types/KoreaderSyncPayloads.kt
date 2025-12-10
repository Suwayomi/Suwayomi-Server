package suwayomi.tachidesk.graphql.types

data class KoSyncStatusPayload(
    val isLoggedIn: Boolean,
    val serverAddress: String?,
    val username: String?,
)

data class KoSyncConnectPayload(
    val clientMutationId: String?,
    val status: KoSyncStatusPayload,
    val message: String?,
)

data class LogoutKoSyncAccountPayload(
    val clientMutationId: String?,
    val status: KoSyncStatusPayload,
)
