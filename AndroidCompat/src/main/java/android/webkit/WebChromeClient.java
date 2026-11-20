/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.webkit;

import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Message;
import android.view.View;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class WebChromeClient {

    public void onProgressChanged(WebView view, int newProgress) {}

    public void onReceivedTitle(WebView view, String title) {}

    public void onReceivedIcon(WebView view, Bitmap icon) {}

    public void onReceivedTouchIconUrl(WebView view, String url,
            boolean precomposed) {}

    public interface CustomViewCallback {
        public void onCustomViewHidden();
    }

    public void onShowCustomView(View view, CustomViewCallback callback) {};

    @Deprecated
    public void onShowCustomView(View view, int requestedOrientation,
            CustomViewCallback callback) {};

    public void onHideCustomView() {}

    public boolean onCreateWindow(WebView view, boolean isDialog,
            boolean isUserGesture, Message resultMsg) {
        return false;
    }

    public void onRequestFocus(WebView view) {}

    public void onCloseWindow(WebView window) {}

    public boolean onJsAlert(WebView view, String url, String message,
            JsResult result) {
        return false;
    }

    public boolean onJsConfirm(WebView view, String url, String message,
            JsResult result) {
        return false;
    }

    public boolean onJsPrompt(WebView view, String url, String message,
            String defaultValue, JsPromptResult result) {
        return false;
    }

    public boolean onJsBeforeUnload(WebView view, String url, String message,
            JsResult result) {
        return false;
    }

    @Deprecated
    public void onExceededDatabaseQuota(String url, String databaseIdentifier,
            long quota, long estimatedDatabaseSize, long totalQuota,
            WebStorage.QuotaUpdater quotaUpdater) {
        // This default implementation passes the current quota back to WebCore.
        // WebCore will interpret this that new quota was declined.
        quotaUpdater.updateQuota(quota);
    }

    @Deprecated
    public void onReachedMaxAppCacheSize(long requiredStorage, long quota,
            WebStorage.QuotaUpdater quotaUpdater) {
        quotaUpdater.updateQuota(quota);
    }

    public void onGeolocationPermissionsShowPrompt(String origin,
            GeolocationPermissions.Callback callback) {}

    public void onGeolocationPermissionsHidePrompt() {}

    public void onPermissionRequest(PermissionRequest request) {
        request.deny();
    }

    public void onPermissionRequestCanceled(PermissionRequest request) {}

    // This method was only called when using the JSC javascript engine. V8 became
    // the default JS engine with Froyo and support for building with JSC was
    // removed in b/5495373. V8 does not have a mechanism for making a callback such
    // as this.
    @Deprecated
    public boolean onJsTimeout() {
        return true;
    }

    @Deprecated
    public void onConsoleMessage(String message, int lineNumber, String sourceID) { }

    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        // Call the old version of this function for backwards compatability.
        onConsoleMessage(consoleMessage.message(), consoleMessage.lineNumber(),
                consoleMessage.sourceId());
        return false;
    }

    @Nullable
    public Bitmap getDefaultVideoPoster() {
        return null;
    }

    @Nullable
    public View getVideoLoadingProgressView() {
        return null;
    }

    /** Obtains a list of all visited history items, used for link coloring
     */
    public void getVisitedHistory(ValueCallback<String[]> callback) {
    }

    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
            FileChooserParams fileChooserParams) {
        return false;
    }

    public static abstract class FileChooserParams {
        @SystemApi
        public static final long ENABLE_FILE_SYSTEM_ACCESS = 364980165L;

        /** @hide */
        @Retention(RetentionPolicy.SOURCE)
        public @interface Mode {}

        /** Open single file. Requires that the file exists before allowing the user to pick it. */
        public static final int MODE_OPEN = 0;
        /** Like Open but allows multiple files to be selected. */
        public static final int MODE_OPEN_MULTIPLE = 1;
        /** Like Open but allows a folder to be selected. */
        public static final int MODE_OPEN_FOLDER = 2;
        /**  Allows picking a nonexistent file and saving it. */
        public static final int MODE_SAVE = 3;

        /** @hide */
        @Retention(RetentionPolicy.SOURCE)
        public @interface PermissionMode {}

        /** File or directory should be opened for reading only. */
        public static final int PERMISSION_MODE_READ = 0;
        /** File or directory should be opened for read and write. */
        public static final int PERMISSION_MODE_READ_WRITE = 1;

        @Nullable
        public static Uri[] parseResult(int resultCode, Intent data) {
            throw new RuntimeException("Stub!");
        }

        @Mode
        public abstract int getMode();

        public abstract String[] getAcceptTypes();

        public abstract boolean isCaptureEnabled();

        @Nullable
        public abstract CharSequence getTitle();

        @Nullable
        public abstract String getFilenameHint();

        @PermissionMode
        public int getPermissionMode() {
            return PERMISSION_MODE_READ;
        }

        public abstract Intent createIntent();
    }

    @SystemApi
    @Deprecated
    public void openFileChooser(ValueCallback<Uri> uploadFile, String acceptType, String capture) {
        uploadFile.onReceiveValue(null);
    }
}
