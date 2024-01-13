package suwayomi.tachidesk.manga.impl.download.fileProvider

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import suwayomi.tachidesk.manga.impl.Page
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import java.io.File
import java.io.InputStream

/*
* Base class for downloaded chapter files provider, example: Folder, Archive
*/
abstract class ChaptersFilesProvider(val mangaId: Int, val chapterId: Int) : DownloadedFilesProvider {
    abstract fun getImageImpl(index: Int): Pair<InputStream, String>

    override fun getImage(): RetrieveFile1Args<Int> {
        return RetrieveFile1Args(::getImageImpl)
    }

    @OptIn(FlowPreview::class)
    open suspend fun downloadImpl(
        download: DownloadChapter,
        scope: CoroutineScope,
        step: suspend (DownloadChapter?, Boolean) -> Unit,
    ): Boolean {
        val pageCount = download.chapter.pageCount
        val chapterDir = getChapterCachePath(mangaId, chapterId)
        val folder = File(chapterDir)
        folder.mkdirs()

        for (pageNum in 0 until pageCount) {
            var pageProgressJob: Job? = null
            val fileName = Page.getPageName(pageNum) // might have to change this to index stored in database
            if (File(folder, fileName).exists()) continue
            try {
                Page.getPageImage(
                    mangaId = download.mangaId,
                    chapterIndex = download.chapterIndex,
                    index = pageNum,
                ) { flow ->
                    pageProgressJob =
                        flow
                            .sample(100)
                            .distinctUntilChanged()
                            .onEach {
                                download.progress = (pageNum.toFloat() + (it.toFloat() * 0.01f)) / pageCount
                                step(null, false) // don't throw on canceled download here since we can't do anything
                            }
                            .launchIn(scope)
                }
            } finally {
                // always cancel the page progress job even if it throws an exception to avoid memory leaks
                pageProgressJob?.cancel()
            }
            // TODO: retry on error with 2,4,8 seconds of wait
            download.progress = ((pageNum + 1).toFloat()) / pageCount
            step(download, false)
        }
        return true
    }

    override fun download(): FileDownload3Args<DownloadChapter, CoroutineScope, suspend (DownloadChapter?, Boolean) -> Unit> {
        return FileDownload3Args(::downloadImpl)
    }

    abstract override fun delete(): Boolean
}
