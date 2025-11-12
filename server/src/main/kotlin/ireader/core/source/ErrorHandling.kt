package ireader.core.source

import ireader.core.source.ParsingUtils.extractMainContent
import org.jsoup.nodes.Document

/**
 * Enhanced error handling for novel fetching and parsing operations
 */
sealed class FetchError {
    /**
     * Network-related errors
     */
    data class NetworkError(
        val message: String,
        val statusCode: Int? = null,
        val cause: Throwable? = null,
    ) : FetchError()

    /**
     * Parsing-related errors
     */
    data class ParsingError(
        val message: String,
        val url: String,
        val cause: Throwable? = null,
        val partialContent: String? = null,
    ) : FetchError()

    /**
     * Content validation errors
     */
    data class ValidationError(
        val message: String,
        val issues: List<String>,
    ) : FetchError()

    /**
     * Timeout errors
     */
    data class TimeoutError(
        val message: String,
        val timeoutMs: Long,
    ) : FetchError()

    /**
     * Authentication/Authorization errors
     */
    data class AuthError(
        val message: String,
        val requiresLogin: Boolean = false,
    ) : FetchError()

    /**
     * Rate limiting errors
     */
    data class RateLimitError(
        val message: String,
        val retryAfterMs: Long? = null,
    ) : FetchError()

    /**
     * Unknown errors
     */
    data class UnknownError(
        val message: String,
        val cause: Throwable? = null,
    ) : FetchError()

    /**
     * Get user-friendly error message
     */
    fun getUserMessage(): String =
        when (this) {
            is NetworkError -> "Network error: $message${statusCode?.let { " (Status: $it)" } ?: ""}"
            is ParsingError -> "Failed to parse content: $message"
            is ValidationError -> "Invalid content: ${issues.joinToString(", ")}"
            is TimeoutError -> "Request timed out after ${timeoutMs}ms"
            is AuthError -> if (requiresLogin) "Login required: $message" else "Authentication error: $message"
            is RateLimitError -> "Rate limit exceeded${retryAfterMs?.let { ". Retry after ${it}ms" } ?: ""}"
            is UnknownError -> "An error occurred: $message"
        }

    /**
     * Check if error is retryable
     */
    fun isRetryable(): Boolean =
        when (this) {
            is NetworkError -> statusCode in listOf(408, 429, 500, 502, 503, 504) || statusCode == null
            is TimeoutError -> true
            is RateLimitError -> true
            is ParsingError -> false
            is ValidationError -> false
            is AuthError -> false
            is UnknownError -> false
        }

    /**
     * Get suggested retry delay in milliseconds
     */
    fun getRetryDelayMs(): Long =
        when (this) {
            is RateLimitError -> retryAfterMs ?: 5000L
            is NetworkError ->
                when (statusCode) {
                    429 -> 10000L // Rate limit
                    503 -> 5000L // Service unavailable
                    else -> 2000L
                }
            is TimeoutError -> 3000L
            else -> 1000L
        }
}

/**
 * Result wrapper for fetch operations with error handling
 */
sealed class FetchResult<out T> {
    data class Success<T>(
        val data: T,
    ) : FetchResult<T>()

    data class Error(
        val error: FetchError,
    ) : FetchResult<Nothing>()

    /**
     * Map success value
     */
    fun <R> map(transform: (T) -> R): FetchResult<R> =
        when (this) {
            is Success -> Success(transform(data))
            is Error -> this
        }

    /**
     * Get value or null
     */
    fun getOrNull(): T? =
        when (this) {
            is Success -> data
            is Error -> null
        }

    /**
     * Execute block on success
     */
    inline fun onSuccess(block: (T) -> Unit): FetchResult<T> {
        if (this is Success) {
            block(data)
        }
        return this
    }

    /**
     * Execute block on error
     */
    inline fun onError(block: (FetchError) -> Unit): FetchResult<T> {
        if (this is Error) {
            block(error)
        }
        return this
    }
}

/**
 * Retry strategy for failed operations
 */
class RetryStrategy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 10000L,
    val backoffMultiplier: Double = 2.0,
) {
    /**
     * Calculate delay for retry attempt
     */
    fun getDelayForAttempt(attempt: Int): Long {
        val delay = (initialDelayMs * Math.pow(backoffMultiplier, attempt.toDouble())).toLong()
        return minOf(delay, maxDelayMs)
    }

    /**
     * Check if should retry
     */
    fun shouldRetry(
        attempt: Int,
        error: FetchError,
    ): Boolean = attempt < maxAttempts && error.isRetryable()
}

/**
 * Error handler with retry logic
 */
object ErrorHandler {
    /**
     * Execute operation with retry logic
     */
    suspend fun <T> executeWithRetry(
        strategy: RetryStrategy = RetryStrategy(),
        operation: suspend (attempt: Int) -> FetchResult<T>,
    ): FetchResult<T> {
        var attempt = 0
        var lastError: FetchError? = null

        while (attempt < strategy.maxAttempts) {
            val result =
                try {
                    operation(attempt)
                } catch (e: Exception) {
                    FetchResult.Error(
                        FetchError.UnknownError(
                            message = e.message ?: "Unknown error",
                            cause = e,
                        ),
                    )
                }

            when (result) {
                is FetchResult.Success -> return result
                is FetchResult.Error -> {
                    lastError = result.error

                    if (!strategy.shouldRetry(attempt, result.error)) {
                        return result
                    }

                    // Wait before retry
                    val delay =
                        maxOf(
                            strategy.getDelayForAttempt(attempt),
                            result.error.getRetryDelayMs(),
                        )
                    kotlinx.coroutines.delay(delay)

                    attempt++
                }
            }
        }

        return FetchResult.Error(
            lastError ?: FetchError.UnknownError("Max retry attempts reached"),
        )
    }

