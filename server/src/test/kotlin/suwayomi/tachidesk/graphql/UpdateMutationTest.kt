package suwayomi.tachidesk.graphql

import suwayomi.tachidesk.test.GraphQLTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateMutationTest : GraphQLTest() {
    @Test
    fun updateStop() {
        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateStopInput!) {
                    updateStop(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("clientMutationId" to "stopId")),
            )

        response.assertNoErrors()
        assertEquals("stopId", response.dataPath("updateStop", "clientMutationId"))
    }
}
