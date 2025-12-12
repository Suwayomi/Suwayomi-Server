package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.HttpStatus
import suwayomi.tachidesk.manga.impl.IReaderSource
import suwayomi.tachidesk.manga.model.dataclass.IReaderSourceDataClass
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.withOperation

object IReaderSourceController {
    val list =
        handler(
            documentWith = {
                withOperation {
                    summary("IReader Source list")
                    description("Get the list of all IReader sources")
                }
            },
            behaviorOf = { ctx ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.json(IReaderSource.getSourceList())
            },
            withResults = {
                json<List<IReaderSourceDataClass>>(HttpStatus.OK)
            },
        )

    val retrieve =
        handler(
            pathParam<Long>("sourceId"),
            documentWith = {
                withOperation {
                    summary("Get IReader source")
                    description("Get a specific IReader source by ID")
                }
            },
            behaviorOf = { ctx, sourceId ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val source = IReaderSource.getSource(sourceId)
                if (source != null) {
                    ctx.json(source)
                } else {
                    ctx.status(HttpStatus.NOT_FOUND)
                }
            },
            withResults = {
                json<IReaderSourceDataClass>(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )
}
