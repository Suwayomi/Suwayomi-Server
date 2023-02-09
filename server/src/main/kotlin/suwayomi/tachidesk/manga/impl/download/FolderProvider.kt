package suwayomi.tachidesk.manga.impl.download

import suwayomi.tachidesk.manga.impl.Page.getPageName
import suwayomi.tachidesk.manga.impl.util.getChapterDir
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/*
* Provides downloaded files when pages were downloaded into folders
* */
class FolderProvider(mangaId: Int, chapterId: Int) : DownloadedFilesProvider(mangaId, chapterId) {
    override fun getImage(index: Int): Pair<InputStream, String> {
        val chapterDir = getChapterDir(mangaId, chapterId)
        val folder = File(chapterDir)
        folder.mkdirs()
        val file = folder.listFiles()?.get(index)
        val fileType = file!!.name.substringAfterLast(".")
        return Pair(FileInputStream(file).buffered(), "image/$fileType")
    }

    override fun putImage(index: Int, image: InputStream): Boolean {
        val chapterDir = getChapterDir(mangaId, chapterId)
        val folder = File(chapterDir)
        folder.mkdirs()
        val fileName = getPageName(index)
        val filePath = "$chapterDir/$fileName"
        ImageResponse.saveImage(filePath, image)
        return true
    }

    override fun delete(): Boolean {
        val chapterDir = getChapterDir(mangaId, chapterId)
        return File(chapterDir).deleteRecursively()
    }
}
