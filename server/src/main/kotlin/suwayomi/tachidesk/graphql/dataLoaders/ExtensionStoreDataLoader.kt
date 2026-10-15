package suwayomi.tachidesk.graphql.dataLoaders

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import graphql.GraphQLContext
import org.dataloader.DataLoader
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import suwayomi.tachidesk.graphql.types.ExtensionNodeList
import suwayomi.tachidesk.graphql.types.ExtensionNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.ExtensionStoreType
import suwayomi.tachidesk.graphql.types.ExtensionType
import suwayomi.tachidesk.manga.model.table.ExtensionStoreTable
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.server.JavalinSetup.future

class ExtensionStoreDataLoader : KotlinDataLoader<String, ExtensionStoreType> {
    override val dataLoaderName = "ExtensionStoreDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<String, ExtensionStoreType> =
        newBatchedDataLoader { ids ->
            future {
                loggedTransaction {
                    val extensionStoreByIndexUrl =
                        ExtensionStoreTable
                            .selectAll()
                            .where { ExtensionStoreTable.indexUrl inList ids }
                            .map { ExtensionStoreType(it) }
                            .associateBy { it.indexUrl }
                    ids.map { extensionStoreByIndexUrl[it] }.asDataLoaderResults()
                }
            }
        }
}

class ExtensionsForExtensionStore : KotlinDataLoader<String, ExtensionNodeList> {
    override val dataLoaderName = "ExtensionsForExtensionStore"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<String, ExtensionNodeList> =
        newBatchedDataLoader<String, ExtensionNodeList> { ids ->
            future {
                loggedTransaction {
                    val extensionByIndexUrl =
                        ExtensionTable
                            .selectAll()
                            .where { ExtensionTable.storeIndexUrl inList ids }
                            .map { ExtensionType(it) }
                            .groupBy { it.storeIndexUrl }
                    ids.map { (extensionByIndexUrl[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}

class ExtensionCountForExtensionStore : KotlinDataLoader<String, Int> {
    override val dataLoaderName = "ExtensionCountForExtensionStore"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<String, Int> =
        newBatchedDataLoader { ids ->
            future {
                loggedTransaction {
                    val count = ExtensionTable.id.count()
                    val countByIndexUrl =
                        ExtensionTable
                            .select(ExtensionTable.storeIndexUrl, count)
                            .where { ExtensionTable.storeIndexUrl inList ids }
                            .groupBy(ExtensionTable.storeIndexUrl)
                            .associate { it[ExtensionTable.storeIndexUrl] to it[count].toInt() }
                    ids.map { countByIndexUrl[it] ?: 0 }
                }
            }
        }
}
