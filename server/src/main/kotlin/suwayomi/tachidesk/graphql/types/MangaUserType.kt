package suwayomi.tachidesk.graphql.types

import org.jetbrains.exposed.v1.core.ResultRow
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.manga.model.table.MangaUserTable

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
}
