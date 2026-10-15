package suwayomi.tachidesk.global.impl

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebViewUrlPolicyTest {
    @Test
    fun httpAndHttpsUrlsAreAllowed() {
        listOf(
            "http://example.com",
            "https://example.com/path?q=1#frag",
            "HTTPS://EXAMPLE.COM/",
            "HtTp://example.com",
            "http://user:password@example.com:8080/path",
            "https://127.0.0.1:4567",
        ).forEach { assertTrue(WebViewUrlPolicy.isAllowed(it), "expected $it to be allowed") }
    }

    @Test
    fun nonHttpSchemesAreRejected() {
        listOf(
            "file:///etc/passwd",
            "file:///C:/Windows/win.ini",
            "data:text/html;base64,PGI+aGk8L2I+",
            "chrome://settings",
            "javascript:alert(1)",
            "view-source:https://example.com",
            "ftp://example.com/file",
            "about:blank",
            "chrome-extension://abcdef/page.html",
            "intent://example.com/#Intent;scheme=https;end",
        ).forEach { assertFalse(WebViewUrlPolicy.isAllowed(it), "expected $it to be rejected") }
    }

    @Test
    fun blankMissingOrMalformedUrlsAreRejected() {
        listOf(
            "",
            "   ",
            "example.com", // missing scheme
            "//example.com/path", // scheme-relative
            "http://", // missing host
            "ht tp://example.com",
            ":\\\\",
        ).forEach { assertFalse(WebViewUrlPolicy.isAllowed(it), "expected $it to be rejected") }
        assertFalse(WebViewUrlPolicy.isAllowed(null))
    }
}
