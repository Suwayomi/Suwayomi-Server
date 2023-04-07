package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.global.model.table.GlobalMetaTable
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
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
    override val edges: List<MetaEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int
) : NodeList() {
    data class MetaEdge(
        override val cursor: Cursor,
        override val node: MetaItem
    ) : Edge()

    companion object {
        fun List<MetaItem>.toNodeList(): MetaNodeList {
            return MetaNodeList(
                nodes = this,
                edges = getEdges(),
                pageInfo = PageInfo(
                    hasNextPage = false,
                    hasPreviousPage = false,
                    startCursor = Cursor(0.toString()),
                    endCursor = Cursor(lastIndex.toString())
                ),
                totalCount = size
            )
        }

        private fun List<MetaItem>.getEdges(): List<MetaEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                MetaEdge(
                    cursor = Cursor("0"),
                    node = first()
                ),
                MetaEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last()
                )
            )
        }
    }
}
