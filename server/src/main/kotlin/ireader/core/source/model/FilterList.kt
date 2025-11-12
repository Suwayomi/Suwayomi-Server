package ireader.core.source.model

typealias FilterList = List<Filter<*>>
typealias CommandList = List<Command<*>>

/**
 * Find the first instance of a specific filter type in the list
 */
inline fun <reified T : Filter<*>> FilterList.findInstance(): T? = filterIsInstance<T>().firstOrNull()

/**
 * Find the first instance of a specific command type in the list
 */
inline fun <reified T : Command<*>> CommandList.findInstance(): T? = filterIsInstance<T>().firstOrNull()
