package eu.kanade.tachiyomi.network

import okhttp3.Response
import java.io.IOException

/**
 * Exception that handles HTTP codes considered not successful by OkHttp.
 * Use it to have a standardized error message in the app across the extensions.
 *
 * Extends [IOException] so `catch(IOException)` covers HTTP error responses the same
 * way as transport failures — upstream callers treating network errors uniformly
 * (retry, offline detection, TokenRefreshFailed-style remapping) get that for free.
 * Catch sites that need the HTTP status code keep working: `catch(HttpException)`
 * still matches first when declared before a sibling `catch(IOException)` (e.g.
 * `Manga.fetchHttpSourceMangaThumbnail`), and Javalin's exact-class exception
 * mapping keeps routing HttpException to its dedicated 424 handler.
 *
 * Known semantic consequence: inside a `try { ... awaitSuccess() ... } catch(e: IOException)`
 * block a 4xx/5xx is now classified as a generic IO/network error — e.g. a tracker
 * interceptor would remap it to TokenRefreshFailed. The current interceptors only
 * wrap `chain.proceed` (which cannot raise HttpException), so this is dormant, but
 * keep the two failure classes apart when adding `awaitSuccess` inside such scopes.
 *
 * @see Response.isSuccessful
 * @since tachiyomix 1.6
 * @param code [Int] the HTTP status code
 */
class HttpException(
    val code: Int,
) : IOException("HTTP error $code")
