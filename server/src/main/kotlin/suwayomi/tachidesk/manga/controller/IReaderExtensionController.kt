package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.HttpStatus
import suwayomi.tachidesk.manga.impl.extension.ireader.IReaderExtension
import suwayomi.tachidesk.manga.impl.extension.ireader.IReaderExtensionsList
import suwayomi.tachidesk.manga.model.dataclass.IReaderExtensionDataClass
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.withOperation
import kotlin.time.Duration.Companion.days

object IReaderExtensionController {
    private val logger = KotlinLogging.logger {}

    val list =
        handler(
            documentWith = {
                withOperation {
                    summary("IReader Extension list")
                    description("List all IReader extensions")
                }
            },
            behaviorOf = { ctx ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future {
                        IReaderExtensionsList.getExtensionList()
                    }.thenApply {
                        ctx.json(it)
                    }
                }
            },
            withResults = {
                json<Array<IReaderExtensionDataClass>>(HttpStatus.OK)
            },
        )

    val install =
        handler(
            pathParam<String>("pkgName"),
            documentWith = {
                withOperation {
                    summary("IReader Extension install")
                    description("Install IReader extension identified with \"pkgName\"")
                }
            },
            behaviorOf = { ctx, pkgName ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future {
                        IReaderExtension.installExtension(pkgName)
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

    val installFile =
        handler(
            documentWith = {
                withOperation {
                    summary("IReader Extension install file")
                    description("Install the uploaded IReader extension file (JAR preferred, APK also supported)")
                }
                uploadedFile("file") {
                    it.description("IReader Extension JAR or APK file")
                    it.required(true)
                }
            },
            behaviorOf = { ctx ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val uploadedFile = ctx.uploadedFile("file")!!
                logger.debug { "Uploaded IReader extension file name: " + uploadedFile.filename() }

                ctx.future {
                    future {
                        IReaderExtension.installExternalExtension(
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

    val update =
        handler(
            pathParam<String>("pkgName"),
            documentWith = {
                withOperation {
                    summary("IReader Extension update")
                    description("Update IReader extension identified with \"pkgName\"")
                }
            },
            behaviorOf = { ctx, pkgName ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future {
                        IReaderExtension.updateExtension(pkgName)
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

    val uninstall =
        handler(
            pathParam<String>("pkgName"),
            documentWith = {
                withOperation {
                    summary("IReader Extension uninstall")
                    description("Uninstall IReader extension identified with \"pkgName\"")
                }
            },
            behaviorOf = { ctx, pkgName ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                IReaderExtension.uninstallExtension(pkgName)
                ctx.status(200)
            },
            withResults = {
                httpCode(HttpStatus.CREATED)
                httpCode(HttpStatus.FOUND)
                httpCode(HttpStatus.NOT_FOUND)
                httpCode(HttpStatus.INTERNAL_SERVER_ERROR)
            },
        )

    val icon =
        handler(
            pathParam<String>("apkName"),
            documentWith = {
                withOperation {
                    summary("IReader Extension icon")
                    description("Icon for IReader extension named `apkName`")
                }
            },
            behaviorOf = { ctx, apkName ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future { IReaderExtension.getExtensionIcon(apkName) }
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
