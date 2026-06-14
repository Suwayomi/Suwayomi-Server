package suwayomi.tachidesk.graphql.types

data class AboutWebUI(
    val tag: String,
    val repoUrl: String,
    val repoType: RepoType,
    val updateTimestamp: Long,
)

data class WebUIUpdateCheck(
    val tag: String,
    val updateAvailable: Boolean,
)

data class WebUIUpdateInfo(
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
