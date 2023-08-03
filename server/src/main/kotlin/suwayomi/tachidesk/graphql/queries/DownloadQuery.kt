package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.types.DownloadStatus
import suwayomi.tachidesk.manga.impl.download.DownloadManager

class DownloadQuery {

    fun downloadStatus(): DownloadStatus {
        return DownloadStatus(DownloadManager.status.value)
    }
}
