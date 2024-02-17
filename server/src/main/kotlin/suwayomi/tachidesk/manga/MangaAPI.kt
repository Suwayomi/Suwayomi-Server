package suwayomi.tachidesk.manga

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import suwayomi.tachidesk.manga.controller.BackupController
import suwayomi.tachidesk.manga.controller.CategoryController
import suwayomi.tachidesk.manga.controller.DownloadController
import suwayomi.tachidesk.manga.controller.ExtensionController
import suwayomi.tachidesk.manga.controller.MangaController
import suwayomi.tachidesk.manga.controller.SourceController
import suwayomi.tachidesk.manga.controller.TrackController
import suwayomi.tachidesk.manga.controller.UpdateController
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.withOperation

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

        val jsonMediaType = "application/json".toMediaType()

        @Serializable
        data class AnilistCredential(
            val grant_type: String = "authorization_code",
            val client_id: String? = null,
            val client_secret: String? = null,
            val redirect_uri: String? = null,
            val code: String? = null,
        )

        path("anilist") {
            get(
                "{test2}",
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
                println("********HERE, posting *****")
                runBlocking {
                    val code = ctx.pathParam("code")
//
                    val client = OkHttpClient()
//
                    val aniResond =
                        client.newCall(
                            POST(

                                url = "https://anilist.co/api/v2/oauth/token",
                                body =
                                    Json.encodeToString(
                                        AnilistCredential(
                                            grant_type = "authorization_code",
                                            client_id = "11324",
                                            client_secret = "sdsdfsdfsdfsdfaasdfsdafz9sgdaPwHK",
                                            redirect_uri = "http://localhost:3000/oath/ainilist/auth",
                                            code
                                        ),
                                    ).toRequestBody(jsonMediaType),
                            ),
                        ).awaitSuccess()

                    // Handle the response and retrieve the access_token
                    println(aniResond)

                    ctx.result("Hello: $aniResond")
                    ctx.status(201)
                }
//                ctx.result("post: $code")
//                ctx.status(201)
            }
        }
    }
}
