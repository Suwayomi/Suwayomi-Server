package suwayomi.tachidesk.graphql

import kotlinx.coroutines.runBlocking
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.test.GraphQLTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadMutationTest : GraphQLTest() {
    @Test
    fun startDownloaderReturnsStoppedForAnEmptyQueue() {
        runBlocking { DownloadManager.clear() }

        val response =
            graphql(
                """
                mutation {
                    startDownloader(input: {clientMutationId: "empty"}) {
                        clientMutationId
                        downloadStatus {
                            state
                            queue {
                                state
                            }
                        }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals("empty", response.dataPath("startDownloader", "clientMutationId"))
        assertEquals("STOPPED", response.dataPath("startDownloader", "downloadStatus", "state"))
        assertEquals(emptyList<Any>(), response.dataPath("startDownloader", "downloadStatus", "queue"))
    }
}
