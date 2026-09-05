package suwayomi.tachidesk.test

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.expediagroup.graphql.server.types.GraphQLResponse
import graphql.ExceptionWhileDataFetching
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.global.impl.util.Bcrypt
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.server.GraphQLSchemaProvider
import suwayomi.tachidesk.graphql.server.TachideskDataLoaderRegistryFactory
import suwayomi.tachidesk.graphql.server.toGraphQLContext
import suwayomi.tachidesk.server.JavalinSetup
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.UserType
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Base class for executing GraphQL queries and mutations end-to-end against the real schema,
 * data loaders and resolvers, without spinning up the HTTP layer.
 *
 * The [UserType] placed in the GraphQL context is what the `@RequireAuth` directive reads to
 * inject the `userId` argument, so tests can run as a specific user.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class GraphQLTest : ApplicationTest() {
    companion object {
        private val logger = KotlinLogging.logger {}

        private val requestHandler: GraphQLRequestHandler by lazy {
            val exceptionHandler =
                DataFetcherExceptionHandler { handlerParameters ->
                    future {
                        val exception = handlerParameters.exception
                        val sourceLocation = handlerParameters.sourceLocation
                        val path = handlerParameters.path

                        logger.error(exception) { "GraphQL execution failed due to" }

                        val error =
                            ExceptionWhileDataFetching(
                                path,
                                Throwable(exception.message + "\r\n\r\n" + exception.stackTraceToString(), exception),
                                sourceLocation,
                            )

                        DataFetcherExceptionHandlerResult.newResult().error(error).build()
                    }
                }

            val graphQL =
                GraphQL
                    .newGraphQL(runBlocking { GraphQLSchemaProvider.getSchema() })
                    .queryExecutionStrategy(AsyncExecutionStrategy(exceptionHandler))
                    .mutationExecutionStrategy(AsyncExecutionStrategy(exceptionHandler))
                    .build()

            GraphQLRequestHandler(graphQL, TachideskDataLoaderRegistryFactory.create())
        }
    }

    /** The default admin user (id 1) used when a test does not specify one. */
    protected val admin: UserType = UserType.Admin(1)

    /**
     * Create a new user account and return its id.
     *
     * Useful for multi-user tests where a second (or third) user needs to exist so that rows can be
     * created for them without violating the `USER_ID` foreign key.
     */
    protected fun createTestUser(username: String): Int =
        transaction {
            UserAccountTable
                .insertAndGetId {
                    it[UserAccountTable.username] = username
                    it[UserAccountTable.password] = Bcrypt.encryptPassword("password")
                }.value
        }

    /**
     * Execute a single GraphQL query or mutation.
     *
     * @param query the GraphQL operation string
     * @param variables optional variables map
     * @param user the [UserType] to run the operation as (defaults to [admin])
     */
    protected fun graphql(
        query: String,
        variables: Map<String, Any?>? = null,
        user: UserType = admin,
    ): GraphQLResponse<*> =
        runBlocking {
            val context =
                mapOf(
                    JavalinSetup.Attribute.TachideskUser to user,
                ).toGraphQLContext()

            val request = GraphQLRequest(query = query, variables = variables)

            @Suppress("UNCHECKED_CAST")
            requestHandler.executeRequest(request, context) as GraphQLResponse<*>
        }

    /** Assert that the response contains no GraphQL errors. */
    protected fun GraphQLResponse<*>.assertNoErrors() {
        assertTrue(
            errors.isNullOrEmpty(),
            "Expected no GraphQL errors but got: $errors",
        )
    }

    /** Assert that the response contains at least one GraphQL error. */
    protected fun GraphQLResponse<*>.assertHasError() {
        assertFalse(
            errors.isNullOrEmpty(),
            "Expected GraphQL errors but none were returned",
        )
    }

    /**
     * Navigate a (nested) response data structure by a path of keys.
     *
     * Map keys are matched by name; list indices are matched by their integer string form.
     *
     * Example: `response.data.path("mangas", "nodes", "0", "title")`
     */
    protected fun Any?.path(vararg keys: String): Any? =
        keys.fold(this) { acc, key ->
            when (acc) {
                is Map<*, *> -> {
                    acc[key]
                }

                is List<*> -> {
                    val index = key.toIntOrNull()
                    if (index != null) acc.getOrNull(index) else null
                }

                else -> {
                    null
                }
            }
        }

    /** Navigate the response data map by a path of keys. */
    protected fun GraphQLResponse<*>.dataPath(vararg keys: String): Any? = data.path(*keys)
}
