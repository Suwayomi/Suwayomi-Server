package eu.kanade.tachiyomi.network.interceptor

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FlareSolverSessionManagerTest {
    @Test
    fun `first use replaces an existing session with a proxied session`() =
        runTest {
            val commands = mutableListOf<CFClearance.FlareSolverRequest>()
            val manager = FlareSolverSessionManager()

            manager.ensure(settings()) { _, request ->
                commands += request
                when (request.cmd) {
                    "sessions.list" -> response(sessions = listOf("suwayomi"))
                    else -> response()
                }
            }

            assertEquals(
                listOf("sessions.list", "sessions.destroy", "sessions.create"),
                commands.map { it.cmd },
            )
            assertEquals("socks5://proxy.example:1080", commands.last().proxy?.url)
        }

    @Test
    fun `subsequent use keeps the configured session and recovers after solver restart`() =
        runTest {
            val commands = mutableListOf<CFClearance.FlareSolverRequest>()
            var sessionExists = false
            val manager = FlareSolverSessionManager()
            val send: suspend (String, CFClearance.FlareSolverRequest) -> CFClearance.FlareSolverCommandResponse =
                { _, request ->
                    commands += request
                    when (request.cmd) {
                        "sessions.list" -> {
                            response(sessions = emptyList())
                        }

                        "sessions.create" -> {
                            val message = if (sessionExists) "Session already exists." else "Session created successfully."
                            sessionExists = true
                            response(message = message)
                        }

                        else -> {
                            response()
                        }
                    }
                }

            manager.ensure(settings(), send)
            manager.ensure(settings(), send)
            sessionExists = false
            manager.ensure(settings(), send)

            assertEquals(
                listOf("sessions.list", "sessions.create", "sessions.create", "sessions.create"),
                commands.map { it.cmd },
            )
        }

    @Test
    fun `proxy changes recreate only the configured session`() =
        runTest {
            val commands = mutableListOf<CFClearance.FlareSolverRequest>()
            val manager = FlareSolverSessionManager()

            manager.ensure(settings()) { _, request ->
                commands += request
                if (request.cmd == "sessions.list") response(sessions = emptyList()) else response()
            }
            manager.ensure(settings(proxyUrl = "socks5://new.example:1080")) { _, request ->
                commands += request
                if (request.cmd == "sessions.list") {
                    response(sessions = listOf("suwayomi", "another-client"))
                } else {
                    response()
                }
            }

            assertEquals(1, commands.count { it.cmd == "sessions.destroy" })
            assertEquals("suwayomi", commands.single { it.cmd == "sessions.destroy" }.session)
            assertEquals("socks5://new.example:1080", commands.last().proxy?.url)
        }

    @Test
    fun `expired session is recreated on the next use without background work`() =
        runTest {
            var now = 0L
            val commands = mutableListOf<CFClearance.FlareSolverRequest>()
            val manager = FlareSolverSessionManager { now }
            val send: suspend (String, CFClearance.FlareSolverRequest) -> CFClearance.FlareSolverCommandResponse =
                { _, request ->
                    commands += request
                    if (request.cmd == "sessions.list") response(sessions = listOf("suwayomi")) else response()
                }

            manager.ensure(settings(ttlMinutes = 10), send)
            now = 11L * 60 * 1_000_000_000
            manager.ensure(settings(ttlMinutes = 10), send)

            assertEquals(2, commands.count { it.cmd == "sessions.destroy" })
            assertEquals(2, commands.count { it.cmd == "sessions.create" })
        }

    @Test
    fun `server error recreates the session and retries the request once`() =
        runTest {
            val solver = FakeSessionCommands()
            var requestAttempts = 0
            val manager = FlareSolverSessionManager()

            val result =
                manager.execute(settings(), solver::send, retryOnFailure = true) {
                    requestAttempts++
                    if (requestAttempts == 1) throw HttpException(500)
                    "success"
                }

            assertEquals("success", result)
            assertEquals(2, requestAttempts)
            assertEquals(
                listOf("sessions.list", "sessions.create", "sessions.list", "sessions.destroy", "sessions.create"),
                solver.commands.map { it.cmd },
            )
        }

    @Test
    fun `server error invalidates the session without retrying non-idempotent requests`() =
        runTest {
            val solver = FakeSessionCommands()
            var requestAttempts = 0
            val manager = FlareSolverSessionManager()

            assertFailsWith<HttpException> {
                manager.execute(settings(), solver::send, retryOnFailure = false) {
                    requestAttempts++
                    throw HttpException(500)
                }
            }

            assertEquals(1, requestAttempts)
            assertEquals(false, solver.sessionExists)
            assertEquals(
                listOf("sessions.list", "sessions.create", "sessions.list", "sessions.destroy"),
                solver.commands.map { it.cmd },
            )
        }

    @Test
    fun `failed retry invalidates the recreated session`() =
        runTest {
            val solver = FakeSessionCommands()
            var requestAttempts = 0
            val manager = FlareSolverSessionManager()

            val error =
                assertFailsWith<HttpException> {
                    manager.execute(settings(), solver::send, retryOnFailure = true) {
                        requestAttempts++
                        throw HttpException(500)
                    }
                }

            assertEquals(2, requestAttempts)
            assertEquals(false, solver.sessionExists)
            assertEquals(1, error.suppressed.size)
            assertEquals(
                listOf(
                    "sessions.list",
                    "sessions.create",
                    "sessions.list",
                    "sessions.destroy",
                    "sessions.create",
                    "sessions.list",
                    "sessions.destroy",
                ),
                solver.commands.map { it.cmd },
            )
        }

    @Test
    fun `non-server error does not recreate or retry the session`() =
        runTest {
            val commands = mutableListOf<CFClearance.FlareSolverRequest>()
            var requestAttempts = 0
            val manager = FlareSolverSessionManager()

            assertFailsWith<HttpException> {
                manager.execute(
                    settings(),
                    { _, request ->
                        commands += request
                        if (request.cmd == "sessions.list") response(sessions = emptyList()) else response()
                    },
                    retryOnFailure = true,
                ) {
                    requestAttempts++
                    throw HttpException(502)
                }
            }

            assertEquals(1, requestAttempts)
            assertEquals(listOf("sessions.list", "sessions.create"), commands.map { it.cmd })
        }

    private fun settings(
        proxyUrl: String = "socks5://proxy.example:1080",
        ttlMinutes: Int = 30,
    ) = FlareSolverSessionSettings(
        url = "http://localhost:8191/v1",
        name = "suwayomi",
        ttlMinutes = ttlMinutes,
        proxy = CFClearance.FlareSolverProxy(proxyUrl),
    )

    private fun response(
        message: String = "",
        sessions: List<String>? = null,
    ) = CFClearance.FlareSolverCommandResponse(
        status = "ok",
        message = message,
        sessions = sessions,
    )

    private class FakeSessionCommands {
        val commands = mutableListOf<CFClearance.FlareSolverRequest>()
        var sessionExists = false

        suspend fun send(
            url: String,
            request: CFClearance.FlareSolverRequest,
        ): CFClearance.FlareSolverCommandResponse {
            commands += request
            return when (request.cmd) {
                "sessions.list" -> response(sessions = if (sessionExists) listOf("suwayomi") else emptyList())
                "sessions.create" -> response().also { sessionExists = true }
                "sessions.destroy" -> response().also { sessionExists = false }
                else -> response()
            }
        }

        private fun response(sessions: List<String>? = null) =
            CFClearance.FlareSolverCommandResponse(
                status = "ok",
                message = "",
                sessions = sessions,
            )
    }
}
