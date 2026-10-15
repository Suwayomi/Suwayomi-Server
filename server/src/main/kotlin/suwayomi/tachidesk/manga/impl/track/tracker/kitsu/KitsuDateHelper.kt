package suwayomi.tachidesk.manga.impl.track.tracker.kitsu

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object KitsuDateHelper {
    private const val PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    private val formatter =
        SimpleDateFormat(PATTERN, Locale.ENGLISH).apply {
            // The API timestamps are UTC (the trailing 'Z' is literal); without
            // an explicit zone the JVM default shifted both reads and writes.
            timeZone = TimeZone.getTimeZone("UTC")
        }

    // SimpleDateFormat is not thread-safe.
    @Synchronized
    fun convert(dateValue: Long): String? {
        if (dateValue == 0L) return null

        return formatter.format(Date(dateValue))
    }

    @Synchronized
    fun parse(dateString: String?): Long {
        if (dateString == null) return 0L

        val dateValue = formatter.parse(dateString)

        return dateValue?.time ?: return 0
    }
}
