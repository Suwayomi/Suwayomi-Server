package suwayomi.tachidesk.graphql.types

data class AboutWebUI(
    val channel: WebUIChannel,
    val tag: String,
    val updateTimestamp: Long,
)

data class WebUIUpdateCheck(
    val channel: WebUIChannel,
    val tag: String,
    val updateAvailable: Boolean,
)

data class WebUIUpdateInfo(
    val channel: WebUIChannel,
    val tag: String,
)

enum class UpdateState {
    IDLE,
    DOWNLOADING,
    FINISHED,
    ERROR,
}

data class WebUIUpdateStatus(
    val info: WebUIUpdateInfo,
    val state: UpdateState,
    val progress: Int,
)
