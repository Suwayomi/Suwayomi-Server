package android.webkit;

import android.annotation.Nullable;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Message;
import android.view.KeyEvent;

public class WebViewClient {
    public static final int ERROR_AUTHENTICATION = -4;
    public static final int ERROR_BAD_URL = -12;
    public static final int ERROR_CONNECT = -6;
    public static final int ERROR_FAILED_SSL_HANDSHAKE = -11;
    public static final int ERROR_FILE = -13;
    public static final int ERROR_FILE_NOT_FOUND = -14;
    public static final int ERROR_HOST_LOOKUP = -2;
    public static final int ERROR_IO = -7;
    public static final int ERROR_PROXY_AUTHENTICATION = -5;
    public static final int ERROR_REDIRECT_LOOP = -9;
    public static final int ERROR_TIMEOUT = -8;
    public static final int ERROR_TOO_MANY_REQUESTS = -15;
    public static final int ERROR_UNKNOWN = -1;
    public static final int ERROR_UNSAFE_RESOURCE = -16;
    public static final int ERROR_UNSUPPORTED_AUTH_SCHEME = -3;
    public static final int ERROR_UNSUPPORTED_SCHEME = -10;
    public static final int SAFE_BROWSING_THREAT_BILLING = 4;
    public static final int SAFE_BROWSING_THREAT_MALWARE = 1;
    public static final int SAFE_BROWSING_THREAT_PHISHING = 2;
    public static final int SAFE_BROWSING_THREAT_UNKNOWN = 0;
    public static final int SAFE_BROWSING_THREAT_UNWANTED_SOFTWARE = 3;

    public WebViewClient() {
        throw new RuntimeException("Stub!");
    }

    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        throw new RuntimeException("Stub!");
    }

    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        throw new RuntimeException("Stub!");
    }

    public void onPageFinished(WebView view, String url) {
        throw new RuntimeException("Stub!");
    }

    public void onLoadResource(WebView view, String url) {
        // throw new RuntimeException("Stub!");
    }

    public void onPageCommitVisible(WebView view, String url) {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        throw new RuntimeException("Stub!");
    }

    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        throw new RuntimeException("Stub!");
    }

    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        throw new RuntimeException("Stub!");
    }

    public void onFormResubmission(WebView view, Message dontResend, Message resend) {
        throw new RuntimeException("Stub!");
    }

    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
        throw new RuntimeException("Stub!");
    }

    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        throw new RuntimeException("Stub!");
    }

    public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
        throw new RuntimeException("Stub!");
    }

    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
        throw new RuntimeException("Stub!");
    }

    public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
        throw new RuntimeException("Stub!");
    }

    public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
        throw new RuntimeException("Stub!");
    }

    public void onScaleChanged(WebView view, float oldScale, float newScale) {
        throw new RuntimeException("Stub!");
    }

    public void onReceivedLoginRequest(WebView view, String realm, @Nullable String account, String args) {
        throw new RuntimeException("Stub!");
    }

    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        throw new RuntimeException("Stub!");
    }

    public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threatType, SafeBrowsingResponse callback) {
        throw new RuntimeException("Stub!");
    }
}
