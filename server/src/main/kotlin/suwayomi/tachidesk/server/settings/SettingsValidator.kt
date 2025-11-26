package suwayomi.tachidesk.server.settings

import suwayomi.tachidesk.graphql.types.Settings

object SettingsValidator {
    fun validate(
        name: String,
        value: Any?,
    ): String? {
        val metadata = SettingsRegistry.get(name) ?: return null

        val maybeConvertedValue =
            if (value != null) {
                metadata.typeInfo.convertToInternalType?.invoke(value) ?: value
            } else {
                value
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
