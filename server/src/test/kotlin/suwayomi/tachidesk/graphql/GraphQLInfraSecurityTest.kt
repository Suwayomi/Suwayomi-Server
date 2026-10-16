package suwayomi.tachidesk.graphql

import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.expediagroup.graphql.server.types.GraphQLResponse
import kotlinx.coroutines.runBlocking
import suwayomi.tachidesk.graphql.server.TachideskDataLoaderRegistryFactory
import suwayomi.tachidesk.graphql.server.TachideskGraphQLServer
import suwayomi.tachidesk.graphql.server.toGraphQLContext
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.server.JavalinSetup
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import kotlin.test.Test

/**
 * Runs queries through the production [TachideskGraphQLServer] engine (with the
 * depth/complexity/introspection instrumentation attached), unlike the shared base class
 * which builds a bare engine.
 */
class GraphQLInfraSecurityTest : GraphQLTest() {
    private val securedRequestHandler by lazy {
        GraphQLRequestHandler(
            TachideskGraphQLServer.getGraphQLObject(),
            TachideskDataLoaderRegistryFactory.create(),
        )
    }

    private fun graphqlSecured(
        query: String,
        user: UserType = admin,
    ): GraphQLResponse<*> =
        runBlocking {
            val context =
                mapOf(
                    JavalinSetup.Attribute.TachideskUser to user,
                ).toGraphQLContext()

            @Suppress("UNCHECKED_CAST")
            securedRequestHandler.executeRequest(GraphQLRequest(query = query), context) as GraphQLResponse<*>
        }

    private fun nestedMangaQuery(cycles: Int): String =
        buildString {
            append("query { manga(id: 1) { ")
            repeat(cycles) { append("chapters { nodes { manga { ") }
            append("id")
            repeat(cycles) { append(" } } }") }
            append(" } }")
        }

    private fun <T> withAuthMode(
        authMode: AuthMode,
        block: () -> T,
    ): T {
        val previous = serverConfig.authMode.value
        serverConfig.authMode.value = authMode
        return try {
            block()
        } finally {
            serverConfig.authMode.value = previous
        }
    }

    @Test
    fun regularQueryPassesInstrumentation() {
        val response =
            graphqlSecured(
                """
                query { aboutServer { name version } }
                """.trimIndent(),
            )
        response.assertNoErrors()
    }

    private fun assertLimiterError(
        response: GraphQLResponse<*>,
        keyword: String,
    ) {
        response.assertHasError()
        kotlin.test.assertTrue(
            response.errors.orEmpty().any { it.message.contains(keyword, ignoreCase = true) },
            "expected a limiter error mentioning '$keyword', got: ${response.errors}",
        )
    }

    @Test
    fun tooDeepQueryIsRejected() {
        // depth = 1 + 5 * 3 + 1 = 17 > MAX_QUERY_DEPTH (15)
        assertLimiterError(graphqlSecured(nestedMangaQuery(5)), "depth")
    }

    @Test
    fun moderatelyDeepQueryPasses() {
        // depth = 1 + 2 * 3 + 1 = 8 <= MAX_QUERY_DEPTH; the (empty-db) data error is fine,
        // only an instrumentation abort would prove the limiter misbehaves
        val response = graphqlSecured(nestedMangaQuery(2))
        val limiterErrors =
            response.errors.orEmpty().filter {
                it.message.contains("depth", ignoreCase = true) ||
                    it.message.contains("complexity", ignoreCase = true)
            }
        kotlin.test.assertTrue(limiterErrors.isEmpty(), "unexpected limiter errors: $limiterErrors")
    }

    @Test
    fun overlyComplexQueryIsRejected() {
        // 600 aliased aboutServer selections ~ 1200 complexity > MAX_QUERY_COMPLEXITY (500)
        val fatQuery = "query { " + (1..600).joinToString(" ") { "q$it: aboutServer { name }" } + " }"
        assertLimiterError(graphqlSecured(fatQuery), "complexity")
    }

    @Test
    fun introspectionIsRejectedForVisitorInUiLogin() =
        withAuthMode(AuthMode.UI_LOGIN) {
            val response = graphqlSecured("{ __schema { types { name } } }", UserType.Visitor)
            response.assertHasError()
        }

    @Test
    fun introspectionViaFragmentIsRejectedForVisitorInUiLogin() =
        withAuthMode(AuthMode.UI_LOGIN) {
            val response =
                graphqlSecured(
                    "{ ...I } fragment I on Query { __type(name: \"Query\") { name } }",
                    UserType.Visitor,
                )
            response.assertHasError()
        }

    @Test
    fun introspectionIsAllowedForAdminInUiLogin() =
        withAuthMode(AuthMode.UI_LOGIN) {
            val response = graphqlSecured("{ __schema { queryType { name } } }", admin)
            response.assertNoErrors()
        }

    @Test
    fun introspectionStaysEnabledInModesWithoutVisitors() =
        withAuthMode(AuthMode.NONE) {
            val response = graphqlSecured("{ __schema { queryType { name } } }", UserType.Visitor)
            response.assertNoErrors()
        }

    @Test
    fun typenameIsNotIntrospection() =
        withAuthMode(AuthMode.UI_LOGIN) {
            val response = graphqlSecured("{ __typename }", UserType.Visitor)
            response.assertNoErrors()
        }

    /**
     * Regression guard for the manually enumerated top-level objects in
     * [suwayomi.tachidesk.graphql.server.TachideskGraphQLSchema]: if one is dropped from
     * the schema config the corresponding root field silently disappears.
     */
    @Suppress("UNCHECKED_CAST")
    private fun rootFieldNames(root: String): Set<String> {
        val response = graphqlSecured("{ __schema { $root { fields { name } } } }")
        response.assertNoErrors()
        return (
            ((response.data as? Map<*, *>)?.get("__schema") as? Map<*, *>)
                ?.get(root) as? Map<*, *>
        )?.get("fields")
            .let { it as? List<*> }
            .orEmpty()
            .mapNotNull { (it as? Map<*, *>)?.get("name") as? String }
            .toSet()
    }

    /**
     * Regression guard for the manually enumerated top-level objects in
     * [suwayomi.tachidesk.graphql.server.TachideskGraphQLSchema]: if one is dropped from
     * the schema config the corresponding root field silently disappears.
     */
    @Test
    fun schemaExposesExpectedRootFields() {
        // graphql-java's good-faith introspection check rejects asking for `fields` too
        // often in one request, so each root type is probed separately
        val queryFields = rootFieldNames("queryType")
        kotlin.test.assertTrue("manga" in queryFields && "mangas" in queryFields && "aboutServer" in queryFields)
        val mutationFields = rootFieldNames("mutationType")
        kotlin.test.assertTrue("createBackup" in mutationFields && "restoreBackup" in mutationFields)
        kotlin.test.assertTrue(rootFieldNames("subscriptionType").isNotEmpty())
    }
}
