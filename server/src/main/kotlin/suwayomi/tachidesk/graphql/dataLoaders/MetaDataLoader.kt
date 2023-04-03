package suwayomi.tachidesk.graphql.dataLoaders

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.jetbrains.exposed.sql.Slf4jSqlDebugLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.global.model.table.GlobalMetaTable
import suwayomi.tachidesk.graphql.types.CategoryMetaItem
import suwayomi.tachidesk.graphql.types.ChapterMetaItem
import suwayomi.tachidesk.graphql.types.GlobalMetaItem
import suwayomi.tachidesk.graphql.types.MangaMetaItem
import suwayomi.tachidesk.graphql.types.MetaItem
import suwayomi.tachidesk.graphql.types.MetaNodeList
import suwayomi.tachidesk.graphql.types.MetaNodeList.Companion.toNodeList
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.server.JavalinSetup.future

class GlobalMetaDataLoader : KotlinDataLoader<String, MetaItem?> {
    override val dataLoaderName = "GlobalMetaDataLoader"
    override fun getDataLoader(): DataLoader<String, MetaItem?> = DataLoaderFactory.newDataLoader<String, MetaItem?> { ids ->
        future {
            transaction {
                addLogger(Slf4jSqlDebugLogger)
                val metasByRefId = GlobalMetaTable.select { GlobalMetaTable.key inList ids }
                    .map { GlobalMetaItem(it) }
                    .associateBy { it.key }
                ids.map { metasByRefId[it] }
            }
        }
    }
}

class ChapterMetaDataLoader : KotlinDataLoader<Int, MetaNodeList> {
    override val dataLoaderName = "ChapterMetaDataLoader"
    override fun getDataLoader(): DataLoader<Int, MetaNodeList> = DataLoaderFactory.newDataLoader<Int, MetaNodeList> { ids ->
        future {
            transaction {
                addLogger(Slf4jSqlDebugLogger)
                val metasByRefId = ChapterMetaTable.select { ChapterMetaTable.ref inList ids }
                    .map { ChapterMetaItem(it) }
                    .groupBy { it.ref }
                ids.map { (metasByRefId[it] ?: emptyList()).toNodeList() }
            }
        }
    }
}

class MangaMetaDataLoader : KotlinDataLoader<Int, MetaNodeList> {
    override val dataLoaderName = "MangaMetaDataLoader"
    override fun getDataLoader(): DataLoader<Int, MetaNodeList> = DataLoaderFactory.newDataLoader<Int, MetaNodeList> { ids ->
        future {
            transaction {
                addLogger(Slf4jSqlDebugLogger)
                val metasByRefId = MangaMetaTable.select { MangaMetaTable.ref inList ids }
                    .map { MangaMetaItem(it) }
                    .groupBy { it.ref }
                ids.map { (metasByRefId[it] ?: emptyList()).toNodeList() }
            }
        }
    }
}

class CategoryMetaDataLoader : KotlinDataLoader<Int, MetaNodeList> {
    override val dataLoaderName = "CategoryMetaDataLoader"
    override fun getDataLoader(): DataLoader<Int, MetaNodeList> = DataLoaderFactory.newDataLoader<Int, MetaNodeList> { ids ->
        future {
            transaction {
                addLogger(Slf4jSqlDebugLogger)
                val metasByRefId = MangaMetaTable.select { MangaMetaTable.ref inList ids }
                    .map { CategoryMetaItem(it) }
                    .groupBy { it.ref }
                ids.map { (metasByRefId[it] ?: emptyList()).toNodeList() }
            }
        }
    }
}
