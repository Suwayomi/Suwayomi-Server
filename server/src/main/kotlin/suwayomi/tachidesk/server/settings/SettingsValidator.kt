package suwayomi.tachidesk.server.settings

import suwayomi.tachidesk.graphql.types.Settings

object SettingsValidator {
    fun validate(
        name: String,
        value: Any?,
    ): String? {
        val metadata = SettingsRegistry.get(name) ?: return null

        if (value == null) {
            return "value required"
        }

        val maybeConvertedValue =
            try {
                metadata.typeInfo.convertToInternalType?.invoke(value) ?: value
            } catch (e: Exception) {
                return "cannot convert value (${e.message})"
            }

        return metadata.validator?.invoke(maybeConvertedValue)
    }

    fun validate(
        values: Map<String, Any?>,
        ignoreNull: Boolean?,
    ): List<String> =
        values
            .filterValues { value -> ignoreNull == false || value != null }
            .mapNotNull { (name, value) -> validate(name, value)?.let { error -> "$name: $error" } }

    fun validate(
        settings: Settings,
        ignoreNull: Boolean = false,
    ): List<String> = validate(settings.asMap(), ignoreNull)
}
