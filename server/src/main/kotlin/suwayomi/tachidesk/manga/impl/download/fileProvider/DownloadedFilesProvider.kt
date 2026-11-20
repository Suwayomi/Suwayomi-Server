package suwayomi.tachidesk.manga.impl.download.fileProvider

interface DownloadedFilesProvider :
    FileDownloader,
    FileRetriever {
    suspend fun delete(): Boolean
}
