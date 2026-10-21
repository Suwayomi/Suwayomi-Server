package suwayomi.tachidesk.server.database

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Isolated
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Driver
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Collections
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.postgresql.Driver as JdbcPostgresDriver

@Isolated
@Timeout(10)
class PostgresDriverTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `default connections bypass proxy selection unlike the original driver`() {
        val original: Driver = JdbcPostgresDriver()
        val driver = PostgresDriver()
        val properties = credentials()
        val expectedProperties = Properties().apply { putAll(properties) }

        withRejectingProxySelector { calls ->
            PostgresErrorServer(expectedConnections = 1).use { server ->
                assertFailsWith<AssertionError> {
                    original.connect(server.url, properties).use { }
                }
                assertEquals(1, calls.get())
                assertEquals(0, server.acceptedConnections.get())

                assertProtocolFailure(driver, server.url, properties)

                assertEquals(1, server.acceptedConnections.get())
                assertEquals(1, calls.get())
                assertEquals(expectedProperties, properties)
                assertFalse(properties.containsKey("socketFactory"))
            }
        }
    }

    @Test
    fun `service factory changes are honored without contaminating later connections`() {
        val serviceProperty = "org.postgresql.pgservicefile"
        val previousService = System.getProperty(serviceProperty)
        val serviceFile = directory.resolve("pg_service.conf")
        val properties = credentials()
        val expectedProperties = Properties().apply { putAll(properties) }
        val driver = PostgresDriver()
        RecordingPostgresSocketFactory.calls.clear()

        try {
            System.setProperty(serviceProperty, serviceFile.toString())
            withRejectingProxySelector { calls ->
                PostgresErrorServer(expectedConnections = 2).use { server ->
                    val url = "${server.url}&service=postgres_driver_test"
                    Files.writeString(serviceFile, "[postgres_driver_test]\n")

                    assertProtocolFailure(driver, url, properties)
                    assertEquals(1, server.acceptedConnections.get())
                    assertEquals(expectedProperties, properties)

                    Files.writeString(
                        serviceFile,
                        "[postgres_driver_test]\nsocketFactory=${RecordingPostgresSocketFactory::class.java.name}\n" +
                            "socketFactoryArg=service\n",
                    )
                    val failure =
                        assertFailsWith<SQLException> {
                            driver.connect(url, properties).use { }
                        }
                    assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is IOException })
                    assertEquals(listOf("service"), RecordingPostgresSocketFactory.calls.toList())
                    assertEquals(1, server.acceptedConnections.get())
                    assertEquals(expectedProperties, properties)

                    Files.writeString(serviceFile, "[postgres_driver_test]\n")
                    assertProtocolFailure(driver, url, properties)

                    assertEquals(2, server.acceptedConnections.get())
                    assertEquals(listOf("service"), RecordingPostgresSocketFactory.calls.toList())
                    assertEquals(0, calls.get())
                    assertEquals(expectedProperties, properties)
                    assertFalse(properties.containsKey("socketFactory"))
                }
            }
        } finally {
            if (previousService == null) {
                System.clearProperty(serviceProperty)
            } else {
                System.setProperty(serviceProperty, previousService)
            }
            RecordingPostgresSocketFactory.calls.clear()
        }
    }

    @Test
    fun `driver metadata is delegated without registering a global wrapper`() {
        val original: Driver = JdbcPostgresDriver()
        val registered = Collections.list(DriverManager.getDrivers())
        val driver = PostgresDriver()
        val unsupportedUrl = "jdbc:unsupported:postgres_driver_test"

        assertEquals(original.majorVersion, driver.majorVersion)
        assertEquals(original.minorVersion, driver.minorVersion)
        assertEquals(original.jdbcCompliant(), driver.jdbcCompliant())
        assertEquals(original.parentLogger, driver.parentLogger)
        assertFalse(driver.acceptsURL(unsupportedUrl))
        assertNull(driver.connect(unsupportedUrl, credentials()))
        assertEquals(registered, Collections.list(DriverManager.getDrivers()))
        assertTrue(registered.none { it is PostgresDriver })
    }

    private fun credentials() =
        Properties().apply {
            setProperty("user", "postgres_driver_test")
            setProperty("password", "postgres_driver_test")
        }

    private fun assertProtocolFailure(
        driver: PostgresDriver,
        url: String,
        properties: Properties,
    ) {
        val failure =
            assertFailsWith<SQLException> {
                driver.connect(url, properties).use { }
            }
        assertEquals("08006", failure.sqlState)
        assertTrue(failure.message.orEmpty().contains(PostgresErrorServer.ERROR_MESSAGE))
    }

    private fun withRejectingProxySelector(block: (AtomicInteger) -> Unit) {
        val previous = ProxySelector.getDefault()
        val calls = AtomicInteger()
        val selector =
            object : ProxySelector() {
                override fun select(uri: URI): MutableList<Proxy> {
                    calls.incrementAndGet()
                    throw AssertionError("Unexpected proxy selection for $uri")
                }

                override fun connectFailed(
                    uri: URI,
                    address: SocketAddress,
                    error: IOException,
                ): Unit = throw AssertionError("Unexpected proxy failure for $uri at $address", error)
            }
        try {
            ProxySelector.setDefault(selector)
            block(calls)
        } finally {
            ProxySelector.setDefault(previous)
        }
    }
}

