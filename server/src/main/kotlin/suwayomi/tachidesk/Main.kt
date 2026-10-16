package suwayomi.tachidesk

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.graphql.server.GraphQLSchemaProvider
import suwayomi.tachidesk.server.JavalinSetup.javalinSetup
import suwayomi.tachidesk.server.applicationSetup
import kotlin.system.exitProcess

fun main() {
    // Any failure during startup must terminate the process: non-daemon threads
    // (scheduler timers, cron4j, OkHttp pools) would otherwise keep a zombie JVM
    // alive that neither listens on the port nor exits.
    try {
        // Trigger graphql schema generation as early as possible, takes multiple seconds, delaying the javalin startup otherwise
        GraphQLSchemaProvider.init()
        applicationSetup()
        // Block on the result only after ServerConfig is registered; schema generation reads it lazily.
        GraphQLSchemaProvider.awaitInit()
        javalinSetup()
    } catch (e: Throwable) {
        KotlinLogging.logger {}.error(e) { "Startup failed, shutting down" }
        exitProcess(-1)
    }
}
