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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test
import kotlin.test.assertEquals
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
        private val completion = CompletableDeferred<Unit>()

        override fun run() {
            Looper.prepare()
            mHandler = Handler(Looper.myLooper()!!)
            completion.complete(Unit)
            Looper.loop()
        }

        suspend fun await() = completion.await()

        companion object {
            private val logger = KotlinLogging.logger {}

            suspend fun test(
                timeout: Duration = 10.seconds,
                block: suspend CompletableDeferred<Unit>.(view: WebView, provider: KcefWebViewProvider) -> Unit,
            ) {
                val thread = LooperThread()
                thread.start()
                thread.await()

                val viewCompletion = CompletableDeferred<WebView>()
                thread.mHandler.post {
                    try {
                        val ctx = mockk<Context>(relaxed = true)
                        val view = WebView(ctx, null, 0, 0, null, false) { KcefWebViewProvider(it) }
                        viewCompletion.complete(view)
                    } catch (e: Throwable) {
                        logger.error(e) { "WebView setup failed" }
                        viewCompletion.completeExceptionally(e)
                    }
                }

                val view = viewCompletion.await()
                val provider = view.webViewProvider as KcefWebViewProvider
                val testCompletion = CompletableDeferred<Unit>()

                try {
                    withContext(thread.mHandler.asCoroutineDispatcher()) {
                        testCompletion.block(view, provider)
                        withTimeout(timeout) {
                            testCompletion.await()
                        }
                    }
                } finally {
                    thread.mHandler.post {
                        view.destroy()
                        Looper.myLooper()!!.quit()
                    }
                }
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

            LooperThread.test { view, provider ->
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

                val deferred1 = CompletableDeferred<Unit>()
                settings.setIncludeCookiesOnIntercept(false)
                view.setWebViewClient(
                    object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            logger.info { "Received request for ${request.url}: ${request.requestHeaders}" }
                            assertFalse(request.requestHeaders.keys.any { it.equals("Cookie", true) })
                            deferred1.complete(Unit)
                            return FailResponse()
                        }
                    },
                )
                provider.loadDataWithBaseURL("https://example.com", "<p>Example</p>", "text/html", null, null)
                deferred1.await()

                val deferred2 = CompletableDeferred<Unit>()
                settings.setIncludeCookiesOnIntercept(true)
                provider.setWebViewClient(
                    object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            logger.info { "Received request for ${request.url}: ${request.requestHeaders}" }
                            assertTrue(request.requestHeaders.keys.any { it.equals("Cookie", true) })
                            deferred2.complete(Unit)
                            return FailResponse()
                        }
                    },
                )
                provider.loadDataWithBaseURL("https://example.com", "<p>Example</p>", "text/html", null, null)
                deferred2.await()

                complete(Unit)
            }
        }
}
