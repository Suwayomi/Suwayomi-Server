package suwayomi.tachidesk.graphql

import suwayomi.tachidesk.test.GraphQLTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadReorderMutationTest : GraphQLTest() {
    @Test
    fun emptyBatchReorderReturnsCurrentQueue() {
        val response =
            graphql(
                """
                mutation {
                    reorderChapterDownloads(input: {clientMutationId: "empty", reorders: []}) {
                        clientMutationId
                        downloadStatus {
                            queue {
                                state
                            }
                        }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals("empty", response.dataPath("reorderChapterDownloads", "clientMutationId"))
    }
}
