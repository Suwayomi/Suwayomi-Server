package suwayomi.tachidesk.manga.impl.download.fileProvider

import kotlinx.coroutines.CoroutineScope
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import java.io.InputStream

/*
* Base class for downloaded chapter files provider, example: Folder, Archive
* */
abstract class ChaptersFilesProvider(val mangaId: Int, val chapterId: Int) : DownloadedFilesProvider {
    abstract fun getImageImpl(index: Int): Pair<InputStream, String>

    override fun getImage(): RetrieveFile1Args<Int> {
        return object : RetrieveFile1Args<Int> {
            override fun execute(a: Int): Pair<InputStream, String> {
                return getImageImpl(a)
            }
        }
    }

    abstract suspend fun downloadImpl(
        download: DownloadChapter,
        scope: CoroutineScope,
        step: suspend (DownloadChapter?, Boolean) -> Unit
    ): Boolean

    override fun download(): FileDownload3Args<DownloadChapter, CoroutineScope, suspend (DownloadChapter?, Boolean) -> Unit> {
        return object : FileDownload3Args<DownloadChapter, CoroutineScope, suspend (DownloadChapter?, Boolean) -> Unit> {
            override suspend fun execute(
                a: DownloadChapter,
                b: CoroutineScope,
                c: suspend (DownloadChapter?, Boolean) -> Unit
            ): Boolean {
                return downloadImpl(a, b, c)
            }
        }
    }

    abstract override fun delete(): Boolean
}
