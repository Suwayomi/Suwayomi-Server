package suwayomi.tachidesk

import graphql.Assert.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import suwayomi.tachidesk.server.subscribeTo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class FlowTest {
    @Test
    fun subscribe() =
        runTest {
            (1..1000).forEach { _ ->
                val testFlow = MutableStateFlow(value = 3)
                testFlow.first()
                val latch = CountDownLatch(1)
                subscribeTo(testFlow, ignoreInitialValue = false) { _ ->
                    latch.countDown()
                }
                assertTrue(latch.await(5, TimeUnit.SECONDS))
            }
        }
}
