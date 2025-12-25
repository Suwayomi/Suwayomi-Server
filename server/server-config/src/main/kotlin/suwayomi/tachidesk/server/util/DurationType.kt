package suwayomi.tachidesk.server.util

import com.typesafe.config.Config
import io.github.config4k.ClassContainer
import io.github.config4k.CustomType
import io.github.config4k.readers.SelectReader
import io.github.config4k.toConfig
import kotlin.time.Duration

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
        return Duration.parse(convertToKotlinDuration(result))
    }

    override fun testParse(clazz: ClassContainer): Boolean = clazz.mapperClass.qualifiedName == "kotlin.time.Duration"

    override fun testToConfig(obj: Any): Boolean = obj as? Duration != null

    override fun toConfig(
        obj: Any,
        name: String,
    ): Config = (obj as Duration).toString().toConfig(name)

    companion object {
        // Regex to match ISO-8601 period part with years/months (e.g., P1Y2M3D, P7M, P1Y)
        private val PERIOD_REGEX = Regex("""P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)D)?(T.*)?""")

        /**
         * Converts ISO-8601 duration strings with years/months to Kotlin Duration format.
         * Kotlin's Duration.parse() doesn't support months/years because they're not fixed-length.
         * This converts them to days (1 year = 365 days, 1 month = 30 days).
         */
        fun convertToKotlinDuration(input: String): String {
            val match = PERIOD_REGEX.matchEntire(input) ?: return input

            val years = match.groupValues[1].toIntOrNull() ?: 0
            val months = match.groupValues[2].toIntOrNull() ?: 0
            val days = match.groupValues[3].toIntOrNull() ?: 0
            val timePart = match.groupValues[4] // e.g., "T2H30M"

            // If no years or months, return original (already valid for Kotlin)
            if (years == 0 && months == 0) {
                return input
            }

            // Convert years and months to days (approximate)
            val totalDays = (years * 365) + (months * 30) + days

            return if (timePart.isNotEmpty()) {
                "P${totalDays}D$timePart"
            } else {
                "P${totalDays}D"
            }
        }
    }
}
