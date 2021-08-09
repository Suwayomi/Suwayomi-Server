package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.Context
import suwayomi.tachidesk.manga.impl.extension.Extension
import suwayomi.tachidesk.manga.impl.extension.ExtensionsList
import suwayomi.tachidesk.server.JavalinSetup.future

object ExtensionController {
    /** list all extensions */
    fun list(ctx: Context) {
        ctx.json(
            future {
                ExtensionsList.getExtensionList()
            }
        )
    }

    /** install extension identified with "pkgName" */
    fun install(ctx: Context) {
        val pkgName = ctx.pathParam("pkgName")

        ctx.json(
            future {
                Extension.installExtension(pkgName)
            }
        )
    }

    /** update extension identified with "pkgName" */
    fun update(ctx: Context) {
        val pkgName = ctx.pathParam("pkgName")

        ctx.json(
            future {
                Extension.updateExtension(pkgName)
            }
        )
    }

    /** uninstall extension identified with "pkgName" */
    fun uninstall(ctx: Context) {
        val pkgName = ctx.pathParam("pkgName")

        Extension.uninstallExtension(pkgName)
        ctx.status(200)
    }

    /** icon for extension named `apkName` */
    fun icon(ctx: Context) {
        val apkName = ctx.pathParam("apkName")

        ctx.result(
            future { Extension.getExtensionIcon(apkName) }
                .thenApply {
                    ctx.header("content-type", it.second)
                    it.first
                }
        )
    }
}
