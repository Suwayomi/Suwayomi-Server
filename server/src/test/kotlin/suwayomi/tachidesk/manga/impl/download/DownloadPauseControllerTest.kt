package suwayomi.tachidesk.manga.impl.download

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DownloadPauseControllerTest {
    @Test
    fun `restarts downloads after successful block when previously started`() =
        runTest {
            val calls = mutableListOf<String>()
            val controller =
                DownloadPauseController(
                    isStarted = { true },
                    stop = { calls += "stop" },
                    start = { calls += "start" },
                )

            val result =
                controller.run {
                    calls += "block"
                    "success"
                }

            assertEquals("success", result)
            assertEquals(listOf("stop", "block", "start"), calls)
        }

    @Test
    fun `does not restart downloads after failed block when previously stopped`() =
        runTest {
            val calls = mutableListOf<String>()
            val controller =
                DownloadPauseController(
                    isStarted = { false },
                    stop = { calls += "stop" },
                    start = { calls += "start" },
                )

            assertFailsWith<IllegalStateException> {
                controller.run {
                    calls += "block"
                    error("update failed")
                }
            }

            assertEquals(listOf("stop", "block"), calls)
        }
}
