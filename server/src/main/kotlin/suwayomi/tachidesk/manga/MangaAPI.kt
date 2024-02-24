package suwayomi.tachidesk.manga

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.apibuilder.ApiBuilder.delete
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.patch
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.apibuilder.ApiBuilder.put
import io.javalin.apibuilder.ApiBuilder.ws
import io.javalin.http.HttpCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import suwayomi.tachidesk.manga.controller.BackupController
import suwayomi.tachidesk.manga.controller.CategoryController
import suwayomi.tachidesk.manga.controller.DownloadController
import suwayomi.tachidesk.manga.controller.ExtensionController
import suwayomi.tachidesk.manga.controller.MangaController
import suwayomi.tachidesk.manga.controller.SourceController
import suwayomi.tachidesk.manga.controller.TrackController
import suwayomi.tachidesk.manga.controller.UpdateController
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.withOperation
import java.io.IOException

object MangaAPI {
    fun defineEndpoints() {
        path("extension") {
            get("list", ExtensionController.list)

            get("install/{pkgName}", ExtensionController.install)
            post("install", ExtensionController.installFile)
            get("update/{pkgName}", ExtensionController.update)
            get("uninstall/{pkgName}", ExtensionController.uninstall)

            get("icon/{apkName}", ExtensionController.icon)
        }

        path("source") {
            get("list", SourceController.list)
            get("{sourceId}", SourceController.retrieve)

            get("{sourceId}/popular/{pageNum}", SourceController.popular)
            get("{sourceId}/latest/{pageNum}", SourceController.latest)

            get("{sourceId}/preferences", SourceController.getPreferences)
            post("{sourceId}/preferences", SourceController.setPreference)

            get("{sourceId}/filters", SourceController.getFilters)
            post("{sourceId}/filters", SourceController.setFilters)

            get("{sourceId}/search", SourceController.searchSingle)
            post("{sourceId}/quick-search", SourceController.quickSearchSingle)
//            get("all/search", SourceController.searchGlobal) // TODO
        }

        path("manga") {
            get("{mangaId}", MangaController.retrieve)
            get("{mangaId}/full", MangaController.retrieveFull)
            get("{mangaId}/thumbnail", MangaController.thumbnail)

            get("{mangaId}/category", MangaController.categoryList)
            get("{mangaId}/category/{categoryId}", MangaController.addToCategory)
            delete("{mangaId}/category/{categoryId}", MangaController.removeFromCategory)

            get("{mangaId}/library", MangaController.addToLibrary)
            delete("{mangaId}/library", MangaController.removeFromLibrary)

            patch("{mangaId}/meta", MangaController.meta)

            get("{mangaId}/chapters", MangaController.chapterList)
            post("{mangaId}/chapter/batch", MangaController.chapterBatch)
            get("{mangaId}/chapter/{chapterIndex}", MangaController.chapterRetrieve)
            patch("{mangaId}/chapter/{chapterIndex}", MangaController.chapterModify)
            put("{mangaId}/chapter/{chapterIndex}", MangaController.chapterModify)
            delete("{mangaId}/chapter/{chapterIndex}", MangaController.chapterDelete)

            patch("{mangaId}/chapter/{chapterIndex}/meta", MangaController.chapterMeta)

            get("{mangaId}/chapter/{chapterIndex}/page/{index}", MangaController.pageRetrieve)
        }

        path("chapter") {
            post("batch", MangaController.anyChapterBatch)
        }

        path("category") {
            get("", CategoryController.categoryList)
            post("", CategoryController.categoryCreate)

            // The order here is important {categoryId} needs to be applied last
            // or throws a NumberFormatException
            patch("reorder", CategoryController.categoryReorder)

            get("{categoryId}", CategoryController.categoryMangas)
            patch("{categoryId}", CategoryController.categoryModify)
            delete("{categoryId}", CategoryController.categoryDelete)

            patch("{categoryId}/meta", CategoryController.meta)
        }

        path("backup") {
            post("import", BackupController.protobufImport)
            post("import/file", BackupController.protobufImportFile)

            post("validate", BackupController.protobufValidate)
            post("validate/file", BackupController.protobufValidateFile)

            get("export", BackupController.protobufExport)
            get("export/file", BackupController.protobufExportFile)
        }

        path("downloads") {
            ws("", DownloadController::downloadsWS)

            get("start", DownloadController.start)
            get("stop", DownloadController.stop)
            get("clear", DownloadController.clear)
        }

        path("download") {
            get("{mangaId}/chapter/{chapterIndex}", DownloadController.queueChapter)
            delete("{mangaId}/chapter/{chapterIndex}", DownloadController.unqueueChapter)
            patch("{mangaId}/chapter/{chapterIndex}/reorder/{to}", DownloadController.reorderChapter)
            post("batch", DownloadController.queueChapters)
            delete("batch", DownloadController.unqueueChapters)
        }

        path("update") {
            get("recentChapters/{pageNum}", UpdateController.recentChapters)
            post("fetch", UpdateController.categoryUpdate)
            post("reset", UpdateController.reset)
            get("summary", UpdateController.updateSummary)
            ws("", UpdateController::categoryUpdateWS)
        }

        path("track") {
            get("list", TrackController.list)
            post("login", TrackController.login)
            post("logout", TrackController.logout)
            post("search", TrackController.search)
            post("bind", TrackController.bind)
            post("update", TrackController.update)
            get("{trackerId}/thumbnail", TrackController.thumbnail)
        }

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        @Serializable
        data class AnilistCredential(
            val grant_type: String = "authorization_code",
            val client_id: String? = null,
            val client_secret: String? = null,
            val redirect_uri: String? = null,
            val code: String? = null,
        )

        @Serializable
        data class AnilistBearerToken(
            val token_type: String,
            val expires_in: Long,
            val access_token: String,
            val refresh_token: String,
        )

        path("anilist") {
            val anilist = TrackerManager.aniList

            get(
                "test2",
                handler(
                    documentWith = {
                        withOperation {
                            summary("List Supported Trackers")
                            description("List all supported Trackers")
                        }
                    },
                    behaviorOf = { ctx ->
                        val here = ctx.pathParam("test2")
                        println("worked the get")
                        ctx.result("lskdjl$here")
                        ctx.status(200)
                    },
                    withResults = {
                        HttpCode.OK
                    },
                ),
            )
            post("{code}") { ctx ->
//
                print("********HERE, posting *****")
                println(anilist.isLoggedIn)

                runBlocking {
                    val accessCode = ctx.pathParam("code")

                    val payload =
                        buildJsonObject {
                            put("grant_type", "authorization_code")
                            put("client_id", "16")
                            put("client_secret", "xIJuPwHK")
                            put("redirect_uri", "http://localhost:3000/oath/ainilist/auth")
                            put("code", accessCode)
                        }

                    val client = OkHttpClient()

                    val request =
                        Request.Builder()
//                            .url("https://jsonplaceholder.typicode.com/posts")
                            .url("https://anilist.co/api/v2/oauth/token")
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Accept", "application/json")
                            .post(payload.toString().toRequestBody(jsonMediaType))
                            .build()

                    val aniResponse =
                        client.newCall(request).enqueue(
                            object : Callback {
                                override fun onFailure(
                                    call: Call,
                                    e: IOException,
                                ) {
                                    e.printStackTrace()
                                }

                                override fun onResponse(
                                    call: Call,
                                    response: Response,
                                ) {
                                    runBlocking {
                                        response.use {
                                            if (!response.isSuccessful) throw IOException("Unexpected code $response")

                                            // Print the access token
                                            val responseBody =
                                                Json.decodeFromString<AnilistBearerToken>(response.body!!.string())
                                            print("HERE IS THE BODY: ")
                                            println(responseBody)

                                            anilist.loginWithToken(responseBody.access_token)
                                            print("ANILIST logged in: " )
                                            println(anilist.isLoggedIn)

//                                        println(response.body!!.string())
                                            // Need to save the token using the anilist login method
                                        }
                                    }
                                }
                            },
                        )

                    ctx.result("Hello: it worked $aniResponse")
                    ctx.status(201)
                }
            }
        }
    }
}
