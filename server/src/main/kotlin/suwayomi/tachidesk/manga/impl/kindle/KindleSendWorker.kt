package suwayomi.tachidesk.manga.impl.kindle

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import suwayomi.tachidesk.server.serverConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Long-running coroutine that drains the Kindle send queue. Throttled
 * to one send per `serverConfig.kindleSendIntervalSeconds` to stay
 * within Amazon's Send-to-Kindle rate limits and the SMTP provider's
 * per-account caps.
 */
object KindleSendWorker {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    fun start() {
        if (!running.compareAndSet(false, true)) return
        logger.info { "Starting Kindle send worker" }
        scope.launch {
            while (isActive) {
                try {
                    val id = KindleSendService.reserveNext()
                    if (id != null) {
                        KindleSendService.process(id)
                        // Throttle gap before fetching the next item.
                        delay(serverConfig.kindleSendIntervalSeconds.value * 1000L)
                    } else {
                        // Idle poll — short sleep so manual enqueues are picked up quickly.
                        delay(5_000L)
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Kindle worker iteration error" }
                    delay(10_000L)
                }
            }
        }
    }
}
