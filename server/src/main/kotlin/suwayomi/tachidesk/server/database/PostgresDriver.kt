package suwayomi.tachidesk.server.database

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import java.io.IOException
import java.sql.Connection
import java.sql.Driver
import java.util.Properties
import org.postgresql.Driver as JdbcPostgresDriver

class PostgresDriver private constructor(
    private val delegate: JdbcPostgresDriver,
) : Driver by delegate {
    constructor() : this(JdbcPostgresDriver())

    @Volatile
    private var preserveDriverDefaults = hasDriverConfiguration()

    override fun connect(
        url: String?,
        info: Properties?,
    ): Connection? {
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            return delegate.connect(url, info)
        }

        val properties =
            Properties().apply {
                info?.stringPropertyNames()?.forEach { key -> setProperty(key, info.getProperty(key)) }
            }

        if (!preserveDriverDefaults && hasDriverConfiguration()) {
            preserveDriverDefaults = true
        }
        if (!preserveDriverDefaults) {
            // Service files can change between physical connections. Never cache our factory in the caller's properties.
            val parsed = JdbcPostgresDriver.parseURL(url, properties)
            if (parsed != null && !parsed.containsKey("socketFactory")) {
                properties.setProperty("socketFactory", DirectPostgresSocketFactory::class.java.name)
            }
        }

        return delegate.connect(url, properties)
    }

    private fun hasDriverConfiguration(): Boolean =
        try {
            // The driver privately caches these defaults. Preserve them without reading or overriding custom values.
            val loader = JdbcPostgresDriver::class.java.classLoader
            val path = "org/postgresql/driverconfig.properties"
            (loader?.getResources(path) ?: ClassLoader.getSystemResources(path)).hasMoreElements()
        } catch (_: IOException) {
            true
        } catch (_: SecurityException) {
            true
        }
}
