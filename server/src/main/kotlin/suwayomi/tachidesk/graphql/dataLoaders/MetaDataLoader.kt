package suwayomi.tachidesk.graphql.dataLoaders

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.ChapterMetaItem
import suwayomi.tachidesk.graphql.types.MangaMetaItem
import suwayomi.tachidesk.graphql.types.MetaType
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import java.util.concurrent.CompletableFuture

class ChapterMetaDataLoader : KotlinDataLoader<Int, MetaType> {
    override val dataLoaderName = "ChapterMetaDataLoader"
    override fun getDataLoader(): DataLoader<Int, MetaType> = DataLoaderFactory.newDataLoader<Int, MetaType> { ids ->
        CompletableFuture.supplyAsync {
            transaction {
                addLogger(StdOutSqlLogger)
                val metasByChapterId = ChapterMetaTable.select { ChapterMetaTable.ref inList ids }
                    .map { ChapterMetaItem(it) }
                    .groupBy { it.ref }
                ids.map { metasByChapterId[it] ?: emptyList() }
            }
        }
    }
}

class MangaMetaDataLoader : KotlinDataLoader<Int, MetaType> {
    override val dataLoaderName = "MangaMetaDataLoader"
    override fun getDataLoader(): DataLoader<Int, MetaType> = DataLoaderFactory.newDataLoader<Int, MetaType> { ids ->
        CompletableFuture.supplyAsync {
            transaction {
                addLogger(StdOutSqlLogger)
                val metasByChapterId = MangaMetaTable.select { MangaMetaTable.ref inList ids }
                    .map { MangaMetaItem(it) }
                    .groupBy { it.ref }
                ids.map { metasByChapterId[it] ?: emptyList() }
            }
        }
    }
}
