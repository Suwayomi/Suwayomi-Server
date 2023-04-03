package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.global.model.table.GlobalMetaTable
import suwayomi.tachidesk.manga.model.table.CategoryMetaTable
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.MangaMetaTable

open class MetaItem(
    val key: String,
    val value: String,
    @GraphQLIgnore
    val ref: Int?
) : Node

class ChapterMetaItem(
    private val row: ResultRow
) : MetaItem(row[ChapterMetaTable.key], row[ChapterMetaTable.value], row[ChapterMetaTable.ref].value)

class MangaMetaItem(
    private val row: ResultRow
) : MetaItem(row[MangaMetaTable.key], row[MangaMetaTable.value], row[MangaMetaTable.ref].value)

class CategoryMetaItem(
    private val row: ResultRow
) : MetaItem(row[CategoryMetaTable.key], row[CategoryMetaTable.value], row[CategoryMetaTable.ref].value)

class GlobalMetaItem(
    private val row: ResultRow
) : MetaItem(row[GlobalMetaTable.key], row[GlobalMetaTable.value], null)

data class MetaNodeList(
    override val nodes: List<MetaItem>,
    override val edges: MetaEdges,
    override val pageInfo: PageInfo,
    override val totalCount: Int
) : NodeList() {
    data class MetaEdges(
        override val cursor: Cursor,
        override val node: MetaItem?
    ) : Edges()

    companion object {
        fun List<MetaItem>.toNodeList(): MetaNodeList {
            return MetaNodeList(
                nodes = this,
                edges = MetaEdges(
                    cursor = lastIndex,
                    node = lastOrNull()
                ),
                pageInfo = PageInfo(
                    hasNextPage = false,
                    hasPreviousPage = false,
                    startCursor = 0,
                    endCursor = lastIndex
                ),
                totalCount = size
            )
        }
    }
}
