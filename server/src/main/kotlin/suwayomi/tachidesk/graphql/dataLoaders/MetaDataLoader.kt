package suwayomi.tachidesk.graphql.dataLoaders

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import graphql.GraphQLContext
import org.dataloader.DataLoader
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import suwayomi.tachidesk.global.model.table.GlobalMetaTable
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.server.primitives.inIds
import suwayomi.tachidesk.graphql.types.CategoryMetaType
import suwayomi.tachidesk.graphql.types.ChapterMetaType
import suwayomi.tachidesk.graphql.types.GlobalMetaType
import suwayomi.tachidesk.graphql.types.MangaMetaType
import suwayomi.tachidesk.graphql.types.SourceMetaType
import suwayomi.tachidesk.manga.model.table.CategoryMetaTable
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.manga.model.table.SourceMetaTable
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.requireUser

class GlobalMetaDataLoader : KotlinDataLoader<String, GlobalMetaType> {
    override val dataLoaderName = "GlobalMetaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<String, GlobalMetaType> =
        newBatchedDataLoader<String, GlobalMetaType> { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                loggedTransaction {
                    val metasByRefId =
                        GlobalMetaTable
                            .selectAll()
                            .where { GlobalMetaTable.key inList ids and (GlobalMetaTable.user eq userId) }
                            .map { GlobalMetaType(it) }
                            .associateBy { it.key }
                    ids.map { metasByRefId[it] }.asDataLoaderResults()
                }
            }
        }
}

class ChapterMetaDataLoader : KotlinDataLoader<Int, List<ChapterMetaType>> {
    override val dataLoaderName = "ChapterMetaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, List<ChapterMetaType>> =
        newBatchedDataLoader<Int, List<ChapterMetaType>> { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                loggedTransaction {
                    val metasByRefId =
                        ChapterMetaTable
                            .selectAll()
                            .where { ChapterMetaTable.ref inIds ids and (ChapterMetaTable.user eq userId) }
                            .map { ChapterMetaType(it) }
                            .groupBy { it.chapterId }
                    ids.map { metasByRefId[it].orEmpty() }
                }
            }
        }
}

class MangaMetaDataLoader : KotlinDataLoader<Int, List<MangaMetaType>> {
    override val dataLoaderName = "MangaMetaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, List<MangaMetaType>> =
        newBatchedDataLoader<Int, List<MangaMetaType>> { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                loggedTransaction {
                    val metasByRefId =
                        MangaMetaTable
                            .selectAll()
                            .where { MangaMetaTable.ref inIds ids and (MangaMetaTable.user eq userId) }
                            .map { MangaMetaType(it) }
                            .groupBy { it.mangaId }
                    ids.map { metasByRefId[it].orEmpty() }
                }
            }
        }
}

class CategoryMetaDataLoader : KotlinDataLoader<Int, List<CategoryMetaType>> {
    override val dataLoaderName = "CategoryMetaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, List<CategoryMetaType>> =
        newBatchedDataLoader<Int, List<CategoryMetaType>> { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                loggedTransaction {
                    val metasByRefId =
                        CategoryMetaTable
                            .selectAll()
                            .where { CategoryMetaTable.ref inIds ids and (CategoryMetaTable.user eq userId) }
                            .map { CategoryMetaType(it) }
                            .groupBy { it.categoryId }
                    ids.map { metasByRefId[it].orEmpty() }
                }
            }
        }
}

class SourceMetaDataLoader : KotlinDataLoader<Long, List<SourceMetaType>> {
    override val dataLoaderName = "SourceMetaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Long, List<SourceMetaType>> =
        newBatchedDataLoader<Long, List<SourceMetaType>> { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                loggedTransaction {
                    val metasByRefId =
                        SourceMetaTable
                            .selectAll()
                            .where { SourceMetaTable.ref inIds ids and (SourceMetaTable.user eq userId) }
                            .map { SourceMetaType(it) }
                            .groupBy { it.sourceId }
                    ids.map { metasByRefId[it].orEmpty() }
                }
            }
        }
}
