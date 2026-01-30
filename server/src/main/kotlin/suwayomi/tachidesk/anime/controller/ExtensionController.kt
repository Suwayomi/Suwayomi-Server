package suwayomi.tachidesk.anime.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.HttpStatus
import suwayomi.tachidesk.anime.impl.extension.AnimeExtension
import suwayomi.tachidesk.anime.impl.extension.AnimeExtensionsList
import suwayomi.tachidesk.anime.model.dataclass.AnimeExtensionDataClass
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.withOperation
import kotlin.time.Duration.Companion.days

object ExtensionController {
    private val logger = KotlinLogging.logger {}

    /** list all extensions */
    val list =
        handler(
            documentWith = {
                withOperation {
                    summary("Anime extension list")
                    description("List all anime extensions")
                }
            },
            behaviorOf = { ctx ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future {
                        AnimeExtensionsList.getExtensionList()
                    }.thenApply {
                        ctx.json(it)
                    }
                }
            },
            withResults = {
                json<Array<AnimeExtensionDataClass>>(HttpStatus.OK)
            },
        )

    /** install extension identified with "pkgName" */
    val install =
        handler(
            pathParam<String>("pkgName"),
            documentWith = {
                withOperation {
                    summary("Anime extension install")
                    description("Install anime extension identified with \"pkgName\"")
                }
            },
            behaviorOf = { ctx, pkgName ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future {
                        AnimeExtension.installExtension(pkgName)
                    }.thenApply {
                        ctx.status(it)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.CREATED)
                httpCode(HttpStatus.FOUND)
                httpCode(HttpStatus.INTERNAL_SERVER_ERROR)
            },
        )

    /** install the uploaded apk file */
    val installFile =
        handler(
            documentWith = {
                withOperation {
                    summary("Anime extension install apk")
                    description("Install the uploaded anime extension apk file")
                }
                uploadedFile("file") {
                    it.description("Anime extension apk")
                    it.required(true)
                }
            },
            behaviorOf = { ctx ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val uploadedFile = ctx.uploadedFile("file")!!
                logger.debug { "Uploaded anime extension file name: " + uploadedFile.filename() }

                ctx.future {
                    future {
                        AnimeExtension.installExternalExtension(
                            uploadedFile.content(),
                            uploadedFile.filename(),
                        )
                    }.thenApply {
                        ctx.status(it)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.CREATED)
                httpCode(HttpStatus.FOUND)
                httpCode(HttpStatus.INTERNAL_SERVER_ERROR)
            },
        )

    /** update extension identified with "pkgName" */
    val update =
        handler(
            pathParam<String>("pkgName"),
            documentWith = {
                withOperation {
                    summary("Anime extension update")
                    description("Update anime extension identified with \"pkgName\"")
                }
            },
            behaviorOf = { ctx, pkgName ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future {
                        AnimeExtension.updateExtension(pkgName)
                    }.thenApply {
                        ctx.status(it)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.CREATED)
                httpCode(HttpStatus.FOUND)
                httpCode(HttpStatus.NOT_FOUND)
                httpCode(HttpStatus.INTERNAL_SERVER_ERROR)
            },
        )

    /** uninstall extension identified with "pkgName" */
    val uninstall =
        handler(
            pathParam<String>("pkgName"),
            documentWith = {
                withOperation {
                    summary("Anime extension uninstall")
                    description("Uninstall anime extension identified with \"pkgName\"")
                }
            },
            behaviorOf = { ctx, pkgName ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                AnimeExtension.uninstallExtension(pkgName)
                ctx.status(200)
            },
            withResults = {
                httpCode(HttpStatus.CREATED)
                httpCode(HttpStatus.FOUND)
                httpCode(HttpStatus.NOT_FOUND)
                httpCode(HttpStatus.INTERNAL_SERVER_ERROR)
            },
        )

    /** icon for extension named `apkName` */
    val icon =
        handler(
            pathParam<String>("apkName"),
            documentWith = {
                withOperation {
                    summary("Anime extension icon")
                    description("Icon for anime extension named `apkName`")
                }
            },
            behaviorOf = { ctx, apkName ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future { AnimeExtension.getExtensionIcon(apkName) }
                        .thenApply {
                            ctx.header("content-type", it.second)
                            val httpCacheSeconds = 365.days.inWholeSeconds
                            ctx.header("cache-control", "max-age=$httpCacheSeconds, immutable")
                            ctx.result(it.first)
                        }
                }
            },
            withResults = {
                image(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )
}
