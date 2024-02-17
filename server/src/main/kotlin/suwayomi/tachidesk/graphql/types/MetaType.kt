package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
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
import suwayomi.tachidesk.manga.model.table.SourceMetaTable
import java.util.concurrent.CompletableFuture

interface MetaType : Node {
    val key: String
    val value: String
}

class ChapterMetaType(
    override val key: String,
    override val value: String,
    val chapterId: Int,
) : MetaType {
    constructor(row: ResultRow) : this(
        key = row[ChapterMetaTable.key],
        value = row[ChapterMetaTable.value],
        chapterId = row[ChapterMetaTable.ref].value,
    )

    fun chapter(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<ChapterType> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, ChapterType>("ChapterDataLoader", chapterId)
    }
}

class MangaMetaType(
    override val key: String,
    override val value: String,
    val mangaId: Int,
) : MetaType {
    constructor(row: ResultRow) : this(
        key = row[MangaMetaTable.key],
        value = row[MangaMetaTable.value],
        mangaId = row[MangaMetaTable.ref].value,
    )

    fun manga(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<MangaType> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, MangaType>("MangaDataLoader", mangaId)
    }
}

class CategoryMetaType(
    override val key: String,
    override val value: String,
    val categoryId: Int,
) : MetaType {
    constructor(row: ResultRow) : this(
        key = row[CategoryMetaTable.key],
        value = row[CategoryMetaTable.value],
        categoryId = row[CategoryMetaTable.ref].value,
    )

    fun category(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<CategoryType> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, CategoryType>("CategoryDataLoader", categoryId)
    }
}

class SourceMetaType(
    override val key: String,
    override val value: String,
    val sourceId: Long,
) : MetaType {
    constructor(row: ResultRow) : this(
        key = row[SourceMetaTable.key],
        value = row[SourceMetaTable.value],
        sourceId = row[SourceMetaTable.ref],
    )

    fun source(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<SourceType> {
        return dataFetchingEnvironment.getValueFromDataLoader<Long, SourceType>("SourceDataLoader", sourceId)
    }
}

class GlobalMetaType(
    override val key: String,
    override val value: String,
) : MetaType {
    constructor(row: ResultRow) : this(
        key = row[GlobalMetaTable.key],
        value = row[GlobalMetaTable.value],
    )
}

data class GlobalMetaNodeList(
    override val nodes: List<GlobalMetaType>,
    override val edges: List<MetaEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class MetaEdge(
        override val cursor: Cursor,
        override val node: GlobalMetaType,
    ) : Edge()

    companion object {
        fun List<GlobalMetaType>.toNodeList(): GlobalMetaNodeList {
            return GlobalMetaNodeList(
                nodes = this,
                edges = getEdges(),
                pageInfo =
                    PageInfo(
                        hasNextPage = false,
                        hasPreviousPage = false,
                        startCursor = Cursor(0.toString()),
                        endCursor = Cursor(lastIndex.toString()),
                    ),
                totalCount = size,
            )
        }

        private fun List<GlobalMetaType>.getEdges(): List<MetaEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                MetaEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                MetaEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}
