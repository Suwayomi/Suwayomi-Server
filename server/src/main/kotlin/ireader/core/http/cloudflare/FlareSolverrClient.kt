package ireader.core.http.cloudflare

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import ireader.core.log.Log
import ireader.core.util.currentTimeMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for FlareSolverr service
 * FlareSolverr is a proxy server that solves Cloudflare challenges
 * https://github.com/FlareSolverr/FlareSolverr
 */
interface FlareSolverrClient {
    /**
     * Solve a Cloudflare challenge
     */
    suspend fun solve(request: FlareSolverrRequest): FlareSolverrResponse
    
    /**
     * Create a persistent session
     */
    suspend fun createSession(sessionId: String): Boolean
    
    /**
     * Destroy a session
     */
    suspend fun destroySession(sessionId: String): Boolean
    
    /**
     * List active sessions
     */
    suspend fun listSessions(): List<String>
    
    /**
     * Check if FlareSolverr is available
     */
    suspend fun isAvailable(): Boolean
    
    /**
     * Get FlareSolverr version info
     */
    suspend fun getVersion(): String?
}

@Serializable
data class FlareSolverrRequest(
    val cmd: String = "request.get",
    val url: String,
    val maxTimeout: Int = 60000,
    val session: String? = null,
    val cookies: List<FlareSolverrCookie>? = null,
    val returnOnlyCookies: Boolean = false,
    val proxy: FlareSolverrProxy? = null,
    val postData: String? = null
)

@Serializable
data class FlareSolverrCookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val expires: Double? = null,
    val size: Int? = null,
    val httpOnly: Boolean? = null,
    val secure: Boolean? = null,
    val session: Boolean? = null,
    val sameSite: String? = null
)

@Serializable
data class FlareSolverrProxy(
    val url: String
)

@Serializable
data class FlareSolverrResponse(
    val status: String,
    val message: String,
    val startTimestamp: Long? = null,
    val endTimestamp: Long? = null,
    val version: String? = null,
    val solution: FlareSolverrSolution? = null
) {
    val isSuccess: Boolean get() = status == "ok"
    val duration: Long? get() = if (startTimestamp != null && endTimestamp != null) {
        endTimestamp - startTimestamp
    } else null
}

@Serializable
data class FlareSolverrSolution(
    val url: String,
    val status: Int,
    val headers: Map<String, String>? = null,
    val response: String? = null,
    val cookies: List<FlareSolverrCookie> = emptyList(),
    val userAgent: String
)

/**
 * Default implementation of FlareSolverrClient
 */
class FlareSolverrClientImpl(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://localhost:8191"
) : FlareSolverrClient {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override suspend fun solve(request: FlareSolverrRequest): FlareSolverrResponse {
        return try {
            val response = httpClient.post("$baseUrl/v1") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(FlareSolverrRequest.serializer(), request))
            }
            
            val responseText = response.bodyAsText()
            json.decodeFromString(FlareSolverrResponse.serializer(), responseText)
        } catch (e: Exception) {
            Log.error { "FlareSolverr request failed" }
            FlareSolverrResponse(
                status = "error",
                message = "FlareSolverr request failed: ${e.message}"
            )
        }
    }

    override suspend fun createSession(sessionId: String): Boolean {
        return try {
            val request = mapOf(
                "cmd" to "sessions.create",
                "session" to sessionId
            )
            val response = httpClient.post("$baseUrl/v1") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            Log.error { "Failed to create FlareSolverr session" }
            false
        }
    }
    
    override suspend fun destroySession(sessionId: String): Boolean {
        return try {
            val request = mapOf(
                "cmd" to "sessions.destroy",
                "session" to sessionId
            )
            val response = httpClient.post("$baseUrl/v1") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            Log.error { "Failed to destroy FlareSolverr session" }
            false
        }
    }
    
    override suspend fun listSessions(): List<String> {
        return try {
            val request = mapOf("cmd" to "sessions.list")
            val response = httpClient.post("$baseUrl/v1") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
            val responseText = response.bodyAsText()
            // Parse sessions from response
            emptyList() // TODO: Parse actual sessions
        } catch (e: Exception) {
            Log.error { "Failed to list FlareSolverr sessions" }
            emptyList()
        }
    }
    
    override suspend fun isAvailable(): Boolean {
        return try {
            val response = httpClient.get("$baseUrl/health")
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getVersion(): String? {
        return try {
            val response = httpClient.get("$baseUrl/health")
            if (response.status.isSuccess()) {
                val text = response.bodyAsText()
                // Extract version from response
                json.decodeFromString<Map<String, String>>(text)["version"]
            } else null
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * FlareSolverr bypass strategy
 * Uses FlareSolverr service to solve Cloudflare challenges
 */
class FlareSolverrStrategy(
    private val client: FlareSolverrClient
) : CloudflareBypassStrategy {
    
    override val priority = 80 // Lower than WebView but higher than nothing
    override val name = "FlareSolverr"
    
    override suspend fun canHandle(challenge: CloudflareChallenge): Boolean {
        // FlareSolverr can handle most challenges except IP blocks
        return when (challenge) {
            is CloudflareChallenge.None -> false
            is CloudflareChallenge.BlockedIP -> false
            else -> client.isAvailable()
        }
    }
    
    override suspend fun bypass(
        url: String,
        challenge: CloudflareChallenge,
        config: BypassConfig
    ): BypassResult {
        if (!client.isAvailable()) {
            return BypassResult.Failed(
                "FlareSolverr is not available",
                challenge,
                canRetry = false
            )
        }
        
        val request = FlareSolverrRequest(
            cmd = "request.get",
            url = url,
            maxTimeout = config.timeout.toInt(),
            returnOnlyCookies = true,
            proxy = config.proxy?.let { 
                FlareSolverrProxy("${it.type.name.lowercase()}://${it.host}:${it.port}")
            }
        )
        
        val response = client.solve(request)
        
        return if (response.isSuccess && response.solution != null) {
            val solution = response.solution
            val cfClearance = solution.cookies.find { it.name == "cf_clearance" }?.value
            
            if (cfClearance != null) {
                BypassResult.Success(
                    ClearanceCookie(
                        cfClearance = cfClearance,
                        cfBm = solution.cookies.find { it.name == "__cf_bm" }?.value,
                        userAgent = solution.userAgent,
                        timestamp = currentTimeMillis(),
                        expiresAt = currentTimeMillis() + ClearanceCookie.DEFAULT_VALIDITY_MS,
                        domain = url.extractDomain()
                    )
                )
            } else {
                BypassResult.Failed(
                    "FlareSolverr solved challenge but no cf_clearance cookie found",
                    challenge,
                    canRetry = true
                )
            }
        } else {
            BypassResult.Failed(
                "FlareSolverr failed: ${response.message}",
                challenge,
                canRetry = true
            )
        }
    }
}
