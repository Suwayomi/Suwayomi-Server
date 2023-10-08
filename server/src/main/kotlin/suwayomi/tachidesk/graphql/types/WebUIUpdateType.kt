package suwayomi.tachidesk.graphql.types

data class WebUIUpdateInfo(
    val channel: String,
    val tag: String,
    val updateAvailable: Boolean,
)

enum class UpdateState {
    STOPPED,
    DOWNLOADING,
    FINISHED,
    ERROR,
}

data class WebUIUpdateStatus(
    val info: WebUIUpdateInfo,
    val state: UpdateState,
    val progress: Int,
)
