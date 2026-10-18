package suwayomi.tachidesk.graphql

import kotlinx.coroutines.runBlocking
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.test.GraphQLTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadReorderMutationTest : GraphQLTest() {
    @Test
    fun emptyBatchReorderReturnsCurrentQueue() {
        runBlocking { DownloadManager.clear() }

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
        assertEquals(emptyList<Any>(), response.dataPath("reorderChapterDownloads", "downloadStatus", "queue"))
    }
}
