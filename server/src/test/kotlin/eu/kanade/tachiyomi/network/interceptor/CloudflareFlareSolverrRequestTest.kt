package eu.kanade.tachiyomi.network.interceptor

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudflareFlareSolverrRequestTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private fun buildRequest(
        originalRequest: Request = Request.Builder().url("https://example.com/manga").build(),
        onlyCookies: Boolean = false,
        timeoutMs: Int = 60000,
        socksEnabled: Boolean = false,
        socksVersion: Int = 5,
        socksHost: String = "",
        socksPort: String = "",
        configuredSessionName: String = "suwayomi",
        configuredSessionTtl: Int = 30,
        cookies: List<CFClearance.FlareSolverCookie> = emptyList(),
    ): CFClearance.FlareSolverRequest =
        CFClearance.buildFlareSolverRequest(
            originalRequest = originalRequest,
            onlyCookies = onlyCookies,
            timeoutMs = timeoutMs,
            socksEnabled = socksEnabled,
            socksVersion = socksVersion,
            socksHost = socksHost,
            socksPort = socksPort,
            configuredSessionName = configuredSessionName,
            configuredSessionTtl = configuredSessionTtl,
            cookies = cookies,
        )

    @Test
    fun `proxy disabled preserves named session and ttl, omits proxy`() {
        val request =
            buildRequest(
                socksEnabled = false,
                configuredSessionName = "suwayomi",
                configuredSessionTtl = 30,
            )

        assertEquals("request.get", request.cmd)
        assertEquals("https://example.com/manga", request.url)
        assertEquals("suwayomi", request.session)
        assertEquals(30, request.sessionTtlMinutes)
        assertNull(request.proxy)

        // Production JSON serialization: session fields present, proxy key omitted
        val serialized = Json.encodeToString(request)
        val jsonElement = json.parseToJsonElement(serialized).jsonObject
        assertEquals("suwayomi", jsonElement["session"]?.jsonPrimitive?.content)
        assertEquals("30", jsonElement["session_ttl_minutes"]?.jsonPrimitive?.content)
        assertTrue("session" in jsonElement)
        assertTrue("session_ttl_minutes" in jsonElement)
        assertTrue("proxy" !in jsonElement)

        // API request does not set X-Proxy-Server header
        val apiRequest =
            CFClearance.buildFlareSolverApiRequest(
                baseUrl = "http://127.0.0.1:8191",
                request = request,
            )
        assertNull(apiRequest.header("X-Proxy-Server"))
    }

    @Test
    fun `proxy enabled omits session and ttl, includes request level proxy and byparr header`() {
        val request =
            buildRequest(
                socksEnabled = true,
                socksVersion = 5,
                socksHost = "proxy.example.com",
                socksPort = "1080",
                configuredSessionName = "suwayomi",
                configuredSessionTtl = 30,
            )

        assertEquals("request.get", request.cmd)
        assertNull(request.session)
        assertNull(request.sessionTtlMinutes)
        assertNotNull(request.proxy)
        assertEquals("socks5://proxy.example.com:1080", request.proxy.url)

        // Production JSON serialization: session keys omitted, proxy key present
        val serialized = Json.encodeToString(request)
        val jsonElement = json.parseToJsonElement(serialized).jsonObject
        assertTrue("session" !in jsonElement)
        assertTrue("session_ttl_minutes" !in jsonElement)
        assertTrue("proxy" in jsonElement)
        val proxyObj = jsonElement["proxy"]?.jsonObject
        assertNotNull(proxyObj)
        assertEquals("socks5://proxy.example.com:1080", proxyObj["url"]?.jsonPrimitive?.content)

        // API request sets X-Proxy-Server header with the exact same proxy URL
        val apiRequest =
            CFClearance.buildFlareSolverApiRequest(
                baseUrl = "http://127.0.0.1:8191",
                request = request,
            )
        assertEquals(request.proxy.url, apiRequest.header("X-Proxy-Server"))
    }

    @Test
    fun `proxy enabled preserves cookies and returnOnlyCookies`() {
        val cookieList = listOf(CFClearance.FlareSolverCookie("test_name", "test_value"))
        val request =
            buildRequest(
                onlyCookies = true,
                cookies = cookieList,
                socksEnabled = true,
                socksVersion = 5,
                socksHost = "127.0.0.1",
                socksPort = "10808",
            )

        assertTrue(request.returnOnlyCookies == true)
        assertEquals(cookieList, request.cookies)
        assertNull(request.session)
        assertEquals("socks5://127.0.0.1:10808", request.proxy?.url)
    }

    @Test
    fun `proxy enabled preserves post request body`() {
        val bodyContent = """{"action":"query","page":1}"""
        val originalRequest =
            Request
                .Builder()
                .url("https://example.com/api")
                .post(bodyContent.toRequestBody("application/json".toMediaType()))
                .build()

        val request =
            buildRequest(
                originalRequest = originalRequest,
                socksEnabled = true,
                socksVersion = 5,
                socksHost = "127.0.0.1",
                socksPort = "10808",
            )

        assertEquals("request.post", request.cmd)
        assertEquals(bodyContent, request.postData)
        assertNull(request.session)
        assertEquals("socks5://127.0.0.1:10808", request.proxy?.url)
    }

    @Test
    fun `socks proxy transition off to on to off updates FlareSolverr request structure correctly`() {
        val originalRequest = Request.Builder().url("https://example.com/chapter").build()

        // 1. Initial SOCKS OFF: session + TTL present, proxy absent
        val offRequest =
            buildRequest(
                originalRequest = originalRequest,
                socksEnabled = false,
                configuredSessionName = "suwayomi",
                configuredSessionTtl = 30,
            )
        assertEquals("suwayomi", offRequest.session)
        assertEquals(30, offRequest.sessionTtlMinutes)
        assertNull(offRequest.proxy)

        val offSerialized = json.parseToJsonElement(Json.encodeToString(offRequest)).jsonObject
        assertTrue("session" in offSerialized)
        assertTrue("session_ttl_minutes" in offSerialized)
        assertTrue("proxy" !in offSerialized)

        // 2. SOCKS ON: proxy present, session + TTL absent
        val onRequest =
            buildRequest(
                originalRequest = originalRequest,
                socksEnabled = true,
                socksVersion = 5,
                socksHost = "127.0.0.1",
                socksPort = "10808",
                configuredSessionName = "suwayomi",
                configuredSessionTtl = 30,
            )
        assertNull(onRequest.session)
        assertNull(onRequest.sessionTtlMinutes)
        assertNotNull(onRequest.proxy)
        assertEquals("socks5://127.0.0.1:10808", onRequest.proxy.url)

        val onSerialized = json.parseToJsonElement(Json.encodeToString(onRequest)).jsonObject
        assertTrue("session" !in onSerialized)
        assertTrue("session_ttl_minutes" !in onSerialized)
        assertTrue("proxy" in onSerialized)

        // 3. SOCKS OFF again: session + TTL restored, proxy absent
        val offAgainRequest =
            buildRequest(
                originalRequest = originalRequest,
                socksEnabled = false,
                configuredSessionName = "suwayomi",
                configuredSessionTtl = 30,
            )
        assertEquals("suwayomi", offAgainRequest.session)
        assertEquals(30, offAgainRequest.sessionTtlMinutes)
        assertNull(offAgainRequest.proxy)

        val offAgainSerialized = json.parseToJsonElement(Json.encodeToString(offAgainRequest)).jsonObject
        assertTrue("session" in offAgainSerialized)
        assertTrue("session_ttl_minutes" in offAgainSerialized)
        assertTrue("proxy" !in offAgainSerialized)
    }

    @Test
    fun `buildFlareSolverApiRequest normalizes url and sets X-Proxy-Server matching body proxy url`() {
        val request =
            buildRequest(
                socksEnabled = true,
                socksVersion = 5,
                socksHost = "127.0.0.1",
                socksPort = "10808",
            )

        val reqWithTrailingSlash =
            CFClearance.buildFlareSolverApiRequest(
                baseUrl = "http://server-a:8191/",
                request = request,
            )
        assertEquals("http://server-a:8191/v1", reqWithTrailingSlash.url.toString())
        assertEquals(request.proxy?.url, reqWithTrailingSlash.header("X-Proxy-Server"))

        val reqWithoutTrailingSlash =
            CFClearance.buildFlareSolverApiRequest(
                baseUrl = "http://server-b:9000",
                request = request,
            )
        assertEquals("http://server-b:9000/v1", reqWithoutTrailingSlash.url.toString())
        assertEquals(request.proxy?.url, reqWithoutTrailingSlash.header("X-Proxy-Server"))

        // Verify body content has valid JSON
        val buffer = Buffer()
        reqWithTrailingSlash.body?.writeTo(buffer)
        val bodyString = buffer.readUtf8()
        val parsedJson = json.parseToJsonElement(bodyString).jsonObject
        assertEquals("request.get", parsedJson["cmd"]?.jsonPrimitive?.content)
        assertNotNull(parsedJson["proxy"]?.jsonObject)
    }

    @Test
    fun `bypass api request never emits proxy credentials headers`() {
        val request =
            buildRequest(
                socksEnabled = true,
                socksVersion = 5,
                socksHost = "127.0.0.1",
                socksPort = "10808",
            )

        val apiRequest =
            CFClearance.buildFlareSolverApiRequest(
                baseUrl = "http://127.0.0.1:8191",
                request = request,
            )

        assertEquals("socks5://127.0.0.1:10808", apiRequest.header("X-Proxy-Server"))
        assertNull(apiRequest.header("X-Proxy-Username"))
        assertNull(apiRequest.header("X-Proxy-Password"))
    }
}
