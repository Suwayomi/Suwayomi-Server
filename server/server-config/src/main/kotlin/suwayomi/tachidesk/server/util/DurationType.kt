package suwayomi.tachidesk.server.util

import com.typesafe.config.Config
import io.github.config4k.ClassContainer
import io.github.config4k.CustomType
import io.github.config4k.readers.SelectReader
import io.github.config4k.toConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.DateTimePeriod

class DurationType : CustomType {
    override fun parse(
        clazz: ClassContainer,
        config: Config,
        name: String,
    ): Any? {
        val stringContainer = ClassContainer(String::class)
        val reader = SelectReader.getReader(stringContainer)
        val result = reader(config, name) as String
        return parseDuration(result)
    }

    override fun testParse(clazz: ClassContainer): Boolean = clazz.mapperClass.qualifiedName == "kotlin.time.Duration"

    override fun testToConfig(obj: Any): Boolean = obj as? Duration != null

    override fun toConfig(
        obj: Any,
        name: String,
    ): Config = (obj as Duration).toString().toConfig(name)

    companion object {
        private const val DAYS_PER_YEAR = 365
        private const val DAYS_PER_MONTH = 30

        /**
         * Parses duration strings in either Kotlin Duration format (5m, 1h, 30s, 60d)
         * or ISO-8601 format (PT5M, P1Y2M3DT4H5M6S).
         *
         * For ISO-8601 with years/months, this converts them to days
         * (1 year = 365 days, 1 month = 30 days).
         */
        fun parseDuration(input: String): Duration {
            // First try Kotlin Duration format (e.g., "5m", "1h", "30s", "60d")
            val kotlinDuration = Duration.parseOrNull(input)
            if (kotlinDuration != null) {
                return kotlinDuration
            }

            // Fall back to ISO-8601 format using kotlinx-datetime
            val period = DateTimePeriod.parse(input)
            val totalDays = (period.years * DAYS_PER_YEAR) + (period.months * DAYS_PER_MONTH) + period.days
            return totalDays.days + period.hours.hours + period.minutes.minutes + period.seconds.seconds
        }
    }
}
