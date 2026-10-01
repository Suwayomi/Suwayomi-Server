package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.core.ResultRow
import suwayomi.tachidesk.graphql.dataLoaders.MangaChapterStats
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import java.util.concurrent.CompletableFuture

class MangaUserType(
    val inLibrary: Boolean,
    val inLibraryAt: Long,
    val mangaId: Int,
) : Node {
    constructor(row: ResultRow) : this(
        row[MangaUserTable.inLibrary],
        row[MangaUserTable.inLibraryAt],
        row[MangaUserTable.manga].value,
    )

    fun downloadCount(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<Int> =
        dataFetchingEnvironment.getValueFromDataLoader<Int, MangaChapterStats>("ChapterFlagCountForMangaDataLoader", mangaId).thenApply {
            it.downloadCount
        }

    fun unreadCount(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<Int> =
        dataFetchingEnvironment.getValueFromDataLoader<Int, MangaChapterStats>("ChapterFlagCountForMangaDataLoader", mangaId).thenApply {
            it.unreadCount
        }

    fun bookmarkCount(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<Int> =
        dataFetchingEnvironment.getValueFromDataLoader<Int, MangaChapterStats>("ChapterFlagCountForMangaDataLoader", mangaId).thenApply {
            it.bookmarkCount
        }

    fun lastReadChapter(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<ChapterType?> =
        dataFetchingEnvironment.getValueFromDataLoader("LastReadChapterForMangaDataLoader", mangaId)

    fun latestReadChapter(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<ChapterType?> =
        dataFetchingEnvironment.getValueFromDataLoader("LatestReadChapterForMangaDataLoader", mangaId)

    fun firstUnreadChapter(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<ChapterType?> =
        dataFetchingEnvironment.getValueFromDataLoader("FirstUnreadChapterForMangaDataLoader", mangaId)
}
