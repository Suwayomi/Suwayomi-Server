package android.webkit

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import ca.gosyer.appdirs.AppDirs
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.cef.CefSettings.LogSeverity
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import xyz.nulldev.androidcompat.webkit.CefHelper
import xyz.nulldev.androidcompat.webkit.KcefWebSettings
import xyz.nulldev.androidcompat.webkit.KcefWebViewProvider
import java.nio.file.Path
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalPathApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CefProviderTest {
    private val logger = KotlinLogging.logger {}

    private class LooperThread : Thread() {
        lateinit var mHandler: Handler
        val latch = CountDownLatch(1)

        override fun run() {
            Looper.prepare()
            mHandler = Handler(Looper.myLooper()!!)
            latch.countDown()
            Looper.loop()
        }

        companion object {
            private val logger = KotlinLogging.logger {}

            suspend fun test(
                timeout: Duration = 10.seconds,
                block: () -> Unit,
            ) {
                val latch = CountDownLatch(1)
                val thread = LooperThread()
                thread.start()
                assertTrue(thread.latch.await(1, TimeUnit.SECONDS))

                var exception: Throwable? = null
                thread.mHandler.post {
                    try {
                        block()
                    } catch (e: Throwable) {
                        logger.error(e) { "Block failed" }
                        exception = e
                    } finally {
                        latch.countDown()
                        Looper.myLooper()!!.quit()
                    }
                }

                assertTrue(latch.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS))
                exception?.let { throw it }
            }
        }
    }

    private class FailResponse(
        mimeType: String = "text/html",
    ) : WebResourceResponse(mimeType, "utf8", 400, "Not Found", null, null)

    @BeforeAll
    fun init() {
        System.loadLibrary("jawt")

        val root = AppDirs { appName = "Tachidesk" }.getUserDataDir()
        val cefDir = Path(root) / "bin/kcef"
        CefHelper.doCreateApp(
            cefDir,
            null,
            LogSeverity.LOGSEVERITY_VERBOSE,
        ) { config ->
            config.getLoader().loadLibrary("cef")
            config.getLoader().loadLibrary("jcef")
        }
    }

    @Test
    fun cefCookiesArePassedOnlyWithSettingEnabled() =
        runTest {
            CefHelper.waitForInit().first()

            LooperThread.test {
                val ctx = mockk<Context>(relaxed = true)
                lateinit var provider: KcefWebViewProvider
                val view = WebView(ctx, null, 0, 0, null, false) { KcefWebViewProvider(it).also { provider = it } }
                val settings = provider.settings as KcefWebSettings
                settings.setUserAgentString("hello")
                CefCookieManager.getGlobalManager().apply {
                    setCookie(
                        "https://example.com",
                        CefCookie(
                            "test-cookie",
                            "test-value",
                            ".example.com",
                            "/",
                            false,
                            true,
                            Date(),
                            null,
                            false,
                            Date(),
                        ),
                    )
                }

                val latch1 = CountDownLatch(1)
                settings.setIncludeCookiesOnIntercept(false)
                provider.setWebViewClient(
                    object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            logger.info { "Received request for ${request.url}: ${request.requestHeaders}" }
                            assertFalse(request.requestHeaders.keys.any { it.equals("Cookie", true) })
                            latch1.countDown()
                            return FailResponse()
                        }
                    },
                )
                provider.loadDataWithBaseURL("https://example.com", "<p>Example</p>", "text/html", null, null)
                assertTrue(latch1.await(10, TimeUnit.SECONDS))

                val latch2 = CountDownLatch(1)
                settings.setIncludeCookiesOnIntercept(true)
                provider.setWebViewClient(
                    object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            logger.info { "Received request for ${request.url}: ${request.requestHeaders}" }
                            assertTrue(request.requestHeaders.keys.any { it.equals("Cookie", true) })
                            latch2.countDown()
                            return FailResponse()
                        }
                    },
                )
                provider.loadDataWithBaseURL("https://example.com", "<p>Example</p>", "text/html", null, null)
                assertTrue(latch2.await(10, TimeUnit.SECONDS))

                view.destroy()
            }
        }
}