private class PostgresErrorServer(
    expectedConnections: Int,
) : Closeable {
    companion object {
        const val ERROR_MESSAGE = "Controlled PostgreSQL transport test failure"
    }

    val acceptedConnections = AtomicInteger()
    private val closing = AtomicBoolean()
    private val activeSocket = AtomicReference<Socket?>()
    private val listener =
        ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0))
            soTimeout = 5000
        }
    private val executor = Executors.newSingleThreadExecutor()
    private val task =
        executor.submit<Unit> {
            try {
                repeat(expectedConnections) {
                    listener.accept().use { socket ->
                        activeSocket.set(socket)
                        if (closing.get()) return@submit
                        socket.soTimeout = 2000
                        acceptedConnections.incrementAndGet()
                        readStartup(socket)
                        val error = "SFATAL\u0000VFATAL\u0000C08006\u0000M$ERROR_MESSAGE\u0000\u0000".toByteArray(Charsets.UTF_8)
                        DataOutputStream(socket.getOutputStream()).apply {
                            writeByte('E'.code)
                            writeInt(error.size + 4)
                            write(error)
                            flush()
                        }
                        activeSocket.compareAndSet(socket, null)
                    }
                }
            } catch (error: IOException) {
                if (!closing.get()) throw error
            }
        }

    val url =
        "jdbc:postgresql://127.0.0.1:${listener.localPort}/postgres?" +
            "connectTimeout=1&socketTimeout=1&loginTimeout=0&sslmode=disable&gssEncMode=disable"

    private fun readStartup(socket: Socket) {
        val input = DataInputStream(socket.getInputStream())
        val length = input.readInt()
        assertTrue(length in 8..8192, "Invalid PostgreSQL StartupMessage length: $length")
        assertEquals(196608, input.readInt(), "Expected PostgreSQL protocol version 3")
        val payload = ByteArray(length - 8)
        input.readFully(payload)
        val parameters = mutableMapOf<String, String>()
        ByteArrayInputStream(payload).use { fields ->
            while (true) {
                val key = readField(fields)
                if (key.isEmpty()) {
                    assertEquals(0, fields.available())
                    break
                }
                parameters[key] = readField(fields)
            }
        }
        assertEquals("postgres_driver_test", parameters["user"])
        assertEquals("postgres", parameters["database"])
    }

    private fun readField(input: ByteArrayInputStream): String =
        buildString {
            while (true) {
                val value = input.read()
                assertTrue(value >= 0, "Unterminated PostgreSQL startup parameter")
                if (value == 0) break
                append(value.toChar())
            }
        }

    override fun close() {
        closing.set(true)
        try {
            listener.close()
        } finally {
            try {
                activeSocket.getAndSet(null)?.close()
            } finally {
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS), "PostgreSQL fixture did not terminate")
                task.get(3, TimeUnit.SECONDS)
            }
        }
    }
}
