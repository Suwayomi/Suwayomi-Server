package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.HttpException
import io.javalin.Javalin
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import suwayomi.tachidesk.server.JavalinSetup.defineExceptionHandlers
import java.io.IOException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals

class JavalinExceptionHandlersTest {
    private lateinit var app: Javalin

    @BeforeEach
    fun startServer() {
        app =
            Javalin
                .create { config ->
                    config.routes.defineExceptionHandlers()
                    config.routes.get("/source-error") { throw HttpException(404) }
                    config.routes.get("/source-unreachable") {
                        throw IOException("cdn.example: Name or service not known", UnknownHostException("cdn.example"))
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
    fun aSourceAnsweringWithAnErrorIsAFailedDependency() {
        // not a 500: the source answered, the server did nothing wrong. Not a 502 either, which
        // clients read as the server being unreachable through its proxy
        assertEquals(424 to "HTTP error 404", get("/source-error"))
    }

    @Test
    fun anUnreachableSourceIsAFailedDependency() {
        assertEquals(424, get("/source-unreachable").first)
    }

    private fun get(path: String): Pair<Int, String> =
        OkHttpClient()
            .newCall(Request.Builder().url("http://127.0.0.1:${app.port()}$path").build())
            .execute()
            .use { it.code to it.body.string() }
}
