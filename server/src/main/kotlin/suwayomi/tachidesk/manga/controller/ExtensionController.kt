package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.Context
import mu.KotlinLogging
import suwayomi.tachidesk.manga.impl.extension.Extension
import suwayomi.tachidesk.manga.impl.extension.ExtensionsList
import suwayomi.tachidesk.server.JavalinSetup.future

object ExtensionController {
    private val logger = KotlinLogging.logger {}

    /** list all extensions */
    fun list(ctx: Context) {
        ctx.future(
            future {
                ExtensionsList.getExtensionList()
            }
        )
    }

    /** install extension identified with "pkgName" */
    fun install(ctx: Context) {
        val pkgName = ctx.pathParam("pkgName")

        ctx.future(
            future {
                Extension.installExtension(pkgName)
            }
        )
    }

    /** install the uploaded apk file */
    fun installFile(ctx: Context) {

        val uploadedFile = ctx.uploadedFile("file")!!
        logger.debug { "Uploaded extension file name: " + uploadedFile.filename }

        ctx.future(
            future {
                Extension.installExternalExtension(uploadedFile.content, uploadedFile.filename)
            }
        )
    }

    /** update extension identified with "pkgName" */
    fun update(ctx: Context) {
        val pkgName = ctx.pathParam("pkgName")

        ctx.future(
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

        ctx.future(
            future { Extension.getExtensionIcon(apkName) }
                .thenApply {
                    ctx.header("content-type", it.second)
                    it.first
                }
        )
    }
}