    /**
     * Convert exception to FetchError
     */
    fun fromException(
        e: Throwable,
        context: String = "",
    ): FetchError =
        when {
            e is java.net.SocketTimeoutException || e is java.util.concurrent.TimeoutException -> {
                FetchError.TimeoutError(
                    message = "Request timed out${if (context.isNotEmpty()) ": $context" else ""}",
                    timeoutMs = 30000L,
                )
            }
            e is java.net.UnknownHostException || e is java.net.ConnectException -> {
                FetchError.NetworkError(
                    message = "Cannot connect to server${if (context.isNotEmpty()) ": $context" else ""}",
                    cause = e,
                )
            }
            e is java.io.IOException -> {
                FetchError.NetworkError(
                    message = "Network error${if (context.isNotEmpty()) ": $context" else ""}",
                    cause = e,
                )
            }
            e.message?.contains("parse", ignoreCase = true) == true -> {
                FetchError.ParsingError(
                    message = e.message ?: "Parsing failed",
                    url = context,
                    cause = e,
                )
            }
            else -> {
                FetchError.UnknownError(
                    message = e.message ?: "Unknown error${if (context.isNotEmpty()) ": $context" else ""}",
                    cause = e,
                )
            }
        }

    /**
     * Validate parsed content and return error if invalid
     */
    fun validateParsedContent(
        content: String,
        minLength: Int = 50,
    ): FetchError? {
        val validation = ParsingErrorRecovery.validateContent(content)

        return if (!validation.isValid) {
            FetchError.ValidationError(
                message = "Content validation failed",
                issues = validation.issues,
            )
        } else {
            null
        }
    }

    /**
     * Create error from HTTP status code
     */
    fun fromHttpStatus(
        statusCode: Int,
        message: String = "",
    ): FetchError =
        when (statusCode) {
            in 400..499 -> {
                when (statusCode) {
                    401, 403 ->
                        FetchError.AuthError(
                            message = message.ifEmpty { "Authentication required" },
                            requiresLogin = statusCode == 401,
                        )
                    429 ->
                        FetchError.RateLimitError(
                            message = message.ifEmpty { "Too many requests" },
                        )
                    else ->
                        FetchError.NetworkError(
                            message = message.ifEmpty { "Client error" },
                            statusCode = statusCode,
                        )
                }
            }
            in 500..599 ->
                FetchError.NetworkError(
                    message = message.ifEmpty { "Server error" },
                    statusCode = statusCode,
                )
            else ->
                FetchError.NetworkError(
                    message = message.ifEmpty { "HTTP error" },
                    statusCode = statusCode,
                )
        }
}

/**
 * Fallback parsing strategies for when primary parsing fails
 */
object FallbackStrategies {
    /**
     * Strategy 1: Try alternative selectors
     */
    fun tryAlternativeSelectors(
        document: Document,
        primarySelector: String,
        alternativeSelectors: List<String>,
    ): FetchResult<String> {
        // Try primary selector first
        val primaryElement = document.selectFirst(primarySelector)
        if (primaryElement != null && primaryElement.text().length > 50) {
            return FetchResult.Success(primaryElement.text())
        }

        // Try alternatives
        for (selector in alternativeSelectors) {
            val element = document.selectFirst(selector)
            if (element != null && element.text().length > 50) {
                return FetchResult.Success(element.text())
            }
        }

        return FetchResult.Error(
            FetchError.ParsingError(
                message = "No content found with any selector",
                url = document.baseUri(),
            ),
        )
    }

    /**
     * Strategy 2: Extract largest text block
     */
    fun extractLargestTextBlock(document: Document): FetchResult<String> {
        val largestBlock =
            document
                .body()
                .select("div, article, section, p")
                .filter { it.text().length > 100 }
                .maxByOrNull { it.text().length }

        return if (largestBlock != null) {
            FetchResult.Success(largestBlock.text())
        } else {
            FetchResult.Error(
                FetchError.ParsingError(
                    message = "No substantial text content found",
                    url = document.baseUri(),
                ),
            )
        }
    }

    /**
     * Strategy 3: Use heuristics to find main content
     */
    fun useContentHeuristics(document: Document): FetchResult<String> {
        val mainContent = document.extractMainContent()

        return if (mainContent != null && mainContent.text().length > 50) {
            FetchResult.Success(mainContent.text())
        } else {
            FetchResult.Error(
                FetchError.ParsingError(
                    message = "Could not identify main content",
                    url = document.baseUri(),
                ),
            )
        }
    }

    /**
     * Apply all fallback strategies in sequence
     */
    fun applyAllStrategies(
        document: Document,
        primarySelector: String,
        alternativeSelectors: List<String> = emptyList(),
    ): FetchResult<String> {
        // Strategy 1: Alternative selectors
        if (alternativeSelectors.isNotEmpty()) {
            val result = tryAlternativeSelectors(document, primarySelector, alternativeSelectors)
            if (result is FetchResult.Success) {
                return result
            }
        }

        // Strategy 2: Content heuristics
        val heuristicsResult = useContentHeuristics(document)
        if (heuristicsResult is FetchResult.Success) {
            return heuristicsResult
        }

        // Strategy 3: Largest text block
        return extractLargestTextBlock(document)
    }
}
