package suwayomi.tachidesk.server.util

import io.github.config4k.registerCustomType

/**
 * Central place for registering custom types for config serialization/deserialization
 * This ensures consistency between runtime config handling and config file generation
 */
object ConfigTypeRegistration {
    private var registered = false

    fun registerCustomTypes() {
        if (registered) return

        registerCustomType(MutableStateFlowType())
        registerCustomType(DurationType())

        registered = true
    }
}
