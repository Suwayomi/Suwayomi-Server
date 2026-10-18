package suwayomi.tachidesk.server.database

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Isolated
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException
import java.util.Properties
import java.util.concurrent.ConcurrentLinkedQueue
import javax.net.SocketFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Isolated
@Timeout(5)
class PostgresTransportCompatibilityTest {
    @TempDir
    lateinit var directory: Path

    private val factoryName = RecordingPostgresSocketFactory::class.java.name
    private val driver = PostgresDriver()
    private val serviceProperty = "org.postgresql.pgservicefile"
    private val url = "jdbc:postgresql://127.0.0.1:1/postgres?connectTimeout=1&socketTimeout=1&loginTimeout=0&sslmode=disable"

    @BeforeEach
    fun prepareDriver() {
        RecordingPostgresSocketFactory.calls.clear()
        PropertyRecordingPostgresSocketFactory.credentials.clear()
    }

    private fun credentials() =
        Properties().apply {
            setProperty("user", "transport_test")
            setProperty("password", "transport_test")
        }

    private fun attemptConnection(
        suffix: String,
        properties: Properties = credentials(),
    ): SQLException =
        assertFailsWith<SQLException> {
            driver.connect(url + suffix, properties).use { }
        }

    private fun withService(
        factory: String = factoryName,
        argument: String,
        block: (Path) -> Unit,
    ) {
        val path = directory.resolve("pg_service.conf")
        writeService(path, factory, argument)
        val previous = System.getProperty(serviceProperty)
        try {
            System.setProperty(serviceProperty, path.toString())
            block(path)
        } finally {
            if (previous == null) System.clearProperty(serviceProperty) else System.setProperty(serviceProperty, previous)
        }
    }

    private fun writeService(
        path: Path,
        factory: String,
        argument: String,
    ) {
        Files.writeString(path, "[transport_test]\nsocketFactory=$factory\nsocketFactoryArg=$argument\n")
    }

    @Test
    fun `URL factory and argument override connection properties`() {
        val properties =
            credentials().apply {
                setProperty("socketFactory", "unavailable.connection.Factory")
                setProperty("socketFactoryArg", "connection")
            }

        attemptConnection("&socketFactory=$factoryName&socketFactoryArg=url", properties)

        assertEquals(listOf("url"), RecordingPostgresSocketFactory.calls.toList())
    }

    @Test
    fun `connection properties override service factory and argument`() {
        withService(factory = "unavailable.service.Factory", argument = "service") {
            val properties =
                credentials().apply {
                    setProperty("socketFactory", factoryName)
                    setProperty("socketFactoryArg", "connection")
                }

            attemptConnection("&service=transport_test", properties)

            assertEquals(listOf("connection"), RecordingPostgresSocketFactory.calls.toList())
        }
    }

    @Test
    fun `service factory and argument are used when absent from URL and properties`() {
        withService(argument = "service") {
            attemptConnection("&service=transport_test")

            assertEquals(listOf("service"), RecordingPostgresSocketFactory.calls.toList())
        }
    }

    @Test
    fun `service factory argument is read for each new connection`() {
        withService(argument = "first") { path ->
            attemptConnection("&service=transport_test")
            writeService(path, factoryName, "second")
            attemptConnection("&service=transport_test")

            assertEquals(listOf("first", "second"), RecordingPostgresSocketFactory.calls.toList())
        }
    }

    @Test
    fun `explicit empty URL factory keeps its failure instead of using connection factory`() {
        val properties =
            credentials().apply {
                setProperty("socketFactory", factoryName)
                setProperty("socketFactoryArg", "connection")
            }

        val failure = attemptConnection("&socketFactory=", properties)

        assertEquals("08006", failure.sqlState)
        assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is ClassNotFoundException })
        assertEquals(emptyList(), RecordingPostgresSocketFactory.calls.toList())
    }

    @Test
    fun `inherited factory properties are preserved without mutating the caller`() {
        val defaults =
            credentials().apply {
                setProperty("socketFactory", factoryName)
                setProperty("socketFactoryArg", "inherited")
            }
        val properties = Properties(defaults)

        attemptConnection("", properties)

        assertEquals(listOf("inherited"), RecordingPostgresSocketFactory.calls.toList())
        assertTrue(properties.isEmpty)
        assertEquals("inherited", defaults.getProperty("socketFactoryArg"))
    }

    @Test
    fun `URL credentials override properties without changing later connections`() {
        val properties =
            credentials().apply {
                setProperty("socketFactory", PropertyRecordingPostgresSocketFactory::class.java.name)
            }

        attemptConnection("&user=url_user&password=url_password", properties)
        attemptConnection("", properties)

        assertEquals(
            listOf("url_user" to "url_password", "transport_test" to "transport_test"),
            PropertyRecordingPostgresSocketFactory.credentials.toList(),
        )
        assertEquals("transport_test", properties.getProperty("user"))
        assertEquals("transport_test", properties.getProperty("password"))
    }
}

class PropertyRecordingPostgresSocketFactory(
    private val properties: Properties,
) : SocketFactory() {
    companion object {
        val credentials = ConcurrentLinkedQueue<Pair<String?, String?>>()
    }

    private fun stopBeforeNetwork(): Socket {
        credentials.add(properties.getProperty("user") to properties.getProperty("password"))
        throw IOException("Controlled transport failure")
    }

    override fun createSocket(): Socket = stopBeforeNetwork()

    override fun createSocket(
        host: String,
        port: Int,
    ): Socket = stopBeforeNetwork()

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = stopBeforeNetwork()

    override fun createSocket(
        host: InetAddress,
        port: Int,
    ): Socket = stopBeforeNetwork()

    override fun createSocket(
        host: InetAddress,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = stopBeforeNetwork()
}

class RecordingPostgresSocketFactory(
    private val argument: String,
) : SocketFactory() {
    companion object {
        val calls = ConcurrentLinkedQueue<String>()
    }

    private fun stopBeforeNetwork(): Socket {
        calls.add(argument)
        throw IOException("Controlled transport failure")
    }

    override fun createSocket(): Socket = stopBeforeNetwork()

    override fun createSocket(
        host: String,
        port: Int,
    ): Socket = stopBeforeNetwork()

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = stopBeforeNetwork()

    override fun createSocket(
        host: InetAddress,
        port: Int,
    ): Socket = stopBeforeNetwork()

    override fun createSocket(
        host: InetAddress,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = stopBeforeNetwork()
}
