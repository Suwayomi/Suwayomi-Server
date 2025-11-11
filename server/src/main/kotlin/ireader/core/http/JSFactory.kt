/*
 * Copyright (C) 2018 The Tachiyomi Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ireader.core.http

/**
 * A factory for creating [JS] instances.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect class JSFactory {

  /**
   * Returns a new instance of [JS].
   */
  fun create(): JS

}
