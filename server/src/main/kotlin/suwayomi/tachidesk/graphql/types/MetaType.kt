package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.manga.model.table.CategoryMetaTable
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.MangaMetaTable

typealias MetaType = List<MetaItem>

open class MetaItem(
    val key: String,
    val value: String,
    @GraphQLIgnore
    val ref: Int
)

class ChapterMetaItem(
    private val row: ResultRow
) : MetaItem(row[ChapterMetaTable.key], row[ChapterMetaTable.value], row[ChapterMetaTable.ref].value)

class MangaMetaItem(
    private val row: ResultRow
) : MetaItem(row[MangaMetaTable.key], row[MangaMetaTable.value], row[MangaMetaTable.ref].value)

class CategoryMetaItem(
    private val row: ResultRow
) : MetaItem(row[CategoryMetaTable.key], row[CategoryMetaTable.value], row[CategoryMetaTable.ref].value)
