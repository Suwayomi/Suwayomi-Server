package suwayomi.tachidesk

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LooperThread : Thread() {
    var mHandler: Handler? = null
    val latch = CountDownLatch(1)

    override fun run() {
        Looper.prepare()
        mHandler = Handler(Looper.myLooper()!!)
        latch.countDown()
        Looper.loop()
    }
}

class LooperTest {
    @Test
    fun multiplePostWork() {
        val thread = LooperThread()
        thread.start()
        val sb = StringBuilder()
        val latch = CountDownLatch(1)
        assertTrue(thread.latch.await(5, TimeUnit.SECONDS))

        thread.mHandler!!.post {
            Thread.sleep(100)
            sb.append("a_b_c")
        }
        thread.mHandler!!.post {
            Thread.sleep(100)
            sb.append("_d_e_f")
        }
        thread.mHandler!!.post {
            Thread.sleep(100)
            sb.append("_g_h_i")
            latch.countDown()
        }

        assertNotEquals("a_b_c_d_e_f_g_h_i", sb.toString())
        assertTrue(latch.await(5, TimeUnit.SECONDS))

        assertEquals("a_b_c_d_e_f_g_h_i", sb.toString())
        thread.mHandler!!.looper.quit()
        // thread.join()
    }

    @Test
    fun loopTest() {
        val thread = LooperThread()
        thread.start()
        val sb = StringBuilder()
        val expected = StringBuilder()
        val latch = CountDownLatch(1)
        assertTrue(thread.latch.await(5, TimeUnit.SECONDS))
        val n = 100

        for (i in 0 until n) {
            thread.mHandler!!.post {
                Thread.sleep(10)
                sb.append("$i")
            }
            expected.append("$i")
        }

        thread.mHandler!!.post {
            latch.countDown()
        }

        assertNotEquals(expected.toString(), sb.toString())
        assertTrue(latch.await(5, TimeUnit.SECONDS), "only got to $sb")

        assertEquals(expected.toString(), sb.toString())
        thread.mHandler!!.looper.quit()
        // thread.join()
    }
}
