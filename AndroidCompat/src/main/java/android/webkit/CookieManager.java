package android.webkit;

import android.annotation.Nullable;
import xyz.nulldev.androidcompat.webkit.CookieManagerImpl;

public abstract class CookieManager {
    /**
     * @deprecated This class should not be constructed by applications, use {@link #getInstance}
     * instead to fetch the singleton instance.
     */
    // TODO(ntfschr): mark this as @SystemApi after a year.
    @Deprecated
    public CookieManager() {}
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException("doesn't implement Cloneable");
    }

    private static CookieManager INSTANCE = null;
    private static final Object lock = new Object();
    /**
     * Gets the singleton CookieManager instance.
     *
     * @return the singleton CookieManager instance
     */
    public static CookieManager getInstance() {
        if (INSTANCE != null) {
            return INSTANCE;
        } else {
            synchronized (lock) {
                if (INSTANCE == null) {
                    INSTANCE = new CookieManagerImpl();
                }
                return INSTANCE;
            }
        }
    }
    /**
     * Sets whether the application's {@link WebView} instances should send and
     * accept cookies.
     * By default this is set to {@code true} and the WebView accepts cookies.
     * <p>
     * When this is {@code true}
     * {@link CookieManager#setAcceptThirdPartyCookies setAcceptThirdPartyCookies} and
     * {@link CookieManager#setAcceptFileSchemeCookies setAcceptFileSchemeCookies}
     * can be used to control the policy for those specific types of cookie.
     *
     * @param accept whether {@link WebView} instances should send and accept
     *               cookies
     */
    public abstract void setAcceptCookie(boolean accept);
    /**
     * Gets whether the application's {@link WebView} instances send and accept
     * cookies.
     *
     * @return {@code true} if {@link WebView} instances send and accept cookies
     */
    public abstract boolean acceptCookie();
    /**
     * Sets whether the {@link WebView} should allow third party cookies to be set.
     * Allowing third party cookies is a per WebView policy and can be set
     * differently on different WebView instances.
     * <p>
     * Apps that target {@link android.os.Build.VERSION_CODES#KITKAT} or below
     * default to allowing third party cookies. Apps targeting
     * {@link android.os.Build.VERSION_CODES#LOLLIPOP} or later default to disallowing
     * third party cookies.
     *
     * @param webview the {@link WebView} instance to set the cookie policy on
     * @param accept whether the {@link WebView} instance should accept
     *               third party cookies
     */
    public abstract void setAcceptThirdPartyCookies(WebView webview, boolean accept);
    /**
     * Gets whether the {@link WebView} should allow third party cookies to be set.
     *
     * @param webview the {@link WebView} instance to get the cookie policy for
     * @return {@code true} if the {@link WebView} accepts third party cookies
     */
    public abstract boolean acceptThirdPartyCookies(WebView webview);
    /**
     * Sets a single cookie (key-value pair) for the given URL. Any existing cookie with the same
     * host, path and name will be replaced with the new cookie. The cookie being set
     * will be ignored if it is expired. To set multiple cookies, your application should invoke
     * this method multiple times.
     *
     * <p>The {@code value} parameter must follow the format of the {@code Set-Cookie} HTTP
     * response header defined by
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-03">RFC6265bis</a>.
     * This is a key-value pair of the form {@code "key=value"}, optionally followed by a list of
     * cookie attributes delimited with semicolons (ex. {@code "key=value; Max-Age=123"}). Please
     * consult the RFC specification for a list of valid attributes.
     *
     * <p class="note"><b>Note:</b> if specifying a {@code value} containing the {@code "Secure"}
     * attribute, {@code url} must use the {@code "https://"} scheme.
     *
     * @param url the URL for which the cookie is to be set
     * @param value the cookie as a string, using the format of the 'Set-Cookie'
     *              HTTP response header
     */
    public abstract void setCookie(String url, String value);
    /**
     * Sets a single cookie (key-value pair) for the given URL. Any existing cookie with the same
     * host, path and name will be replaced with the new cookie. The cookie being set
     * will be ignored if it is expired. To set multiple cookies, your application should invoke
     * this method multiple times.
     *
     * <p>The {@code value} parameter must follow the format of the {@code Set-Cookie} HTTP
     * response header defined by
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-03">RFC6265bis</a>.
     * This is a key-value pair of the form {@code "key=value"}, optionally followed by a list of
     * cookie attributes delimited with semicolons (ex. {@code "key=value; Max-Age=123"}). Please
     * consult the RFC specification for a list of valid attributes.
     *
     * <p>This method is asynchronous. If a {@link ValueCallback} is provided,
     * {@link ValueCallback#onReceiveValue} will be called on the current
     * thread's {@link android.os.Looper} once the operation is complete.
     * The value provided to the callback indicates whether the cookie was set successfully.
     * You can pass {@code null} as the callback if you don't need to know when the operation
     * completes or whether it succeeded, and in this case it is safe to call the method from a
     * thread without a Looper.
     *
     * <p class="note"><b>Note:</b> if specifying a {@code value} containing the {@code "Secure"}
     * attribute, {@code url} must use the {@code "https://"} scheme.
     *
     * @param url the URL for which the cookie is to be set
     * @param value the cookie as a string, using the format of the 'Set-Cookie'
     *              HTTP response header
     * @param callback a callback to be executed when the cookie has been set
     */
    public abstract void setCookie(String url, String value, @Nullable ValueCallback<Boolean>
            callback);
    /**
     * Gets all the cookies for the given URL. This may return multiple key-value pairs if multiple
     * cookies are associated with this URL, in which case each cookie will be delimited by {@code
     * "; "} characters (semicolon followed by a space). Each key-value pair will be of the form
     * {@code "key=value"}.
     *
     * @param url the URL for which the cookies are requested
     * @return value the cookies as a string, using the format of the 'Cookie'
     *               HTTP request header
     */
    public abstract String getCookie(String url);

    /**
     * Removes all session cookies, which are cookies without an expiration
     * date.
     * @deprecated use {@link #removeSessionCookies(ValueCallback)} instead.
     */
    @Deprecated
    public abstract void removeSessionCookie();
    /**
     * Removes all session cookies, which are cookies without an expiration
     * date.
     * <p>
     * This method is asynchronous.
     * If a {@link ValueCallback} is provided,
     * {@link ValueCallback#onReceiveValue(Object)} will be called on the current
     * thread's {@link android.os.Looper} once the operation is complete.
     * The value provided to the callback indicates whether any cookies were removed.
     * You can pass {@code null} as the callback if you don't need to know when the operation
     * completes or whether any cookie were removed, and in this case it is safe to call the
     * method from a thread without a Looper.
     * @param callback a callback which is executed when the session cookies have been removed
     */
    public abstract void removeSessionCookies(@Nullable ValueCallback<Boolean> callback);
    /**
     * Removes all cookies.
     * @deprecated Use {@link #removeAllCookies(ValueCallback)} instead.
     */
    @Deprecated
    public abstract void removeAllCookie();
    /**
     * Removes all cookies.
     * <p>
     * This method is asynchronous.
     * If a {@link ValueCallback} is provided,
     * {@link ValueCallback#onReceiveValue(Object)}  will be called on the current
     * thread's {@link android.os.Looper} once the operation is complete.
     * The value provided to the callback indicates whether any cookies were removed.
     * You can pass {@code null} as the callback if you don't need to know when the operation
     * completes or whether any cookies were removed, and in this case it is safe to call the
     * method from a thread without a Looper.
     * @param callback a callback which is executed when the cookies have been removed
     */
    public abstract void removeAllCookies(@Nullable ValueCallback<Boolean> callback);
    /**
     * Gets whether there are stored cookies.
     *
     * @return {@code true} if there are stored cookies
     */
    public abstract boolean hasCookies();
    /**
     * Removes all expired cookies.
     * @deprecated The WebView handles removing expired cookies automatically.
     */
    @Deprecated
    public abstract void removeExpiredCookie();
    /**
     * Ensures all cookies currently accessible through the getCookie API are
     * written to persistent storage.
     * This call will block the caller until it is done and may perform I/O.
     */
    public abstract void flush();
    /**
     * Gets whether the application's {@link WebView} instances send and accept
     * cookies for file scheme URLs.
     *
     * @return {@code true} if {@link WebView} instances send and accept cookies for
     *         file scheme URLs
     */
    // Static for backward compatibility.
    public static boolean allowFileSchemeCookies() {
        return getInstance().allowFileSchemeCookiesImpl();
    }

    public abstract boolean allowFileSchemeCookiesImpl();

    /**
     * Sets whether the application's {@link WebView} instances should send and accept cookies for
     * file scheme URLs.
     * <p>
     * Use of cookies with file scheme URLs is potentially insecure and turned off by default. All
     * {@code file://} URLs share all their cookies, which may lead to leaking private app cookies
     * (ex. any malicious file can access cookies previously set by other (trusted) files).
     * <p class="note">
     * Loading content via {@code file://} URLs is generally discouraged. See the note in
     * {@link WebSettings#setAllowFileAccess}.
     * Using <a href="{@docRoot}reference/androidx/webkit/WebViewAssetLoader.html">
     * androidx.webkit.WebViewAssetLoader</a> to load files over {@code http(s)://} URLs allows
     * the standard web security model to be used for setting and sharing cookies for local files.
     * <p>
     * Note that calls to this method will have no effect if made after calling other
     * {@link CookieManager} APIs.
     *
     * @deprecated This setting is not secure, please use
     *             <a href="{@docRoot}reference/androidx/webkit/WebViewAssetLoader.html">
     *             androidx.webkit.WebViewAssetLoader</a> instead.
     */
    // Static for backward compatibility.
    @Deprecated
    public static void setAcceptFileSchemeCookies(boolean accept) {
        getInstance().setAcceptFileSchemeCookiesImpl(accept);
    }

    public abstract void setAcceptFileSchemeCookiesImpl(boolean accept);
}