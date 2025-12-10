

package ireader.core.source.model

typealias FilterList = List<Filter<*>>
typealias CommandList = List<Command<*>>

/**
 * Extension functions for FilterList
 */

/**
 * Get all filters that are not at default value
 */
fun FilterList.getActiveFilters(): FilterList {
    return this.filter { !it.isDefaultValue() }
}

/**
 * Reset all filters to default values
 */
fun FilterList.resetAllFilters() {
    this.forEach { it.reset() }
}

/**
 * Check if any filter is active
 */
fun FilterList.hasActiveFilters(): Boolean {
    return this.any { !it.isDefaultValue() }
}

/**
 * Get filter by name
 */
fun FilterList.findByName(name: String): Filter<*>? {
    return this.firstOrNull { it.name == name }
}

/**
 * Extension functions for CommandList
 */

/**
 * Get all commands that are not at default value
 */
fun CommandList.getActiveCommands(): CommandList {
    return this.filter { !it.isDefaultValue() }
}

/**
 * Reset all commands to default values
 */
fun CommandList.resetAllCommands() {
    this.forEach { it.reset() }
}

/**
 * Check if any command is active
 */
fun CommandList.hasActiveCommands(): Boolean {
    return this.any { !it.isDefaultValue() }
}
