package ireader.core.source

import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.Page

/**
 * Stub LocalSource for backward compatibility.
 * The actual implementation is in LocalSourceImpl in the domain layer.
 */
class LocalSource : Source {
    override val id: Long
        get() = SOURCE_ID
    override val name: String
        get() = "Local Source"
    override val lang: String
        get() = "en"

    companion object {
        const val SOURCE_ID = -200L
        const val LOCAL_FOLDER_NAME = "local"
    }
    
    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        return MangaInfo("","")
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        return emptyList()
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        return emptyList()
    }
}

/**
 * Interface for the full LocalSource implementation.
 * Follows the Tachiyomi/Mihon model:
 * - Scans a designated local folder (.../AppName/local/)
 * - Each subfolder represents a novel series
 * - Each file in a series folder represents a chapter
 * - Supports optional cover.jpg and details.json per series
 */
interface LocalCatalogSource : CatalogSource {
    /**
     * Scans the local directory for novel folders
     */
    suspend fun scanLocalNovels(): List<MangaInfo>
    
    /**
     * Scans a specific novel folder for chapter files
     */
    suspend fun scanNovelChapters(novelKey: String): List<ChapterInfo>
    
    /**
     * Reads the content of a local chapter file
     */
    suspend fun readChapterFile(chapterKey: String): List<Page>
    
    /**
     * Gets the absolute path to the local folder
     */
    fun getLocalFolderPath(): String
}

class LocalSourceException : Exception("this is a local source")

class CorruptedSource(sourceId: Long) : Source {
    override val id: Long
        get() = -201
    override val name: String
        get() = "Local Source"
    override val lang: String
        get() = "en"

    companion object {
        const val SOURCE_ID = -200L
    }
    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        return MangaInfo("","")
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        return emptyList()
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        return emptyList()
    }
}

