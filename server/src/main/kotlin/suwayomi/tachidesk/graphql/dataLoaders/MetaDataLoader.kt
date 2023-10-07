package suwayomi.tachidesk.graphql.dataLoaders

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.jetbrains.exposed.sql.Slf4jSqlDebugLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.global.model.table.GlobalMetaTable
import suwayomi.tachidesk.graphql.types.CategoryMetaType
import suwayomi.tachidesk.graphql.types.ChapterMetaType
import suwayomi.tachidesk.graphql.types.GlobalMetaType
import suwayomi.tachidesk.graphql.types.MangaMetaType
import suwayomi.tachidesk.manga.model.table.CategoryMetaTable
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.server.JavalinSetup.future

class GlobalMetaDataLoader : KotlinDataLoader<String, GlobalMetaType?> {
    override val dataLoaderName = "GlobalMetaDataLoader"

    override fun getDataLoader(): DataLoader<String, GlobalMetaType?> =
        DataLoaderFactory.newDataLoader<String, GlobalMetaType?> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val metasByRefId =
                        GlobalMetaTable.select { GlobalMetaTable.key inList ids }
                            .map { GlobalMetaType(it) }
                            .associateBy { it.key }
                    ids.map { metasByRefId[it] }
                }
            }
        }
}

class ChapterMetaDataLoader : KotlinDataLoader<Int, List<ChapterMetaType>> {
    override val dataLoaderName = "ChapterMetaDataLoader"

    override fun getDataLoader(): DataLoader<Int, List<ChapterMetaType>> =
        DataLoaderFactory.newDataLoader<Int, List<ChapterMetaType>> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val metasByRefId =
                        ChapterMetaTable.select { ChapterMetaTable.ref inList ids }
                            .map { ChapterMetaType(it) }
                            .groupBy { it.chapterId }
                    ids.map { metasByRefId[it].orEmpty() }
                }
            }
        }
}

class MangaMetaDataLoader : KotlinDataLoader<Int, List<MangaMetaType>> {
    override val dataLoaderName = "MangaMetaDataLoader"

    override fun getDataLoader(): DataLoader<Int, List<MangaMetaType>> =
        DataLoaderFactory.newDataLoader<Int, List<MangaMetaType>> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val metasByRefId =
                        MangaMetaTable.select { MangaMetaTable.ref inList ids }
                            .map { MangaMetaType(it) }
                            .groupBy { it.mangaId }
                    ids.map { metasByRefId[it].orEmpty() }
                }
            }
        }
}

class CategoryMetaDataLoader : KotlinDataLoader<Int, List<CategoryMetaType>> {
    override val dataLoaderName = "CategoryMetaDataLoader"

    override fun getDataLoader(): DataLoader<Int, List<CategoryMetaType>> =
        DataLoaderFactory.newDataLoader<Int, List<CategoryMetaType>> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val metasByRefId =
                        CategoryMetaTable.select { CategoryMetaTable.ref inList ids }
                            .map { CategoryMetaType(it) }
                            .groupBy { it.categoryId }
                    ids.map { metasByRefId[it].orEmpty() }
                }
            }
        }
}
