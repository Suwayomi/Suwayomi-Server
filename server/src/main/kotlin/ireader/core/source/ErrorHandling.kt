package ireader.core.source

import com.fleeksoft.ksoup.nodes.Document
import ireader.core.source.ParsingUtils.extractMainContent

/**
 * Enhanced error handling for novel fetching and parsing operations
 */
sealed class FetchError {
    data class NetworkError(
        val message: String,
        val statusCode: Int? = null,
        val cause: Throwable? = null
    ) : FetchError()
    
    data class ParsingError(
        val message: String,
        val url: String,
        val cause: Throwable? = null,
        val partialContent: String? = null
    ) : FetchError()
    
    data class ValidationError(
        val message: String,
        val issues: List<String>
    ) : FetchError()
    
    data class TimeoutError(
        val message: String,
        val timeoutMs: Long
    ) : FetchError()
    
    data class AuthError(
        val message: String,
        val requiresLogin: Boolean = false
    ) : FetchError()
    
    data class RateLimitError(
        val message: String,
        val retryAfterMs: Long? = null
    ) : FetchError()
    
    data class UnknownError(
        val message: String,
        val cause: Throwable? = null
    ) : FetchError()
    
    fun getUserMessage(): String {
        return when (this) {
            is NetworkError -> "Network error: $message${statusCode?.let { " (Status: $it)" } ?: ""}"
            is ParsingError -> "Failed to parse content: $message"
            is ValidationError -> "Invalid content: ${issues.joinToString(", ")}"
            is TimeoutError -> "Request timed out after ${timeoutMs}ms"
            is AuthError -> if (requiresLogin) "Login required: $message" else "Authentication error: $message"
            is RateLimitError -> "Rate limit exceeded${retryAfterMs?.let { ". Retry after ${it}ms" } ?: ""}"
            is UnknownError -> "An error occurred: $message"
        }
    }
    
    fun isRetryable(): Boolean {
        return when (this) {
            is NetworkError -> statusCode in listOf(408, 429, 500, 502, 503, 504) || statusCode == null
            is TimeoutError -> true
            is RateLimitError -> true
            is ParsingError -> false
            is ValidationError -> false
            is AuthError -> false
            is UnknownError -> false
        }
    }
    
    fun getRetryDelayMs(): Long {
        return when (this) {
            is RateLimitError -> retryAfterMs ?: 5000L
            is NetworkError -> when (statusCode) {
                429 -> 10000L
                503 -> 5000L
                else -> 2000L
            }
            is TimeoutError -> 3000L
            else -> 1000L
        }
    }
}

sealed class FetchResult<out T> {
    data class Success<T>(val data: T) : FetchResult<T>()
    data class Error(val error: FetchError) : FetchResult<Nothing>()
    
    fun <R> map(transform: (T) -> R): FetchResult<R> {
        return when (this) {
            is Success -> Success(transform(data))
            is Error -> this
        }
    }
    
    fun getOrNull(): T? {
        return when (this) {
            is Success -> data
            is Error -> null
        }
    }
    
    inline fun onSuccess(block: (T) -> Unit): FetchResult<T> {
        if (this is Success) {
            block(data)
        }
        return this
    }
    
    inline fun onError(block: (FetchError) -> Unit): FetchResult<T> {
        if (this is Error) {
            block(error)
        }
        return this
    }
}

class RetryStrategy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 10000L,
    val backoffMultiplier: Double = 2.0
) {
    fun getDelayForAttempt(attempt: Int): Long {
        var delay = initialDelayMs
        repeat(attempt) { delay = (delay * backoffMultiplier).toLong() }
        return minOf(delay, maxDelayMs)
    }
    
    fun shouldRetry(attempt: Int, error: FetchError): Boolean {
        return attempt < maxAttempts && error.isRetryable()
    }
}

object ErrorHandler {
    
    suspend fun <T> executeWithRetry(
        strategy: RetryStrategy = RetryStrategy(),
        operation: suspend (attempt: Int) -> FetchResult<T>
    ): FetchResult<T> {
        var lastError: FetchError? = null
        
        repeat(strategy.maxAttempts) { currentAttempt ->
            val result = try {
                operation(currentAttempt)
            } catch (e: Exception) {
                FetchResult.Error(
                    FetchError.UnknownError(
                        message = e.message ?: "Unknown error",
                        cause = e
                    )
                )
            }
            
            when (result) {
                is FetchResult.Success -> return result
                is FetchResult.Error -> {
                    lastError = result.error
                    
                    val isLastAttempt = currentAttempt >= strategy.maxAttempts - 1
                    if (isLastAttempt || !result.error.isRetryable()) {
                        return result
                    }
                    
                    val delay = maxOf(
                        strategy.getDelayForAttempt(currentAttempt),
                        result.error.getRetryDelayMs()
                    )
                    kotlinx.coroutines.delay(delay)
                }
            }
        }
        
        return FetchResult.Error(
            lastError ?: FetchError.UnknownError("Max retry attempts (${strategy.maxAttempts}) reached")
        )
    }
    
