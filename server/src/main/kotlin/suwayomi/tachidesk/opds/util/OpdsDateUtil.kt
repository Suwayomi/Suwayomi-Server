package suwayomi.tachidesk.opds.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Utilities for handling dates in OPDS format.
 * The OPDS standard uses RFC 3339 formatted dates.
 */
object OpdsDateUtil {
    /**
     * Date formatter for OPDS in RFC 3339 format.
     * Example: "2023-05-23T15:30:00Z"
     */
    val opdsDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    /**
     * Formats the current date and time for OPDS.
     * @return String with the formatted current date
     */
    fun formatCurrentInstantForOpds(): String = opdsDateFormatter.format(Instant.now())

    /**
     * Formats a specific instant for OPDS.
     * @param instant The instant to format
     * @return String with the formatted date
     */
    fun formatInstantForOpds(instant: Instant): String = opdsDateFormatter.format(instant)

    /**
     * Formats a timestamp in milliseconds for OPDS.
     * @param epochMillis Time in milliseconds since Unix epoch
     * @return String with the formatted date
     */
    fun formatEpochMillisForOpds(epochMillis: Long): String = opdsDateFormatter.format(Instant.ofEpochMilli(epochMillis))
}
