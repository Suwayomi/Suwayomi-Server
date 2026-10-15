package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request.Builder
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.Browser.openInBrowser
import suwayomi.tachidesk.server.util.ExitCode.MutexCheckFailedAnotherAppRunning
import suwayomi.tachidesk.server.util.ExitCode.MutexCheckFailedTachideskRunning
import xyz.nulldev.ts.config.ApplicationRootDir
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

object AppMutex {
    private val logger = KotlinLogging.logger {}

    private enum class AppMutexState(
        val stat: Int,
    ) {
        Clear(0),
        TachideskInstanceRunning(1),
        OtherApplicationRunning(2),
    }

    private val appIP = if (serverConfig.ip.value == "0.0.0.0") "127.0.0.1" else serverConfig.ip.value

    /**
     * Real inter-process mutex on the data directory. Kept referenced for the whole
     * process lifetime; the OS releases it automatically on process exit.
     */
    private var dataDirLock: FileLock? = null

    /**
     * Tries to take an exclusive lock on `<dataRoot>/.suwayomi-server.lock`.
     *
     * Returns false only when another process holds the lock — a genuine second
     * instance. This is checked *before* any side-effecting startup step so two
     * instances racing through startup cannot both run the data-dir migrations.
     */
    private fun tryLockDataDir(): Boolean {
        // this process already owns the lock (e.g. a repeated applicationSetup during tests)
        if (dataDirLock != null) {
            return true
        }

        return try {
            val lockFile = File(ApplicationRootDir, ".suwayomi-server.lock")
            lockFile.parentFile?.mkdirs()
            val channel =
                FileChannel.open(
                    lockFile.toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                )
            val lock =
                try {
                    channel.tryLock()
                } catch (e: OverlappingFileLockException) {
                    // already held by this JVM — treat as a live lock
                    null
                }
            if (lock == null) {
                runCatching { channel.close() }
                false
            } else {
                dataDirLock = lock
                true
            }
        } catch (e: IOException) {
            // e.g. read-only data dir — degrade to the HTTP probe only
            logger.warn(e) { "Could not take the data-directory lock; mutex falls back to port probing" }
            true
        }
    }

    /**
     * Probes the unauthenticated `/api/v1/ping` endpoint. Only a response of `pong`
     * identifies a running Suwayomi-Server — the previous implementation parsed the
     * `/api/v1/settings/about/` response, which breaks whenever auth is enabled (401
     * was misclassified as "other application") and can false-positive on any app
     * answering with about-shaped JSON.
     */
    private fun probeInstance(): AppMutexState {
        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(200, TimeUnit.MILLISECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .build()

        val pingPath = ServerSubpath.maybeAddAsPrefix("/api/v1/ping")
        val request =
            Builder()
                .url("http://$appIP:${serverConfig.port.value}$pingPath")
                .build()

        val body =
            try {
                client
                    .newCall(request)
                    .execute()
                    .use { if (it.isSuccessful) it.body.string().trim() else "" }
            } catch (e: IOException) {
                return AppMutexState.Clear
            }

        return if (body == "pong") {
            AppMutexState.TachideskInstanceRunning
        } else {
            AppMutexState.OtherApplicationRunning
        }
    }

    /**
     * Test hook: the unit-test JVM never opens the configured port, so probing it can only
     * yield false positives — any other listener (e.g. a dev server on :4567) would be
     * classified as a running instance and [shutdownApp] -> exitProcess would kill the whole
     * Gradle test worker. The shared test bootstrap sets this once, making the check a
     * constant Clear; production startup never touches it.
     */
    @Volatile
    var bypassForTests = false

    private fun checkAppMutex(): AppMutexState {
        if (bypassForTests) {
            return AppMutexState.Clear
        }
        if (!tryLockDataDir()) {
            // the lock is held by another instance even if its HTTP layer is not up yet
            // (covers the startup race where both instances would otherwise see "Clear")
            return AppMutexState.TachideskInstanceRunning
        }
        return probeInstance()
    }

    fun handleAppMutex() {
        when (checkAppMutex()) {
            AppMutexState.Clear -> {
                logger.info { "Mutex status is clear, Resuming startup." }
            }

            AppMutexState.TachideskInstanceRunning -> {
                logger.info { "Another instance of Suwayomi-Server is running on $appIP:${serverConfig.port.value}" }

                logger.info { "Probably user thought Suwayomi-Server is closed so, opening webUI in browser again." }
                openInBrowser()

                logger.info { "Aborting startup." }

                shutdownApp(MutexCheckFailedTachideskRunning)
            }

            AppMutexState.OtherApplicationRunning -> {
                logger.error { "A non Suwayomi-Server application is running on $appIP:${serverConfig.port.value}, aborting startup." }
                shutdownApp(MutexCheckFailedAnotherAppRunning)
            }
        }
    }
}