    fun fromException(e: Throwable, context: String = ""): FetchError {
        val contextSuffix = if (context.isNotEmpty()) ": $context" else ""
        val errorMessage = e.message?.lowercase() ?: ""
        
        return when {
            errorMessage.contains("timeout") || errorMessage.contains("timed out") -> {
                FetchError.TimeoutError(
                    message = "Request timed out$contextSuffix",
                    timeoutMs = 30000L
                )
            }
            errorMessage.contains("unknown host") || errorMessage.contains("unable to resolve") -> {
                FetchError.NetworkError(
                    message = "Cannot resolve host$contextSuffix",
                    cause = e
                )
            }
            errorMessage.contains("connect") && (errorMessage.contains("refused") || errorMessage.contains("failed")) -> {
                FetchError.NetworkError(
                    message = "Cannot connect to server$contextSuffix",
                    cause = e
                )
            }
            errorMessage.contains("io") || errorMessage.contains("stream") -> {
                val ioMessage = when {
                    errorMessage.contains("closed") -> "Connection closed"
                    errorMessage.contains("reset") -> "Connection reset"
                    else -> "Network error"
                }
                FetchError.NetworkError(
                    message = "$ioMessage$contextSuffix",
                    cause = e
                )
            }
            errorMessage.contains("parse") -> {
                FetchError.ParsingError(
                    message = e.message ?: "Parsing failed",
                    url = context,
                    cause = e
                )
            }
            else -> {
                FetchError.UnknownError(
                    message = e.message ?: "Unknown error$contextSuffix",
                    cause = e
                )
            }
        }
    }
    
    fun validateParsedContent(content: String, minLength: Int = 50): FetchError? {
        val validation = ParsingErrorRecovery.validateContent(content)
        
        return if (!validation.isValid) {
            FetchError.ValidationError(
                message = "Content validation failed",
                issues = validation.issues
            )
        } else {
            null
        }
    }
    
    fun fromHttpStatus(statusCode: Int, message: String = ""): FetchError {
        return when (statusCode) {
            in 400..499 -> {
                when (statusCode) {
                    401, 403 -> FetchError.AuthError(
                        message = message.ifEmpty { "Authentication required" },
                        requiresLogin = statusCode == 401
                    )
                    429 -> FetchError.RateLimitError(
                        message = message.ifEmpty { "Too many requests" }
                    )
                    else -> FetchError.NetworkError(
                        message = message.ifEmpty { "Client error" },
                        statusCode = statusCode
                    )
                }
            }
            in 500..599 -> FetchError.NetworkError(
                message = message.ifEmpty { "Server error" },
                statusCode = statusCode
            )
            else -> FetchError.NetworkError(
                message = message.ifEmpty { "HTTP error" },
                statusCode = statusCode
            )
        }
    }
}

object FallbackStrategies {
    
    fun tryAlternativeSelectors(
        document: Document,
        primarySelector: String,
        alternativeSelectors: List<String>
    ): FetchResult<String> {
        val primaryElement = document.selectFirst(primarySelector)
        if (primaryElement != null && primaryElement.text().length > 50) {
            return FetchResult.Success(primaryElement.text())
        }
        
        for (selector in alternativeSelectors) {
            val element = document.selectFirst(selector)
            if (element != null && element.text().length > 50) {
                return FetchResult.Success(element.text())
            }
        }
        
        return FetchResult.Error(
            FetchError.ParsingError(
                message = "No content found with any selector",
                url = document.baseUri()
            )
        )
    }
    
    fun extractLargestTextBlock(document: Document): FetchResult<String> {
        val largestBlock = document.body()
            ?.select("div, article, section, p")
            ?.filter { it.text().length > 100 }
            ?.maxByOrNull { it.text().length }
        
        return if (largestBlock != null) {
            FetchResult.Success(largestBlock.text())
        } else {
            FetchResult.Error(
                FetchError.ParsingError(
                    message = "No substantial text content found",
                    url = document.baseUri()
                )
            )
        }
    }
    
    fun useContentHeuristics(document: Document): FetchResult<String> {
        val mainContent = document.extractMainContent()
        
        return if (mainContent != null && mainContent.text().length > 50) {
            FetchResult.Success(mainContent.text())
        } else {
            FetchResult.Error(
                FetchError.ParsingError(
                    message = "Could not identify main content",
                    url = document.baseUri()
                )
            )
        }
    }
    
    fun applyAllStrategies(
        document: Document,
        primarySelector: String,
        alternativeSelectors: List<String> = emptyList()
    ): FetchResult<String> {
        if (alternativeSelectors.isNotEmpty()) {
            val result = tryAlternativeSelectors(document, primarySelector, alternativeSelectors)
            if (result is FetchResult.Success) {
                return result
            }
        }
        
        val heuristicsResult = useContentHeuristics(document)
        if (heuristicsResult is FetchResult.Success) {
            return heuristicsResult
        }
        
        return extractLargestTextBlock(document)
    }
}
