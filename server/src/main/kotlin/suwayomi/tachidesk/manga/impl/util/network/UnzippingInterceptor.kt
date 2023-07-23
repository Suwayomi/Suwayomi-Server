package suwayomi.tachidesk.manga.impl.util.network

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response
import okhttp3.internal.http.RealResponseBody
import okio.GzipSource
import okio.buffer
import java.io.IOException

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

// ref: https://stackoverflow.com/questions/51901333/okhttp-3-how-to-decompress-gzip-deflate-response-manually-using-java-android
class UnzippingInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Chain): Response {
        val response: Response = chain.proceed(chain.request())
        return unzip(response)
    }

    @Throws(IOException::class)
    private fun unzip(response: Response): Response {
        // check if we have gzip response
        val contentEncoding: String? = response.headers["Content-Encoding"]

        // this is used to decompress gzipped responses
        return if (contentEncoding != null && contentEncoding == "gzip") {
            val body = response.body
            val contentLength: Long = body.contentLength()
            val responseBody = GzipSource(body.source())
            val strippedHeaders: Headers = response.headers.newBuilder().build()
            response.newBuilder().headers(strippedHeaders)
                .body(RealResponseBody(body.contentType().toString(), contentLength, responseBody.buffer()))
                .build()
        } else {
            response
        }
    }
}
