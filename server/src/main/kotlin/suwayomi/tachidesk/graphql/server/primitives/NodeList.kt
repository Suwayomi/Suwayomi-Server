package suwayomi.tachidesk.graphql.server.primitives

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

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
