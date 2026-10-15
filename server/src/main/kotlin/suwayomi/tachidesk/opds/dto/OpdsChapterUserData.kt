package suwayomi.tachidesk.opds.dto

import org.jetbrains.exposed.v1.core.ResultRow
import suwayomi.tachidesk.manga.model.table.ChapterUserTable

/**
 * OPDS-local projection of a `CHAPTERUSER` row. Decouples the opds repositories from the
 * GraphQL layer (`graphql.types.ChapterUserType`), which is not a public model API.
 */
data class OpdsChapterUserData(
    val isRead: Boolean,
    val isBookmarked: Boolean,
    val lastPageRead: Int,
    val lastReadAt: Long,
    val isDownloaded: Boolean,
) {
    constructor(row: ResultRow) : this(
        row[ChapterUserTable.isRead],
        row[ChapterUserTable.isBookmarked],
        row[ChapterUserTable.lastPageRead],
        row[ChapterUserTable.lastReadAt],
        row[ChapterUserTable.isDownloaded],
    )
}
