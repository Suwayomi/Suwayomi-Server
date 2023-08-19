package suwayomi.tachidesk.manga.impl.download.fileProvider

interface DownloadedFilesProvider : FileDownloader, FileRetriever {
    fun delete(): Boolean
}
