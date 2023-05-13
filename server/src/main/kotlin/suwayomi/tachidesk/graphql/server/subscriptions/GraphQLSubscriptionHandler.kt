/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server.subscriptions

import com.expediagroup.graphql.dataloader.KotlinDataLoaderRegistryFactory
import com.expediagroup.graphql.server.extensions.toExecutionInput
import com.expediagroup.graphql.server.extensions.toGraphQLError
import com.expediagroup.graphql.server.extensions.toGraphQLKotlinType
import com.expediagroup.graphql.server.extensions.toGraphQLResponse
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.expediagroup.graphql.server.types.GraphQLResponse
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

open class GraphQLSubscriptionHandler(
    private val graphQL: GraphQL,
    private val dataLoaderRegistryFactory: KotlinDataLoaderRegistryFactory? = null
) {
    open fun executeSubscription(
        graphQLRequest: GraphQLRequest,
        graphQLContext: GraphQLContext = GraphQLContext.of(emptyMap<Any, Any>())
    ): Flow<GraphQLResponse<*>> {
        val dataLoaderRegistry = dataLoaderRegistryFactory?.generate()
        val input = graphQLRequest.toExecutionInput(dataLoaderRegistry, graphQLContext)

        val res = graphQL.execute(input)
        val data = res.getData<Flow<ExecutionResult>>()
        val mapped = data.map { result -> result.toGraphQLResponse() }
        return mapped.catch { throwable ->
            val error = throwable.toGraphQLError()
            emit(GraphQLResponse<Any?>(errors = listOf(error.toGraphQLKotlinType())))
        }
    }
}
