package suwayomi.tachidesk.manga.impl.download.model

enum class DownloadUpdateType {
    QUEUED,
    DEQUEUED,
    PROGRESS,
    FINISHED,
    ERROR,
    POSITION,
}

data class DownloadUpdate(
    val type: DownloadUpdateType,
    val downloadChapter: DownloadChapter,
)
