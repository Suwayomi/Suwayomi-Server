package suwayomi.tachidesk.manga.impl.download

import kotlinx.coroutines.CoroutineScope
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import java.io.InputStream
import kotlin.reflect.KSuspendFunction2

/*
* Base class for downloaded chapter files provider, example: Folder, Archive
* */
abstract class DownloadedFilesProvider(val mangaId: Int, val chapterId: Int) {
    abstract fun getImage(index: Int): Pair<InputStream, String>

    abstract suspend fun download(
        download: DownloadChapter,
        scope: CoroutineScope,
        step: KSuspendFunction2<DownloadChapter?, Boolean, Unit>
    ): Boolean

    abstract fun delete(): Boolean
}
