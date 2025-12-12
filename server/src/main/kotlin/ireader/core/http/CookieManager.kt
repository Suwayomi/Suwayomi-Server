/*
 * Copyright (C) 2018 The Tachiyomi Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ireader.core.http

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A cookie manager for saving cookies from network responses, allowing to send them in succesive
 * requests. There's an in-memory copy of the cookies through [cache] and a persistent copy
 * through [store] so that they are restored when the application is restarted.
 */
class CookieManager(private val store: CookieStore) : CookieJar {

  /**
   * Map containing the currently cached list of cookies by domain (e.g., example.com).
   */
  private val cache = hashMapOf<String, List<Cookie>>()

  init {
    for ((domain, cookies) in store.load()) {
      try {
        // A valid scheme is required to create an url. It doesn't matter if the cookie is secure
        // or not as the scheme part is ignored while creating the cookie.
        val url = "http://$domain".toHttpUrlOrNull() ?: continue

        val nonExpiredCookies = cookies.mapNotNull { Cookie.parse(url, it) }
          .filter { !it.hasExpired() }
        cache[domain] = nonExpiredCookies
      } catch (e: Exception) {
        // Ignore
      }
    }
  }

  /**
   * Updates the current list of cookies with the provided [cookies] for the domain given by [url].
   * They are saved in the in-memory cache, but also in the store for the ones marked as persistent.
   */
  @Synchronized
  override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
    val domain = url.host

    // Append or replace the cookies for this domain.
    val cookiesForDomain = cache[domain].orEmpty().toMutableList()
    for (cookie in cookies) {
      // Find a cookie with the same name. Replace it if found, otherwise add a new one.
      val pos = cookiesForDomain.indexOfFirst { it.name == cookie.name }
      if (pos == -1) {
        cookiesForDomain.add(cookie)
      } else {
        cookiesForDomain[pos] = cookie
      }
    }
    cache[domain] = cookiesForDomain

    // Get cookies to be stored
    val newValues = cookiesForDomain.asSequence()
      .filter { it.persistent && !it.hasExpired() }
      .map(Cookie::toString)
      .toSet()

    store.update(domain, newValues)
  }

  /**
   * Returns the list of cookies to append for a request to [url].
   */
  override fun loadForRequest(url: HttpUrl): List<Cookie> {
    val cookies = cache[url.host].orEmpty().filter { !it.hasExpired() }
    return if (url.isHttps) {
      cookies
    } else {
      cookies.filter { !it.secure }
    }
  }

  /**
   * Clears all the cookies from both the in-memory and the persistent store.
   */
  @Synchronized
  fun clear() {
    cache.clear()
    store.clear()
  }

  /**
   * Returns true if this cookie has expired.
   */
  private fun Cookie.hasExpired() = System.currentTimeMillis() >= expiresAt
}
