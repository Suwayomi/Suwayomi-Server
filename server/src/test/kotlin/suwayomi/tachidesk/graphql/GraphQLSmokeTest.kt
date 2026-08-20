package suwayomi.tachidesk.graphql

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.test.GraphQLTest

class GraphQLSmokeTest : GraphQLTest() {
    @Test
    fun aboutServer() {
        val response =
            graphql(
                """
                query {
                    aboutServer {
                        name
                        version
                        buildType
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertNotNull(response.dataPath("aboutServer", "name"), "aboutServer.name should be present")
    }

    @Test
    fun mangasEmpty() {
        val response =
            graphql(
                """
                query {
                    mangas {
                        totalCount
                        nodes {
                            id
                        }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertNotNull(response.dataPath("mangas", "totalCount"), "mangas.totalCount should be present")
    }
}
