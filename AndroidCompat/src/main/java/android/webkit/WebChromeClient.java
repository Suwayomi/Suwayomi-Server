package android.webkit;

import android.annotation.Nullable;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Message;
import android.view.View;

public class WebChromeClient {
    public WebChromeClient() {
        throw new RuntimeException("Stub!");
    }

    public void onProgressChanged(WebView view, int newProgress) {
        throw new RuntimeException("Stub!");
    }

    public void onReceivedTitle(WebView view, String title) {
        throw new RuntimeException("Stub!");
    }

    public void onReceivedIcon(WebView view, Bitmap icon) {
        throw new RuntimeException("Stub!");
    }

    public void onReceivedTouchIconUrl(WebView view, String url, boolean precomposed) {
        throw new RuntimeException("Stub!");
    }

    public void onShowCustomView(View view, CustomViewCallback callback) {
        throw new RuntimeException("Stub!");
    }

    public void onHideCustomView() {
        throw new RuntimeException("Stub!");
    }

    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        throw new RuntimeException("Stub!");
    }

    public void onRequestFocus(WebView view) {
        throw new RuntimeException("Stub!");
    }

    public void onCloseWindow(WebView window) {
        throw new RuntimeException("Stub!");
    }

    public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
        throw new RuntimeException("Stub!");
    }

    public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
        throw new RuntimeException("Stub!");
    }

    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
        throw new RuntimeException("Stub!");
    }

    public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
        throw new RuntimeException("Stub!");
    }

    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
        throw new RuntimeException("Stub!");
    }

    public void onGeolocationPermissionsHidePrompt() {
        throw new RuntimeException("Stub!");
    }

    public void onPermissionRequest(PermissionRequest request) {
        throw new RuntimeException("Stub!");
    }

    public void onPermissionRequestCanceled(PermissionRequest request) {
        throw new RuntimeException("Stub!");
    }

    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public Bitmap getDefaultVideoPoster() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public View getVideoLoadingProgressView() {
        throw new RuntimeException("Stub!");
    }

    public void getVisitedHistory(ValueCallback<String[]> callback) {
        throw new RuntimeException("Stub!");
    }

    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        throw new RuntimeException("Stub!");
    }

    public abstract static class FileChooserParams {
        public static final int MODE_OPEN = 0;
        public static final int MODE_OPEN_MULTIPLE = 1;
        public static final int MODE_SAVE = 3;

        public FileChooserParams() {
            throw new RuntimeException("Stub!");
        }

        @Nullable
        public static Uri[] parseResult(int resultCode, Intent data) {
            throw new RuntimeException("Stub!");
        }

        public abstract int getMode();

        public abstract String[] getAcceptTypes();

        public abstract boolean isCaptureEnabled();

        @Nullable
        public abstract CharSequence getTitle();

        @Nullable
        public abstract String getFilenameHint();

        public abstract Intent createIntent();
    }

    public interface CustomViewCallback {
        void onCustomViewHidden();
    }
}
