package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.Context
import java.io.InputStream
import java.security.MessageDigest

/**
 * Answer with an image clients may cache for [maxAgeSeconds].
 *
 * The image gets an ETag from its content, so once the cache expired a client revalidating with `If-None-Match`
 * gets a 304 instead of the whole image when it didn't change (Javalin compares the ETag and answers the 304).
 */
fun Context.cachedImageResult(
    image: InputStream,
    mimeType: String,
    maxAgeSeconds: Long,
): Context {
    val bytes = image.use { it.readBytes() }
    header("content-type", mimeType)
    header("cache-control", "max-age=$maxAgeSeconds")
    header("etag", imageETag(bytes))
    return result(bytes)
}

internal fun imageETag(image: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(image)
    // 128 bits are plenty to tell two versions of an image apart
    return "\"" + digest.take(16).joinToString("") { "%02x".format(it) } + "\""
}
