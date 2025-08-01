package suwayomi.tachidesk.graphql.types

enum class KoreaderSyncChecksumMethod {
    BINARY,
    FILENAME,
}

enum class KoreaderSyncStrategy {
    LATEST,
    SUWAYOMI,
    KOSYNC,
}

data class KoSyncStatusPayload(
    val isLoggedIn: Boolean,
    val username: String?,
)

data class KoSyncConnectPayload(
    val success: Boolean,
    val message: String?,
    val username: String?,
)
