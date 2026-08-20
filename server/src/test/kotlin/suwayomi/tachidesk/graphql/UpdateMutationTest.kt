package suwayomi.tachidesk.graphql

import org.junit.jupiter.api.Test
import suwayomi.tachidesk.test.GraphQLTest

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
        org.junit.jupiter.api.Assertions.assertEquals("stopId", response.dataPath("updateStop", "clientMutationId"))
    }
}
