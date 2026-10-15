package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.delete
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.patch
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.HttpStatus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import suwayomi.tachidesk.graphql.controller.GraphQLController
import suwayomi.tachidesk.manga.controller.DownloadController
import suwayomi.tachidesk.manga.controller.ExtensionController
import suwayomi.tachidesk.manga.controller.SourceController
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.defineExceptionHandlers
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Asserts that the REST write endpoints carry the same per-endpoint permission checks as
 * their GraphQL counterparts. The real production handlers are mounted on a test Javalin
 * instance; the resolved [UserType] is injected through a test-only header instead of a real
 * auth mode so each row of the endpoint/permission matrix can be exercised directly.
 *
 * The authorization check happens before any side effect, so the interesting statuses are
 * 401 (unauthenticated), 403 (authenticated but missing the permission) and anything else
 * (the check passed and the request reached business logic, which then fails or succeeds
 * for unrelated reasons in the test environment).
 */
class RestPermissionsTest : ApplicationTest() {
    private lateinit var app: Javalin

    private val client =
        OkHttpClient
            .Builder()
            .build()

    @BeforeEach
    fun startServer() {
        app =
            Javalin
                .create { config ->
                    config.routes.defineExceptionHandlers()

                    // test-only auth resolution: the X-Test-* headers pick the UserType that
                    // the production beforeMatched filter would normally resolve
                    config.routes.beforeMatched { ctx ->
                        val permissions =
                            ctx
                                .header(PERMISSIONS_HEADER)
                                ?.split(',')
                                ?.filter { it.isNotBlank() }
                                ?.map { UserPermission.valueOf(it.trim()) }
                                .orEmpty()
                        val user =
                            when (ctx.header(USER_HEADER)) {
                                "admin" -> UserType.Admin(1)
                                "visitor" -> UserType.Visitor
                                else -> UserType.User(id = 42, permissions = permissions)
                            }
                        ctx.attribute(Attribute.TachideskUser.name, user)
                        ctx.attribute(Attribute.TachideskBasic.name, false)
                    }

                    config.routes.apiBuilder {
                        path("api/v1/") {
                            path("extension") {
                                get("install/{pkgName}", ExtensionController.install)
                                post("install", ExtensionController.installFile)
                                get("update/{pkgName}", ExtensionController.update)
                                get("uninstall/{pkgName}", ExtensionController.uninstall)
                            }
                            path("source") {
                                post("{sourceId}/preferences", SourceController.setPreference)
                                post("{sourceId}/filters", SourceController.setFilters)
                            }
                            path("downloads") {
                                get("start", DownloadController.start)
                                get("stop", DownloadController.stop)
                                get("clear", DownloadController.clear)
                            }
                            path("download") {
                                patch("{mangaId}/chapter/{chapterIndex}/reorder/{to}", DownloadController.reorderChapter)
                            }
                            // mounted the same way GraphQL.defineEndpoints mounts it under /api
                            path("graphql/files") {
                                get("backup/{file}", GraphQLController::retrieveFile)
                            }
                        }
                    }
                }.start("127.0.0.1", 0)
    }

    @AfterEach
    fun stopServer() {
        app.stop()
    }

    private enum class Method {
        GET,
        POST,
        PATCH,
        DELETE,
    }

    private data class Endpoint(
        val method: Method,
        val path: String,
        val permission: UserPermission,
    )

    private val gatedEndpoints =
        listOf(
            Endpoint(Method.GET, "extension/install/some.pkg", UserPermission.INSTALL_EXTENSIONS),
            Endpoint(Method.POST, "extension/install", UserPermission.INSTALL_EXTERNAL_EXTENSIONS),
            Endpoint(Method.GET, "extension/update/some.pkg", UserPermission.INSTALL_EXTENSIONS),
            Endpoint(Method.GET, "extension/uninstall/some.pkg", UserPermission.UNINSTALL_EXTENSIONS),
            Endpoint(Method.POST, "source/1/preferences", UserPermission.MANAGE_SOURCE_PREFERENCES),
            Endpoint(Method.POST, "source/1/filters", UserPermission.MANAGE_SOURCE_PREFERENCES),
            Endpoint(Method.GET, "downloads/start", UserPermission.DOWNLOAD_CHAPTERS),
            Endpoint(Method.GET, "downloads/stop", UserPermission.DOWNLOAD_CHAPTERS),
            Endpoint(Method.GET, "downloads/clear", UserPermission.DOWNLOAD_CHAPTERS),
            Endpoint(Method.PATCH, "download/1/chapter/1/reorder/0", UserPermission.DOWNLOAD_CHAPTERS),
        )

