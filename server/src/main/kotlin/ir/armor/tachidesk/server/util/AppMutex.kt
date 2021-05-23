package ir.armor.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.plugin.json.JavalinJackson
import ir.armor.tachidesk.server.impl_internal.AboutDataClass
import ir.armor.tachidesk.server.serverConfig
import ir.armor.tachidesk.server.util.AppMutex.AppMutexStat.Clear
import ir.armor.tachidesk.server.util.AppMutex.AppMutexStat.OtherApplicationRunning
import ir.armor.tachidesk.server.util.AppMutex.AppMutexStat.TachideskInstanceRunning
import ir.armor.tachidesk.server.util.Browser.openInBrowser
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request.Builder
import java.io.IOException
import java.util.concurrent.TimeUnit

object AppMutex {
    private val logger = KotlinLogging.logger {}

    private enum class AppMutexStat(val stat: Int) {
        Clear(0),
        TachideskInstanceRunning(1),
        OtherApplicationRunning(2)
    }

    private val appIP = if (serverConfig.ip == "0.0.0.0") "127.0.0.1" else serverConfig.ip

    private fun checkAppMutex(): AppMutexStat {
        val client = OkHttpClient.Builder()
            .connectTimeout(200, TimeUnit.MILLISECONDS)
            .build()

        val request = Builder()
            .url("http://$appIP:${serverConfig.port}/api/v1/about/")
            .build()

        val response = try {
            client.newCall(request).execute().use { response -> response.body!!.string() }
        } catch (e: IOException) {
            return AppMutexStat.Clear
        }

        return try {
            JavalinJackson.fromJson(response, AboutDataClass::class.java)
            AppMutexStat.TachideskInstanceRunning
        } catch (e: IOException) {
            AppMutexStat.OtherApplicationRunning
        }
    }

    fun handleAppMutex() {
        when (checkAppMutex()) {
            Clear -> {
                logger.info("Mutex status is clear, Resuming startup.")
            }
            TachideskInstanceRunning -> {
                logger.info("Another instance of Tachidesk is running on $appIP:${serverConfig.port}")

                logger.info("Probably user thought tachidesk is closed so, opening webUI in browser again.")
                openInBrowser()

                logger.info("Aborting startup.")

                shutdownApp(ExitCode.MutexCheckFailedTachideskRunning)
            }
            OtherApplicationRunning -> {
                logger.error("A non Tachidesk application is running on $appIP:${serverConfig.port}, aborting startup.")
                shutdownApp(ExitCode.MutexCheckFailedAnotherAppRunning)
            }
        }
    }
}
