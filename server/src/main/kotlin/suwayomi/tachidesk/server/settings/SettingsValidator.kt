package suwayomi.tachidesk.server.settings

import suwayomi.tachidesk.graphql.types.Settings

object SettingsValidator {
    private fun validateSingle(
        name: String,
        value: Any?,
    ): String? {
        val metadata = SettingsRegistry.get(name) ?: return null
        return metadata.validator?.invoke(value)
    }

    private fun validateAll(
        values: Map<String, Any?>,
        ignoreNull: Boolean?,
    ): List<String> =
        values
            .filterValues { value -> ignoreNull == false || value != null }
            .mapNotNull { (name, value) -> validateSingle(name, value)?.let { error -> "$name: $error" } }

    fun validate(
        settings: Settings,
        ignoreNull: Boolean = false,
    ): List<String> = validateAll(settings.asMap(), ignoreNull)
}