    private fun request(
        endpoint: Endpoint,
        userHeader: String,
        permissionsHeader: String? = null,
    ): Int {
        val body: RequestBody? =
            when (endpoint.method) {
                Method.POST, Method.PATCH -> "[]".toRequestBody("application/json".toMediaType())
                else -> null
            }
        val builder =
            Request
                .Builder()
                .url("http://127.0.0.1:${app.port()}/api/v1/${endpoint.path}")
                .header(USER_HEADER, userHeader)
        permissionsHeader?.let { builder.header(PERMISSIONS_HEADER, it) }
        when (endpoint.method) {
            Method.GET -> builder.get()
            Method.POST -> builder.post(body!!)
            Method.PATCH -> builder.patch(body!!)
            Method.DELETE -> builder.delete(body)
        }
        return client.newCall(builder.build()).execute().use { it.code }
    }

    @Test
    fun unauthenticatedRequestsAreRejected() {
        gatedEndpoints.forEach { endpoint ->
            assertEquals(
                HttpStatus.UNAUTHORIZED.code,
                request(endpoint, userHeader = "visitor"),
                "expected 401 for unauthenticated ${endpoint.method} ${endpoint.path}",
            )
        }
    }

    @Test
    fun zeroPermissionUsersAreForbidden() {
        gatedEndpoints.forEach { endpoint ->
            assertEquals(
                HttpStatus.FORBIDDEN.code,
                request(endpoint, userHeader = "user"),
                "expected 403 for a zero-permission user on ${endpoint.method} ${endpoint.path}",
            )
        }
    }

    @Test
    fun usersWithTheMappedPermissionPassTheGate() {
        gatedEndpoints.forEach { endpoint ->
            val status =
                request(endpoint, userHeader = "user", permissionsHeader = endpoint.permission.name)
            assertTrue(
                status != HttpStatus.FORBIDDEN.code && status != HttpStatus.UNAUTHORIZED.code,
                "expected the permission check to pass for ${endpoint.method} ${endpoint.path}, got $status",
            )
        }
    }

    @Test
    fun unauthenticatedBackupFileDownloadIsRejected() {
        // GET /api/graphql/files/backup/{file} gates on requireUser() only: any logged-in
        // user may download a stored backup (the URL is unguessable), a visitor may not
        val download = { user: String ->
            client
                .newCall(
                    Request
                        .Builder()
                        .url("http://127.0.0.1:${app.port()}/api/v1/graphql/files/backup/missing.tachibk")
                        .header(USER_HEADER, user)
                        .get()
                        .build(),
                ).execute()
                .use { it.code }
        }

        assertEquals(HttpStatus.UNAUTHORIZED.code, download("visitor"))

        val authenticatedStatus = download("user")
        assertTrue(
            authenticatedStatus != HttpStatus.UNAUTHORIZED.code && authenticatedStatus != HttpStatus.FORBIDDEN.code,
            "expected a logged-in user to pass the download gate, got $authenticatedStatus",
        )
    }

    @Test
    fun adminPassesEveryGate() {
        // AuthMode.NONE/BASIC_AUTH/SIMPLE_LOGIN all resolve to the built-in admin
        gatedEndpoints.forEach { endpoint ->
            val status = request(endpoint, userHeader = "admin")
            assertTrue(
                status != HttpStatus.FORBIDDEN.code && status != HttpStatus.UNAUTHORIZED.code,
                "expected the admin to pass ${endpoint.method} ${endpoint.path}, got $status",
            )
        }
    }

    private companion object {
        const val USER_HEADER = "X-Test-User"
        const val PERMISSIONS_HEADER = "X-Test-Permissions"
    }
}
