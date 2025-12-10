package ireader.core.util


import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Utility object for parsing dates from various formats commonly found in web novels.
 * Provides thread-safe date parsing with support for relative dates (e.g., "2 hours ago").
 */
object DateParser {

    /**
     * Parses a date string that may be in relative format (e.g., "2 hours ago")
     * or absolute format (e.g., "Jan 15, 2024").
     *
     * @param dateStr The date string to parse
     * @return Unix timestamp in milliseconds, or 0 if parsing fails
     */
    fun parseRelativeOrAbsoluteDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L

        return when {
            "ago" in dateStr.lowercase() -> parseRelativeDate(dateStr)
            else -> parseAbsoluteDate(dateStr)
        }
    }

    /**
     * Parses relative date strings like "2 hours ago", "3 days ago", etc.
     *
     * @param dateStr The relative date string
     * @return Unix timestamp in milliseconds, or 0 if parsing fails
     */
    @OptIn(ExperimentalTime::class)
    fun parseRelativeDate(dateStr: String): Long {
        return try {
            val parts = dateStr.lowercase().split(' ')
            if (parts.size < 2) return 0L

            val value = parts[0].toIntOrNull() ?: return 0L
            val unit = parts[1]

            val now = Clock.System.now()
            val duration = when {
                "sec" in unit || "second" in unit -> value.seconds
                "min" in unit || "minute" in unit -> value.minutes
                "hour" in unit -> value.hours
                "day" in unit -> value.days
                "week" in unit -> (value * 7).days
                "month" in unit -> (value * 30).days // Approximate
                "year" in unit -> (value * 365).days // Approximate
                else -> return 0L
            }

            (now - duration).toEpochMilliseconds()
        } catch (e: Exception) {
            0L
        }
    }


    /**
     * Parses absolute date strings using common date formats.
     * Supports formats like: "Jan 15, 2024", "2024-01-15", "15/01/2024", etc.
     *
     * @param dateStr The absolute date string
     * @return Unix timestamp in milliseconds, or 0 if parsing fails
     */
    fun parseAbsoluteDate(dateStr: String): Long {
        val cleaned = dateStr.trim()

        // Try ISO format first (2024-01-15 or 2024-01-15T10:30:00)
        tryParseIso(cleaned)?.let { return it }

        // Try common formats
        tryParseCommonFormats(cleaned)?.let { return it }

        return 0L
    }

    @OptIn(ExperimentalTime::class)
    private fun tryParseIso(dateStr: String): Long? {
        return try {
            // Try full ISO instant
            Instant.parse(dateStr).toEpochMilliseconds()
        } catch (e: Exception) {
            try {
                // Try date only (2024-01-15)
                val date = LocalDate.parse(dateStr)
                date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
            } catch (e: Exception) {
                null
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun tryParseCommonFormats(dateStr: String): Long? {
        // Month name formats: "Jan 15, 2024" or "January 15, 2024"
        val monthNamePattern = Regex("""(\w+)\s+(\d{1,2}),?\s*(\d{4})""")
        monthNamePattern.find(dateStr)?.let { match ->
            val (monthStr, day, year) = match.destructured
            val month = parseMonth(monthStr) ?: return@let null
            return try {
                LocalDate(year.toInt(), month, day.toInt())
                    .atStartOfDayIn(TimeZone.UTC)
                    .toEpochMilliseconds()
            } catch (e: Exception) {
                null
            }
        }

        // Numeric formats: "01/15/2024" or "15/01/2024" or "2024/01/15"
        val numericPattern = Regex("""(\d{1,4})[/\-.](\d{1,2})[/\-.](\d{1,4})""")
        numericPattern.find(dateStr)?.let { match ->
            val (first, second, third) = match.destructured
            return tryParseNumericDate(first.toInt(), second.toInt(), third.toInt())
        }

        return null
    }

    private fun parseMonth(monthStr: String): Int? {
        val months = mapOf(
            "jan" to 1, "january" to 1,
            "feb" to 2, "february" to 2,
            "mar" to 3, "march" to 3,
            "apr" to 4, "april" to 4,
            "may" to 5,
            "jun" to 6, "june" to 6,
            "jul" to 7, "july" to 7,
            "aug" to 8, "august" to 8,
            "sep" to 9, "sept" to 9, "september" to 9,
            "oct" to 10, "october" to 10,
            "nov" to 11, "november" to 11,
            "dec" to 12, "december" to 12
        )
        return months[monthStr.lowercase()]
    }

    @OptIn(ExperimentalTime::class)
    private fun tryParseNumericDate(first: Int, second: Int, third: Int): Long? {
        return try {
            val (year, month, day) = when {
                // YYYY-MM-DD or YYYY/MM/DD
                first > 1000 -> Triple(first, second, third)
                // DD/MM/YYYY (European) or MM/DD/YYYY (US) - assume MM/DD/YYYY if ambiguous
                third > 1000 -> {
                    if (first > 12) Triple(third, second, first) // DD/MM/YYYY
                    else Triple(third, first, second) // MM/DD/YYYY
                }
                else -> return null
            }
            LocalDate(year, month, day)
                .atStartOfDayIn(TimeZone.UTC)
                .toEpochMilliseconds()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets the current timestamp in milliseconds.
     */
    @OptIn(ExperimentalTime::class)
    fun now(): Long = Clock.System.now().toEpochMilliseconds()
}
