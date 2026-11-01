package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.HttpStatus
import suwayomi.tachidesk.manga.impl.extension.Extension
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

    /** icon for extension named `apkName` */
    val icon =
        handler(
            pathParam<String>("pkgName"),
            documentWith = {
                withOperation {
                    summary("Extension icon")
                    description("Icon for extension named `apkName`")
                }
            },
            behaviorOf = { ctx, pkgName ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future { Extension.getExtensionIcon(pkgName) }
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
