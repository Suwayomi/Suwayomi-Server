package suwayomi.tachidesk.manga.impl.download.model

enum class DownloadUpdateType {
    QUEUED,
    DEQUEUED,
    PAUSED,
    STOPPED,
    PROGRESS,
    FINISHED,
    ERROR,
    POSITION,
}

data class DownloadUpdate(
    val type: DownloadUpdateType,
    val downloadQueueItem: DownloadQueueItem,
)
