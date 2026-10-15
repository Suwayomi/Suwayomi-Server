package suwayomi.tachidesk.server.settings

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

private val logger = KotlinLogging.logger { }

/**
 * Reflection-dump of a settings view (`Settings`, `UserSettings`, partial variants) into a
 * `name -> value` map. Properties that can't be read are skipped with a debug log instead of
 * silently disappearing.
 */
internal fun Any.asMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()

    this::class.memberProperties.forEach { property ->
        try {
            // Skip the 'id' property from Node interface
            if (property.name == "id") return@forEach

            @Suppress("UNCHECKED_CAST")
            val value = (property as KProperty1<Any, *>).get(this)
            map[property.name] = value
        } catch (e: Exception) {
            // Skip properties that can't be accessed
            logger.debug(e) { "Skipping unreadable settings property '${property.name}' on ${this::class.simpleName}" }
        }
    }

    return map
}
