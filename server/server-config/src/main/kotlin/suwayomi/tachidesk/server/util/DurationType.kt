package suwayomi.tachidesk.server.util

import com.typesafe.config.Config
import io.github.config4k.ClassContainer
import io.github.config4k.CustomType
import io.github.config4k.readers.SelectReader
import io.github.config4k.toConfig
import java.time.Period
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.toKotlinDuration
import java.time.Duration as JavaDuration

class DurationType : CustomType {
    override fun parse(
        clazz: ClassContainer,
        config: Config,
        name: String,
    ): Any? {
        val clazz = ClassContainer(String::class)
        val reader = SelectReader.getReader(clazz)
        val path = name
        val result = reader(config, path) as String
        return parseIso8601Duration(result)
    }

    override fun testParse(clazz: ClassContainer): Boolean = clazz.mapperClass.qualifiedName == "kotlin.time.Duration"

    override fun testToConfig(obj: Any): Boolean = obj as? Duration != null

    override fun toConfig(
        obj: Any,
        name: String,
    ): Config = (obj as Duration).toString().toConfig(name)

    companion object {
        /**
         * Parses ISO-8601 duration strings including years/months.
         * Kotlin's Duration.parse() doesn't support months/years because they're not fixed-length.
         * This converts them to days (1 year = 365 days, 1 month = 30 days).
         */
        fun parseIso8601Duration(input: String): Duration {
            val tIndex = input.indexOf('T')

            return when {
                // Duration only (PT2H30M)
                tIndex == 1 -> JavaDuration.parse(input).toKotlinDuration()

                // Period only (P1Y2M3D)
                tIndex == -1 -> {
                    val period = Period.parse(input)
                    (period.years * 365L + period.months * 30L + period.days).days
                }

                // Both period and time (P1DT2H)
                else -> {
                    val period = Period.parse(input.substring(0, tIndex))
                    val duration = JavaDuration.parse("P${input.substring(tIndex)}")
                    val periodDays = (period.years * 365L + period.months * 30L + period.days).days
                    periodDays + duration.toKotlinDuration()
                }
            }
        }
    }
}
