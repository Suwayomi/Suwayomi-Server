package suwayomi.tachidesk.manga.impl.extension.lnreader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.io.InputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipException

/**
 * The only network path available to an LNReader guest. It derives a bounded
 * request client from [NetworkHelper]'s client, retaining its cookies, proxy,
 * user agent, Cloudflare behavior, TLS and interceptors.
 */
class LnNetworkGateway(
    client: OkHttpClient,
    private val runtime: LnPluginRuntime,
    private val policy: LnNetworkPolicy = LnNetworkPolicy(),
    private val resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    private val json: Json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        },
) {
    constructor(
        network: NetworkHelper,
        runtime: LnPluginRuntime,
        policy: LnNetworkPolicy = LnNetworkPolicy(),
    ) : this(network.client, runtime, policy)

    private val client =
        client
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    fun invoke(
        operation: String,
        requestJson: String,
    ): String {
        require(operation == "fetch") { "Unsupported LNReader network operation '$operation'" }
        require(requestJson.length <= MAX_REQUEST_JSON_BYTES) { "LNReader network request is too large" }
        val request = json.decodeFromString<FetchRequestDto>(requestJson)
        return json.encodeToString(fetch(request))
    }

    private fun fetch(req: FetchRequestDto): FetchResponse {
        var current = buildRequest(req)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val call = resolvedClient(current.url).newCall(current)
            check(runtime.registerActiveCall(call)) { "LNReader runtime closed before network call" }
            val response =
                try {
                    call.execute()
                } finally {
                    runtime.unregisterActiveCall(call)
                }
            if (!response.isRedirect || redirectCount == MAX_REDIRECTS) {
                return response.use(::responseData)
            }
            val location = response.header("Location") ?: return response.use(::responseData)
            val target = current.url.resolve(location) ?: throw IllegalArgumentException("Invalid LNReader redirect target")
            response.close()
            current = redirect(current, response.code, target)
        }
        error("Unreachable LNReader redirect state")
    }

    private fun buildRequest(req: FetchRequestDto): Request {
        require(req.url.length <= MAX_URL_LENGTH && req.url.none(Char::isISOControl)) { "Invalid LNReader url" }
        val method = req.method.uppercase().also { require(it.matches(METHOD)) { "Invalid LNReader HTTP method" } }
        val headers = buildHeaders(req.headers)
        val body = req.body?.toRequestBody()
        require(body == null || method !in NO_BODY_METHODS) { "LNReader $method request cannot have a body" }
        val effectiveBody = body ?: if (method in EMPTY_BODY_METHODS) ByteArray(0).toRequestBody() else null
        return Request
            .Builder()
            .url(req.url)
            .headers(headers)
            .method(method, effectiveBody)
            .build()
    }

    private fun buildHeaders(input: Map<String, String>): Headers {
        require(input.size <= MAX_HEADERS) { "LNReader has too many request headers" }
        return Headers
            .Builder()
            .apply {
                set("Connection", "keep-alive")
                set("Accept", "*/*")
                set("Accept-Language", "*")
                set("Sec-Fetch-Mode", "cors")
                set("Accept-Encoding", "gzip, deflate")
                set("Cache-Control", "max-age=0")
                input.forEach { (name, value) ->
                    require(name.matches(HEADER_NAME) && value.length <= MAX_HEADER_VALUE && value.none(Char::isISOControl)) {
                        "Invalid LNReader request header"
                    }
                    if (name.lowercase() !in FORBIDDEN_HEADERS) set(name, value)
                }
            }.build()
    }

    private fun RequestBodyDto.toRequestBody(): RequestBody =
        when (type) {
            "text" -> {
                val content = requireNotNull(value) { "LNReader value is required" }
                require(content.length <= MAX_BODY_BYTES) { "Invalid LNReader value" }
                content.toRequestBody(contentType?.toMediaType())
            }

            "base64" -> {
                val b64 = requireNotNull(value) { "LNReader value is required" }
                require(b64.length <= MAX_BASE64_BODY_BYTES) { "Invalid LNReader value" }
                val bytes = Base64.getDecoder().decode(b64.replace("\r", "").replace("\n", ""))
                require(bytes.size <= MAX_BODY_BYTES) { "LNReader request body is too large" }
                bytes.toRequestBody(contentType?.toMediaType())
            }

            "form" -> {
                val formParts = requireNotNull(parts) { "LNReader form parts are required" }
                require(formParts.size <= MAX_FORM_PARTS) { "LNReader form has too many parts" }
                MultipartBody
                    .Builder()
                    .setType(MultipartBody.FORM)
                    .apply {
                        formParts.forEach { part ->
                            require(part.name.length <= MAX_FORM_NAME && part.name.none(Char::isISOControl)) { "Invalid LNReader name" }
                            require(part.value.length <= MAX_BODY_BYTES) { "Invalid LNReader value" }
                            addFormDataPart(part.name, part.value)
                        }
                    }.build()
                    .also { require(it.contentLength() <= MAX_BODY_BYTES) { "LNReader form is too large" } }
            }

            else -> {
                throw UnsupportedOperationException("Unsupported LNReader request body")
            }
        }

    private fun redirect(
        request: Request,
        status: Int,
        target: HttpUrl,
    ): Request =
        request
            .newBuilder()
            .url(target)
            .apply {
                if (request.url.scheme != target.scheme || request.url.host != target.host || request.url.port != target.port) {
                    removeHeader("Authorization").removeHeader("Cookie").removeHeader("Referer")
                }
                if (status == 303 || (status in setOf(301, 302) && request.method !in setOf("GET", "HEAD"))) {
                    method("GET", null).removeHeader("Content-Length").removeHeader("Content-Type")
                }
            }.build()

    private fun responseData(response: Response): FetchResponse {
        val body = response.body
        val promisesBody = response.request.method != "HEAD" && response.code !in 100..199 && response.code != 204 && response.code != 304
        val encodings = response.headers.values("Content-Encoding")
        require(encodings.size <= 1 && (encodings.isEmpty() || !encodings[0].contains(','))) {
            "Unsupported stacked LNReader response content encoding"
        }
        val encoding = encodings.firstOrNull()?.trim()?.lowercase()
        val wasDecompressed = promisesBody && encoding in DECOMPRESSED_ENCODINGS

        val bytes =
            if (promisesBody) {
                require(body.contentLength() == -1L || body.contentLength() <= MAX_RESPONSE_BYTES) { "LNReader response is too large" }
                body.readBounded(encoding)
            } else {
                body.close()
                ByteArray(0)
            }

        val forbidden = if (wasDecompressed) FORBIDDEN_DECOMPRESSED_HEADERS else FORBIDDEN_RESPONSE_HEADERS
        val headers =
            response.headers
                .names()
                .filterNot { it.lowercase() in forbidden }
                .associate { it.lowercase() to response.headers.values(it).joinToString(", ") }

        return FetchResponse(
            status = response.code,
            statusText = response.message,
            url = response.request.url.toString(),
            headers = headers,
            bodyBase64 = Base64.getEncoder().encodeToString(bytes),
        )
    }

    private fun ResponseBody.readBounded(contentEncoding: String?): ByteArray =
        when (contentEncoding?.lowercase()) {
            "gzip" -> GZIPInputStream(byteStream()).readBounded()
            "deflate" -> decompressDeflate(byteStream().readBounded())
            null, "identity" -> byteStream().readBounded()
            else -> throw UnsupportedOperationException("Unsupported LNReader response content encoding: $contentEncoding")
        }

    private fun decompressDeflate(compressed: ByteArray): ByteArray {
        if (compressed.isEmpty()) return compressed
        return try {
            InflaterInputStream(compressed.inputStream(), Inflater(false)).readBounded()
        } catch (_: ZipException) {
            InflaterInputStream(compressed.inputStream(), Inflater(true)).readBounded()
        }
    }

    private fun InputStream.readBounded(): ByteArray =
        use { input ->
            val sink = Buffer()
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buf)
                if (count < 0) break
                if (count == 0) continue
                total += count
                require(total <= MAX_RESPONSE_BYTES) { "LNReader response is too large" }
                sink.write(buf, 0, count)
            }
            sink.readByteArray()
        }

    private fun resolvedClient(url: HttpUrl): OkHttpClient = createPinnedClient(client, url, policy, resolve)

    @Serializable
    private data class FetchRequestDto(
        val url: String,
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val body: RequestBodyDto? = null,
    )

    @Serializable
    private data class RequestBodyDto(
        val type: String,
        val value: String? = null,
        val contentType: String? = null,
        val parts: List<FormPartDto>? = null,
    )

    @Serializable
    private data class FormPartDto(
        val name: String,
        val value: String,
    )

    @Serializable
    data class FetchResponse(
        val status: Int,
        val statusText: String,
        val url: String,
        val headers: Map<String, String>,
        val bodyBase64: String,
    )

    companion object {
        /** Image URLs returned by plugins must use the same pinned-address policy as guest fetches. */
        suspend fun fetchImage(
            client: OkHttpClient,
            request: Request,
            policy: LnNetworkPolicy,
        ): Response {
            var current = request
            val boundedClient =
                client
                    .newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build()
            repeat(MAX_REDIRECTS + 1) { redirects ->
                val response = createPinnedClient(boundedClient, current.url, policy).newCall(current).await()
                if (response.isRedirect && redirects < MAX_REDIRECTS) {
                    val target = response.header("Location")?.let(current.url::resolve)
                    if (target != null) {
                        response.close()
                        current =
                            current
                                .newBuilder()
                                .url(target)
                                .apply {
                                    if (current.url.scheme != target.scheme ||
                                        current.url.host != target.host ||
                                        current.url.port != target.port
                                    ) {
                                        removeHeader("Authorization").removeHeader("Cookie").removeHeader("Referer")
                                    }
                                }.build()
                        return@repeat
                    }
                }
                if (!response.isSuccessful) {
                    response.close()
                    throw HttpException(response.code)
                }
                val body = response.body
                if (body.contentLength() > MAX_IMAGE_BYTES) {
                    response.close()
                    throw IOException("LNReader image exceeds size limit")
                }
                val boundedBody =
                    object : ResponseBody() {
                        private val limitedSource: BufferedSource =
                            object : ForwardingSource(body.source()) {
                                private var bytesRead = 0L

                                override fun read(
                                    sink: Buffer,
                                    byteCount: Long,
                                ): Long {
                                    val count = super.read(sink, minOf(byteCount, MAX_IMAGE_BYTES + 1 - bytesRead))
                                    if (count > 0) bytesRead += count
                                    if (bytesRead > MAX_IMAGE_BYTES) throw IOException("LNReader image exceeds size limit")
                                    return count
                                }
                            }.buffer()

                        override fun contentType() = body.contentType()

                        override fun contentLength() = body.contentLength()

                        override fun source() = limitedSource
                    }
                return response.newBuilder().body(boundedBody).build()
            }
            error("Unreachable LNReader image redirect state")
        }

        internal fun createPinnedClient(
            client: OkHttpClient,
            url: HttpUrl,
            policy: LnNetworkPolicy,
            resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
        ): OkHttpClient {
            require(url.host.isNotBlank()) { "LNReader request must have a host" }
            val addresses =
                resolve(url.host).also { addrs ->
                    require(addrs.isNotEmpty() && addrs.all { isPermittedAddress(it, policy.allows(url)) }) {
                        "LNReader destination is not permitted"
                    }
                }
            return client
                .newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .dns(
                    Dns { hostname ->
                        require(hostname.equals(url.host, true)) { "Unexpected LNReader DNS lookup" }
                        addresses
                    },
                ).build()
        }

        val METHOD = Regex("[A-Z]{1,16}")
        val HEADER_NAME = Regex("[!#\$%&'*+.^_`|~0-9A-Za-z-]{1,128}")
        val FORBIDDEN_HEADERS = setOf("host", "content-length", "transfer-encoding", "connection")
        val FORBIDDEN_RESPONSE_HEADERS = setOf("set-cookie", "set-cookie2", "transfer-encoding")
        val FORBIDDEN_DECOMPRESSED_HEADERS = FORBIDDEN_RESPONSE_HEADERS + setOf("content-encoding", "content-length")
        val NO_BODY_METHODS = setOf("GET", "HEAD")
        val EMPTY_BODY_METHODS = setOf("POST", "PUT", "PATCH")
        val DECOMPRESSED_ENCODINGS = setOf("gzip", "deflate")
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val REQUEST_TIMEOUT_SECONDS = 60L
        const val MAX_REDIRECTS = 5
        const val MAX_REQUEST_JSON_BYTES = 512 * 1024
        const val MAX_URL_LENGTH = 4096
        const val MAX_HEADERS = 64
        const val MAX_HEADER_VALUE = 4096
        const val MAX_BODY_BYTES = 512 * 1024
        const val MAX_BASE64_BODY_BYTES = MAX_BODY_BYTES * 2
        const val MAX_FORM_PARTS = 128
        const val MAX_FORM_NAME = 256
        const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 10L * 1024 * 1024

        fun isPermittedAddress(
            address: InetAddress,
            allowedLocalOrigin: Boolean = false,
        ): Boolean {
            if (address.isAnyLocalAddress || address.isMulticastAddress) return false
            if (allowedLocalOrigin) return true
            if (address.isLoopbackAddress) return false
            if (address.isLinkLocalAddress || address.isSiteLocalAddress) return false

            val bytes = address.address
            return when (bytes.size) {
                4 -> isPermittedIpv4(bytes)
                16 -> isPermittedIpv6(bytes, allowedLocalOrigin)
                else -> false
            }
        }

        private fun isPermittedIpv4(bytes: ByteArray): Boolean {
            val b0 = bytes[0].toInt() and 0xff
            val b1 = bytes[1].toInt() and 0xff
            val b2 = bytes[2].toInt() and 0xff
            return when {
                b0 == 0 -> false
                b0 == 100 && b1 in 64..127 -> false
                b0 == 192 && b1 == 0 && (b2 == 0 || b2 == 2) -> false
                b0 == 198 && (b1 in 18..19 || (b1 == 51 && b2 == 100)) -> false
                b0 == 203 && b1 == 0 && b2 == 113 -> false
                b0 >= 240 -> false
                else -> true
            }
        }

        private fun isPermittedIpv6(
            bytes: ByteArray,
            allowedLocalOrigin: Boolean,
        ): Boolean {
            val b0 = bytes[0].toInt() and 0xff
            val b1 = bytes[1].toInt() and 0xff

            // fc00::/7 Unique Local Addresses (ULA, RFC 4193)
            if ((b0 and 0xfe) == 0xfc) return false

            // 100::/64 Discard prefix (RFC 6666)
            if (b0 == 0x01 && b1 == 0x00 && (2..7).all { bytes[it] == 0.toByte() }) return false

            // 2001:db8::/32 Documentation (RFC 3849)
            if (b0 == 0x20 && b1 == 0x01 && bytes[2] == 0x0d.toByte() && bytes[3] == 0xb8.toByte()) return false

            // 2001:2::/48 Benchmarking (RFC 5180)
            if (b0 == 0x20 && b1 == 0x01 && bytes[2] == 0.toByte() && bytes[3] == 0x02.toByte()) return false

            // 64:ff9b:1::/48 Local-Use IPv4/IPv6 translation (RFC 8215)
            if (
                b0 == 0x00 &&
                b1 == 0x64 &&
                bytes[2] == 0xff.toByte() &&
                bytes[3] == 0x9b.toByte() &&
                bytes[4] == 0.toByte() &&
                bytes[5] == 1.toByte()
            ) {
                return false
            }

            // Embedded IPv4 check (IPv4-mapped, IPv4-compatible, NAT64 well-known, 6to4, Teredo)
            val unwrapped = unwrapIpv4(bytes)
            if (unwrapped.isNotEmpty()) {
                return unwrapped.all { isPermittedAddress(it, allowedLocalOrigin) }
            }

            return true
        }

        fun unwrapIpv4(bytes: ByteArray): List<InetAddress> {
            if (bytes.size != 16) return emptyList()

            val b0 = bytes[0].toInt() and 0xff
            val b1 = bytes[1].toInt() and 0xff

            // 1. IPv4-mapped (::ffff:0:0/96)
            val isMapped =
                (0..9).all { bytes[it] == 0.toByte() } &&
                    bytes[10] == 0xff.toByte() &&
                    bytes[11] == 0xff.toByte()
            if (isMapped) {
                return listOfNotNull(runCatching { InetAddress.getByAddress(bytes.copyOfRange(12, 16)) }.getOrNull())
            }

            // 2. IPv4-compatible (::0:0/96, deprecated RFC 4291)
            // Exclude :: and ::1 which are handled by loopback/any checks
            val isCompatible = (0..11).all { bytes[it] == 0.toByte() }
            if (isCompatible) {
                val last4 = bytes.copyOfRange(12, 16)
                val isZeroOrOne =
                    last4[0] == 0.toByte() &&
                        last4[1] == 0.toByte() &&
                        last4[2] == 0.toByte() &&
                        (last4[3] == 0.toByte() || last4[3] == 1.toByte())
                if (!isZeroOrOne) {
                    return listOfNotNull(runCatching { InetAddress.getByAddress(last4) }.getOrNull())
                }
            }

            // 3. NAT64 Well-Known Prefix (64:ff9b::/96)
            val isNat64WellKnown =
                b0 == 0x00 &&
                    b1 == 0x64 &&
                    bytes[2] == 0xff.toByte() &&
                    bytes[3] == 0x9b.toByte() &&
                    (4..11).all { bytes[it] == 0.toByte() }
            if (isNat64WellKnown) {
                return listOfNotNull(runCatching { InetAddress.getByAddress(bytes.copyOfRange(12, 16)) }.getOrNull())
            }

            // 4. 6to4 translation prefix (2002::/16)
            val is6to4 = b0 == 0x20 && b1 == 0x02
            if (is6to4) {
                return listOfNotNull(runCatching { InetAddress.getByAddress(bytes.copyOfRange(2, 6)) }.getOrNull())
            }

            // 5. Teredo tunneling (2001:0000::/32)
            val isTeredo = b0 == 0x20 && b1 == 0x01 && bytes[2] == 0.toByte() && bytes[3] == 0.toByte()
            if (isTeredo) {
                val serverIpv4 = runCatching { InetAddress.getByAddress(bytes.copyOfRange(4, 8)) }.getOrNull()
                val deobfuscated = ByteArray(4) { (bytes[12 + it].toInt() xor 0xff).toByte() }
                val clientIpv4 = runCatching { InetAddress.getByAddress(deobfuscated) }.getOrNull()
                return listOfNotNull(serverIpv4, clientIpv4)
            }

            return emptyList()
        }

        fun isUniqueLocalAddress(address: Inet6Address): Boolean = (address.address[0].toInt() and 0xfe) == 0xfc
    }
}

/** Explicit local origins are an opt-in exception to the public-network SSRF policy. */
class LnNetworkPolicy(
    private val allowedOrigins: () -> Set<LnNetworkOrigin> = { emptySet() },
) {
    fun allows(url: HttpUrl): Boolean = allowedOrigins().any { it.matches(url) }

    companion object {
        fun parseOrigins(value: String): Set<LnNetworkOrigin> =
            value
                .split(',', '\n', '\r', '\t', ' ')
                .asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .mapNotNull(LnNetworkOrigin::parse)
                .toSet()
    }
}

@ConsistentCopyVisibility
data class LnNetworkOrigin private constructor(
    private val scheme: String,
    private val host: String,
    private val port: Int,
) {
    fun matches(url: HttpUrl): Boolean = scheme == url.scheme && host == url.host && port == url.port

    companion object {
        fun parse(value: String): LnNetworkOrigin? {
            val url = value.toHttpUrlOrNull() ?: return null
            if (url.encodedPath != "/" || url.query != null || url.fragment != null) return null
            return LnNetworkOrigin(url.scheme, url.host, url.port)
        }
    }
}
