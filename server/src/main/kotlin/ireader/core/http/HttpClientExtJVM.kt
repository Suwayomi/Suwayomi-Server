/*
 * Copyright (C) 2018 The Tachiyomi Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ireader.core.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.http
import io.ktor.client.engine.okhttp.OkHttpConfig
import okhttp3.OkHttpClient

val HttpClient.okhttp: OkHttpClient
    get() = (engineConfig as OkHttpConfig).preconfigured!!

