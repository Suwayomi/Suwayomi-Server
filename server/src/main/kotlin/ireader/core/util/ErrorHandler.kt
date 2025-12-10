package ireader.core.util

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

/**
 * Standardized error handling utilities for extensions.
 * Provides retry logic, error categorization, and logging.
 */
object ErrorHandler {

    /**
     * Sealed class representing different types of errors that can occur.
     */
    sealed class SourceError(message: String, cause: Throwable? = null) : Exception(message, cause) {
        class NetworkError(message: String = "Network request failed", cause: Throwable? = null) :
            SourceError(message, cause)

        class ParseError(message: String = "Failed to parse response", cause: Throwable? = null) :
            SourceError(message, cause)

        class AuthenticationError(message: String = "Authentication required", cause: Throwable? = null) :
            SourceError(message, cause)

        class RateLimitError(message: String = "Rate limit exceeded", cause: Throwable? = null) :
            SourceError(message, cause)

        class SourceUnavailable(message: String = "Source is unavailable", cause: Throwable? = null) :
            SourceError(message, cause)

        class UnknownError(message: String = "Unknown error occurred", cause: Throwable? = null) :
            SourceError(message, cause)
    }

    /**
     * Configuration for retry behavior.
     */
    data class RetryConfig(
        val maxAttempts: Int = 3,
        val initialDelayMs: Long = 1000,
        val maxDelayMs: Long = 10000,
        val factor: Double = 2.0
    )

    /**
     * Executes a block with retry logic using exponential backoff.
     *
     * @param config Retry configuration
     * @param shouldRetry Function to determine if an exception should trigger a retry
     * @param block The suspending function to execute
     * @return Result of the block execution
     * @throws Exception if all retry attempts fail
     */
    suspend fun <T> withRetry(
        config: RetryConfig = RetryConfig(),
        shouldRetry: (Exception) -> Boolean = { e ->
            e is SourceError.NetworkError || e is SourceError.RateLimitError
        },
        block: suspend (attempt: Int) -> T
    ): T {
        var lastException: Exception? = null

        repeat(config.maxAttempts) { attempt ->
            try {
                return block(attempt + 1)
            } catch (e: Exception) {
                lastException = e

                // Check if we should retry this error
                if (!shouldRetry(e) || attempt == config.maxAttempts - 1) {
                    throw e
                }

                // Calculate delay with exponential backoff
                val delayMs = min(
                    config.initialDelayMs * config.factor.pow(attempt.toDouble()).toLong(),
                    config.maxDelayMs
                )

                delay(delayMs)
            }
        }

        throw lastException ?: SourceError.UnknownError("Retry failed without exception")
    }

    /**
     * Safely executes a block and returns a Result.
     *
     * @param block The suspending function to execute
     * @return Result containing either the success value or the exception
     */
    suspend fun <T> safeRequest(block: suspend () -> T): Result<T> {
        return runCatching { block() }
            .onFailure { e ->
                // Log error (in production, use proper logging)
                println("Error in source request: ${e.message}")
            }
    }

    /**
     * Categorizes a generic exception into a specific SourceError type.
     *
     * @param exception The exception to categorize
     * @return A SourceError subclass representing the error type
     */
    fun categorizeError(exception: Throwable): SourceError {
        return when {
            exception is SourceError -> exception
            exception.message?.contains("timeout", ignoreCase = true) == true ->
                SourceError.NetworkError("Request timeout", exception)
            exception.message?.contains("401", ignoreCase = true) == true ||
            exception.message?.contains("403", ignoreCase = true) == true ->
                SourceError.AuthenticationError(cause = exception)
            exception.message?.contains("429", ignoreCase = true) == true ->
                SourceError.RateLimitError(cause = exception)
            exception.message?.contains("404", ignoreCase = true) == true ||
            exception.message?.contains("503", ignoreCase = true) == true ->
                SourceError.SourceUnavailable(cause = exception)
            else -> SourceError.UnknownError(exception.message ?: "Unknown error", exception)
        }
    }
}
