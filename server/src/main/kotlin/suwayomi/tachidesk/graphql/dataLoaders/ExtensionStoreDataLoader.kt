package suwayomi.tachidesk.graphql.dataLoaders

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import graphql.GraphQLContext
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.jetbrains.exposed.v1.core.Slf4jSqlDebugLogger
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val extensionStoreByIndexUrl =
                        ExtensionStoreTable
                            .selectAll()
                            .where { ExtensionStoreTable.indexUrl inList ids }
                            .map { ExtensionStoreType(it) }
                            .associateBy { it.indexUrl }
                    ids.map { extensionStoreByIndexUrl[it] }
                }
            }
        }
}

class ExtensionsForExtensionStore : KotlinDataLoader<String, ExtensionNodeList> {
    override val dataLoaderName = "ExtensionsForExtensionStore"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<String, ExtensionNodeList> =
        DataLoaderFactory.newDataLoader<String, ExtensionNodeList> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
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
