/*
 * Copyright (C) 2018 The Tachiyomi Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ireader.core.http

/**
 * An interface for a persistent cookie store.
 */
interface CookieStore {

  /**
   * Returns a map of all the cookies stored by domain.
   */
  fun load(): Map<String, Set<String>>

  /**
   * Updates the cookies stored for this [domain] with the provided by [cookies].
   */
  fun update(domain: String, cookies: Set<String>)

  /**
   * Clears all the cookies saved in this store.
   */
  fun clear()
}
