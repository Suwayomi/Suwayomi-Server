package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.Javalin
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImageResultTest {
    private lateinit var app: Javalin
    private var image = "first-image".toByteArray()

    @BeforeEach
    fun startServer() {
        app =
            Javalin
                .create { config ->
                    config.routes.get("/image") { ctx ->
                        ctx.cachedImageResult(image.inputStream(), "image/png", maxAgeSeconds = 60)
                    }
                }
                // loopback only: listening on every interface makes the OS firewall ask to let Java in
                .start("127.0.0.1", 0)
    }

    @AfterEach
    fun stopServer() {
        app.stop()
    }

    @Test
    fun servesTheImageWithItsCacheHeadersAndAnETag() {
        get().use { response ->
            assertEquals(200, response.code)
            assertEquals("image/png", response.header("content-type"))
            assertEquals("max-age=60", response.header("cache-control"))
            assertNotNull(response.header("etag"))
            assertContentEquals(image, response.body.bytes())
        }
    }

    @Test
    fun answersNotModifiedWhenTheClientHasTheSameImage() {
        val etag = get().use { assertNotNull(it.header("etag")) }

        get(ifNoneMatch = etag).use { response ->
            assertEquals(304, response.code)
            assertTrue(response.body.bytes().isEmpty())
        }
    }

    @Test
    fun sendsTheNewImageWhenItChanged() {
        val etag = get().use { assertNotNull(it.header("etag")) }
        image = "second-image".toByteArray()

        get(ifNoneMatch = etag).use { response ->
            assertEquals(200, response.code)
            assertNotEquals(etag, response.header("etag"))
            assertContentEquals(image, response.body.bytes())
        }
    }

    private fun get(ifNoneMatch: String? = null): Response =
        OkHttpClient()
            .newCall(
                Request
                    .Builder()
                    .url("http://127.0.0.1:${app.port()}/image")
                    .apply { ifNoneMatch?.let { header("If-None-Match", it) } }
                    .build(),
            ).execute()
}
