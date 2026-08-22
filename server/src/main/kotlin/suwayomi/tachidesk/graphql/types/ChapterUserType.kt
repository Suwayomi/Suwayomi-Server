package suwayomi.tachidesk.graphql.types

import org.jetbrains.exposed.v1.core.ResultRow
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable

class ChapterUserType(
    val isRead: Boolean,
    val isBookmarked: Boolean,
    val lastPageRead: Int,
    val lastReadAt: Long,
    val isDownloaded: Boolean,
    val isDownloadRequested: Boolean,
    val chapterId: Int,
) : Node {
    constructor(row: ResultRow) : this(
        row[ChapterUserTable.isRead],
        row[ChapterUserTable.isBookmarked],
        row[ChapterUserTable.lastPageRead],
        row[ChapterUserTable.lastReadAt],
        row[ChapterUserTable.isDownloaded],
        row[ChapterUserTable.isDownloadRequested],
        row[ChapterUserTable.chapter].value,
    )
}
