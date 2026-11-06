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
import kotlinx.serialization.json.Json
import org.cef.CefSettings.LogSeverity
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
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

suspend fun runComplete(block: suspend CompletableDeferred<Unit>.() -> Unit) {
    val testCompletion = CompletableDeferred<Unit>()
    val looper = Looper.myLooper()!!
    val originalObserver =
        looper.setThrowableObserver {
            testCompletion.completeExceptionally(it)
        }
    try {
        testCompletion.block()
        testCompletion.await()
    } finally {
        looper.setThrowableObserver(originalObserver)
    }
}

@OptIn(ExperimentalPathApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class CefProviderTest {
    private val logger = KotlinLogging.logger {}

    private class LooperThread : Thread() {
        lateinit var mHandler: Handler
        lateinit var mLooper: Looper
        private val completion = CompletableDeferred<Unit>()

        override fun run() {
            Looper.prepare()
            mLooper = Looper.myLooper()!!
            mHandler = Handler(mLooper)
            completion.complete(Unit)
            Looper.loop()
        }

        suspend fun await() = completion.await()

        companion object {
            private val logger = KotlinLogging.logger {}

            suspend fun test(
                timeout: Duration = 10.seconds,
                block: suspend (view: WebView, provider: KcefWebViewProvider) -> Unit,
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

                try {
                    withContext(thread.mHandler.asCoroutineDispatcher()) {
                        withTimeout(timeout) {
                            block(view, provider)
                        }
                    }
                } finally {
                    thread.mHandler.post {
                        view.destroy()
                        Looper.myLooper()!!.quit()
                    }
                    thread.join()
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
    fun cefNavigationWorks() =
        runTest {
            CefHelper.waitForInit().first()

            LooperThread.test { view, provider ->
                runComplete {
                    view.setWebViewClient(
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView,
                                url: String,
                            ) {
                                logger.info { "Page finished $url" }
                                provider.getSource {
                                    assertEquals("""<html><head></head><body><p>Example 1</p></body></html>""", it)
                                    complete(Unit)
                                }
                            }
                        },
                    )
                    provider.loadDataWithBaseURL(
                        "https://example.com",
                        """<p>Example 1</p>""",
                        "text/html",
                        null,
                        null,
                    )
                }

                runComplete {
                    view.setWebViewClient(
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView,
                                url: String,
                            ) {
                                logger.info { "Page finished $url" }
                                provider.getSource {
                                    assertEquals("""<html><head></head><body><p>Example 2</p></body></html>""", it)
                                    complete(Unit)
                                }
                            }
                        },
                    )
                    provider.loadDataWithBaseURL(
                        "https://example.com",
                        """<p>Example 2</p>""",
                        "text/html",
                        null,
                        null,
                    )
                }
            }
        }

    @Test
    fun cefCookiesArePassedOnlyWithSettingEnabled() =
        runTest {
            CefHelper.waitForInit().first()

            LooperThread.test { view, provider ->
                val settings = provider.settings as KcefWebSettings
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

                runComplete {
                    settings.setIncludeCookiesOnIntercept(false)
                    view.setWebViewClient(
                        object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                logger.info { "Received request for ${request.url}: ${request.requestHeaders}" }
                                assertFalse(request.requestHeaders.keys.any { it.equals("Cookie", true) })
                                complete(Unit)
                                return FailResponse()
                            }
                        },
                    )
                    provider.stopLoading()
                    provider.loadDataWithBaseURL(
                        "https://example.com",
                        """<img src="test.jpg?v=1"/><p>Example</p>""",
                        "text/html",
                        null,
                        null,
                    )
                }

                runComplete {
                    settings.setIncludeCookiesOnIntercept(true)
                    provider.setWebViewClient(
                        object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                logger.info { "Received request for ${request.url}: ${request.requestHeaders}" }
                                assertTrue(request.requestHeaders.keys.any { it.equals("Cookie", true) })
                                complete(Unit)
                                return FailResponse()
                            }
                        },
                    )
                    provider.stopLoading()
                    provider.loadDataWithBaseURL(
                        "https://example.com",
                        """<img src="test.jpg?v=2"/><p>Example</p>""",
                        "text/html",
                        null,
                        null,
                    )
                }
            }
        }

    @Test
    fun cefDocumentInjectionIsCalledInOrder() =
        runTest {
            CefHelper.waitForInit().first()

            LooperThread.test { view, provider ->
                provider.addJavaScriptOnEvent(
                    """
                    console.log('RUN START');
                    (window._suwa_trace = window._suwa_trace || []).push('DOCUMENT_START')
                    """,
                    KcefWebViewProvider.ScriptType.DOCUMENT_START,
                    arrayOf("*"),
                    "",
                )
                provider.addJavaScriptOnEvent(
                    """
                    console.log('RUN END');
                    (window._suwa_trace = window._suwa_trace || []).push('DOCUMENT_END')
                    """,
                    KcefWebViewProvider.ScriptType.DOCUMENT_END,
                    arrayOf("*"),
                    "",
                )

                runComplete {
                    view.setWebViewClient(
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView,
                                url: String,
                            ) {
                                logger.info { "Page finished $url" }
                                view.evaluateJavascript("JSON.stringify(window._suwa_trace)") {
                                    val v = Json.decodeFromString<String>(it)
                                    logger.info { "Trace: $v" }
                                    val list = Json.decodeFromString<List<String>>(v)
                                    // NOTE: We currently can not guarantee that DOCUMENT_START happens before SCRIPT :(
                                    assertEquals(setOf("DOCUMENT_START", "SCRIPT", "DOCUMENT_END"), Json.decodeFromString<Set<String>>(v))
                                    assertTrue(list.indexOf("DOCUMENT_END") > list.indexOf("SCRIPT"))
                                    complete(Unit)
                                }
                            }
                        },
                    )
                    provider.stopLoading()
                    view.loadDataWithBaseURL(
                        "https://example.com",
                        """
                        <head>
                        <script>
                            console.log('RUN SCRIPT');
                            (window._suwa_trace = window._suwa_trace || []).push('SCRIPT')
                        </script>
                        </head>
                        <p>Example</p>
                        """,
                        "text/html",
                        null,
                        null,
                    )
                }
            }
        }

    @Test
    fun cefDocumentInjectionIsCalledOnlyForMatchingOrigins() =
        runTest {
            CefHelper.waitForInit().first()

            LooperThread.test { view, provider ->
                val handle1 =
                    provider.addJavaScriptOnEvent(
                        """
                    console.log('RUN START example');
                    (window._suwa_trace = window._suwa_trace || []).push('DOCUMENT_START example')
                    """,
                        KcefWebViewProvider.ScriptType.DOCUMENT_START,
                        arrayOf("https://example.com"),
                        "",
                    )
                provider.addJavaScriptOnEvent(
                    """
                    console.log('RUN START example http');
                    (window._suwa_trace = window._suwa_trace || []).push('DOCUMENT_START example http')
                    """,
                    KcefWebViewProvider.ScriptType.DOCUMENT_START,
                    arrayOf("http://example.com"),
                    "",
                )
                provider.addJavaScriptOnEvent(
                    """
                    console.log('RUN START example host mismatch');
                    (window._suwa_trace = window._suwa_trace || []).push('DOCUMENT_START example host mismatch')
                    """,
                    KcefWebViewProvider.ScriptType.DOCUMENT_START,
                    arrayOf("https://www.example.com", "https://example1.com", "https://example.com:1234"),
                    "",
                )
                val handle4 =
                    provider.addJavaScriptOnEvent(
                        """
                    console.log('RUN START example:1234');
                    (window._suwa_trace = window._suwa_trace || []).push('DOCUMENT_START example:1234')
                    """,
                        KcefWebViewProvider.ScriptType.DOCUMENT_START,
                        arrayOf("https://example.com:1234"),
                        "",
                    )

                runComplete {
                    view.setWebViewClient(
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView,
                                url: String,
                            ) {
                                logger.info { "Page finished $url" }
                                view.evaluateJavascript("JSON.stringify(window._suwa_trace)") {
                                    assertEquals(
                                        setOf("DOCUMENT_START example"),
                                        Json.decodeFromString<Set<String>>(Json.decodeFromString<String>(it)),
                                    )
                                    complete(Unit)
                                }
                            }
                        },
                    )
                    provider.stopLoading()
                    view.loadDataWithBaseURL("https://example.com", "<p>Example</p>", "text/html", null, null)
                }

                runComplete {
                    view.setWebViewClient(
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView,
                                url: String,
                            ) {
                                logger.info { "Page finished $url" }
                                view.evaluateJavascript("JSON.stringify(window._suwa_trace)") {
                                    assertEquals(
                                        setOf("DOCUMENT_START example"),
                                        Json.decodeFromString<Set<String>>(Json.decodeFromString<String>(it)),
                                    )
                                    complete(Unit)
                                }
                            }
                        },
                    )
                    provider.stopLoading()
                    view.loadDataWithBaseURL("https://example.com:443", "<p>Example</p>", "text/html", null, null)
                }

                runComplete {
                    view.setWebViewClient(
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView,
                                url: String,
                            ) {
                                logger.info { "Page finished $url" }
                                view.evaluateJavascript("JSON.stringify(window._suwa_trace)") {
                                    assertEquals(
                                        setOf("DOCUMENT_START example host mismatch", "DOCUMENT_START example:1234"),
                                        Json.decodeFromString<Set<String>>(Json.decodeFromString<String>(it)),
                                    )
                                    complete(Unit)
                                }
                            }
                        },
                    )
                    provider.stopLoading()
                    view.loadDataWithBaseURL("https://example.com:1234", "<p>Example</p>", "text/html", null, null)
                }

                handle1.remove()
                handle4.remove()

                runComplete {
                    view.setWebViewClient(
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView,
                                url: String,
                            ) {
                                logger.info { "Page finished $url" }
                                view.evaluateJavascript("JSON.stringify(window._suwa_trace)") {
                                    assertEquals(
                                        setOf("DOCUMENT_START example host mismatch"),
                                        Json.decodeFromString<Set<String>>(Json.decodeFromString<String>(it)),
                                    )
                                    complete(Unit)
                                }
                            }
                        },
                    )
                    provider.stopLoading()
                    view.loadDataWithBaseURL("https://example.com:1234", "<p>Example</p>", "text/html", null, null)
                }
            }
        }

    @Test
    fun cefDocumentInjectionIsCalledOnlyForMatchingOriginsWithWildcards() =
        runTest {
            CefHelper.waitForInit().first()

            LooperThread.test { view, provider ->
                provider.addJavaScriptOnEvent(
                    """
                    console.log('RUN START https');
                    (window._suwa_trace = window._suwa_trace || []).push('DOCUMENT_START https')
                    """,
                    KcefWebViewProvider.ScriptType.DOCUMENT_START,
                    arrayOf("https://"),
                    "",
                )
                provider.addJavaScriptOnEvent(
                    """
                    console.log('RUN START example https.*');
                    (window._suwa_trace = window._suwa_trace || []).push('DOCUMENT_START example https.*')
                    """,
                    KcefWebViewProvider.ScriptType.DOCUMENT_START,
                    arrayOf("https://*.example.com"),
                    "",
                )
                provider.addJavaScriptOnEvent(
                    """
                    console.log('RUN START example https:443.*');
                    (window._suwa_trace = window._suwa_trace || []).push('DOCUMENT_START example https:443.*')
                    """,
                    KcefWebViewProvider.ScriptType.DOCUMENT_START,
                    arrayOf("https://*.example.com:443"),
                    "",
                )
                provider.addJavaScriptOnEvent(
                    """
                    console.log('RUN START example https:1234.*');
                    (window._suwa_trace = window._suwa_trace || []).push('DOCUMENT_START example https:1234.*')
                    """,
                    KcefWebViewProvider.ScriptType.DOCUMENT_START,
                    arrayOf("https://*.example.com:1234"),
                    "",
                )

                runComplete {
                    view.setWebViewClient(
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView,
                                url: String,
                            ) {
                                logger.info { "Page finished $url" }
                                view.evaluateJavascript("JSON.stringify(window._suwa_trace)") {
                                    assertEquals(
                                        setOf("DOCUMENT_START https"),
                                        Json.decodeFromString<Set<String>>(Json.decodeFromString<String>(it)),
                                    )
                                    complete(Unit)
                                }
                            }
                        },
                    )
                    provider.stopLoading()
                    view.loadDataWithBaseURL("https://example.com", "<p>Example</p>", "text/html", null, null)
                }

                runComplete {
                    view.setWebViewClient(
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView,
                                url: String,
                            ) {
                                logger.info { "Page finished $url" }
                                view.evaluateJavascript("JSON.stringify(window._suwa_trace)") {
                                    assertEquals(
                                        setOf(
                                            "DOCUMENT_START https",
                                            "DOCUMENT_START example https.*",
                                            "DOCUMENT_START example https:443.*",
                                        ),
                                        Json.decodeFromString<Set<String>>(Json.decodeFromString<String>(it)),
                                    )
                                    complete(Unit)
                                }
                            }
                        },
                    )
                    provider.stopLoading()
                    view.loadDataWithBaseURL("https://www.example.com", "<p>Example</p>", "text/html", null, null)
                }

                runComplete {
                    view.setWebViewClient(
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView,
                                url: String,
                            ) {
                                logger.info { "Page finished $url" }
                                view.evaluateJavascript("JSON.stringify(window._suwa_trace)") {
                                    assertEquals(
                                        setOf("DOCUMENT_START https", "DOCUMENT_START example https:1234.*"),
                                        Json.decodeFromString<Set<String>>(Json.decodeFromString<String>(it)),
                                    )
                                    complete(Unit)
                                }
                            }
                        },
                    )
                    provider.stopLoading()
                    view.loadDataWithBaseURL("https://www.example.com:1234", "<p>Example</p>", "text/html", null, null)
                }
            }
        }
}
