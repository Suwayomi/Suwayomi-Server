package suwayomi.tachidesk.graphql.server.primitives

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import java.util.concurrent.CompletableFuture

interface Node

abstract class NodeList {
    @GraphQLDescription("A list of [T] objects.")
    abstract val nodes: List<Node>

    @GraphQLDescription("A list of edges which contains the [T] and cursor to aid in pagination.")
    abstract val edges: List<Edge>

    @GraphQLDescription("Information to aid in pagination.")
    abstract val pageInfo: PageInfo

    @GraphQLDescription("The count of all nodes you could get from the connection.")
    abstract val totalCount: Int
}

data class PageInfo(
    @GraphQLDescription("When paginating forwards, are there more items?")
    val hasNextPage: Boolean,
    @GraphQLDescription("When paginating backwards, are there more items?")
    val hasPreviousPage: Boolean,
    @GraphQLDescription("When paginating backwards, the cursor to continue.")
    val startCursor: Cursor?,
    @GraphQLDescription("When paginating forwards, the cursor to continue.")
    val endCursor: Cursor?,
)

abstract class Edge {
    @GraphQLDescription("A cursor for use in pagination.")
    abstract val cursor: Cursor

    @GraphQLDescription("The [T] at the end of the edge.")
    abstract val node: Node
}

/**
 * Resolves a list field from [nodesLoader], which loads every node of the list, unless the caller
 * selected nothing but its `totalCount`, as a list showing counts does: then [totalLoader] counts
 * the nodes in SQL instead, and [withTotal] builds the list around that total.
 */
fun <K : Any, N : NodeList> DataFetchingEnvironment.getNodeListFromDataLoaders(
    nodesLoader: String,
    totalLoader: String,
    key: K,
    withTotal: (totalCount: Int) -> N,
): CompletableFuture<N> {
    val onlyTotal = selectionSet.immediateFields.all { it.name == "totalCount" || it.name == "__typename" }
    if (!onlyTotal) return getValueFromDataLoader<K, N>(nodesLoader, key)
    return getValueFromDataLoader<K, Int>(totalLoader, key).thenApply(withTotal)
}
