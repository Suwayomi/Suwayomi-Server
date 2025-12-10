package ireader.core.source.dsl

import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList

/**
 * DSL for building search filters easily
 * 
 * Example:
 * ```kotlin
 * override fun getSearchFilters() = filters {
 *     text("Title")
 *     text("Author")
 *     
 *     select("Genre") {
 *         option("All", "")
 *         option("Action", "action")
 *         option("Romance", "romance")
 *     }
 *     
 *     select("Status") {
 *         option("All", "")
 *         option("Ongoing", "ongoing")
 *         option("Completed", "completed")
 *     }
 *     
 *     checkbox("Completed Only")
 *     
 *     sort("Sort By") {
 *         option("Latest", "latest")
 *         option("Popular", "popular")
 *         option("Rating", "rating")
 *     }
 * }
 * ```
 */
fun filters(block: FilterBuilder.() -> Unit): FilterList {
    return FilterBuilder().apply(block).build()
}

class FilterBuilder {
    private val filters = mutableListOf<Filter<*>>()
    
    /**
     * Add a text input filter
     */
    fun text(name: String, defaultValue: String = "") {
        filters.add(Filter.Text(name, defaultValue))
    }
    
    /**
     * Add a title filter (special text filter)
     */
    fun title(name: String = "Title") {
        filters.add(Filter.Title(name))
    }
    
    /**
     * Add an author filter
     */
    fun author(name: String = "Author") {
        filters.add(Filter.Author(name))
    }
    
    /**
     * Add a checkbox filter
     */
    fun checkbox(name: String, checked: Boolean = false) {
        filters.add(Filter.Check(name, allowsExclusion = false, value = if (checked) true else null))
    }
    
    /**
     * Add a tri-state checkbox (include/exclude/ignore)
     */
    fun triStateCheckbox(name: String) {
        filters.add(Filter.Check(name, allowsExclusion = true, value = null))
    }
    
    /**
     * Add a select dropdown
     */
    fun select(name: String, block: SelectBuilder.() -> Unit) {
        val builder = SelectBuilder().apply(block)
        filters.add(Filter.Select(name, builder.options.toTypedArray(), builder.defaultIndex))
    }
    
    /**
     * Add a sort filter
     */
    fun sort(name: String, block: SortBuilder.() -> Unit) {
        val builder = SortBuilder().apply(block)
        filters.add(Filter.Sort(
            name, 
            builder.options.toTypedArray(),
            if (builder.defaultIndex >= 0) 
                Filter.Sort.Selection(builder.defaultIndex, builder.defaultAscending) 
            else null
        ))
    }
    
    /**
     * Add a group of filters
     */
    fun group(name: String, block: FilterBuilder.() -> Unit) {
        val groupFilters = FilterBuilder().apply(block).build()
        filters.add(Filter.Group(name, groupFilters))
    }
    
    /**
     * Add a note/header
     */
    fun note(text: String) {
        filters.add(Filter.Note(text))
    }
    
    /**
     * Add genre checkboxes
     */
    fun genres(name: String = "Genres", vararg genres: String) {
        val genreFilters = genres.map { Filter.Genre(it) }
        filters.add(Filter.Group(name, genreFilters))
    }
    
    /**
     * Add genre checkboxes with exclusion support
     */
    fun genresWithExclusion(name: String = "Genres", vararg genres: String) {
        val genreFilters = genres.map { Filter.Genre(it, allowsExclusion = true) }
        filters.add(Filter.Group(name, genreFilters))
    }
    
    fun build(): FilterList = filters
}

class SelectBuilder {
    internal val options = mutableListOf<String>()
    internal var defaultIndex = 0
    
    fun option(label: String) {
        options.add(label)
    }
    
    fun option(label: String, value: String) {
        // Store label, value can be retrieved by index
        options.add(label)
    }
    
    fun default(index: Int) {
        defaultIndex = index
    }
}

class SortBuilder {
    internal val options = mutableListOf<String>()
    internal var defaultIndex = -1
    internal var defaultAscending = false
    
    fun option(label: String) {
        options.add(label)
    }
    
    fun option(label: String, value: String) {
        options.add(label)
    }
    
    fun default(index: Int, ascending: Boolean = false) {
        defaultIndex = index
        defaultAscending = ascending
    }
}


/**
 * Helper class for building URLs with filter values
 */
class UrlBuilder(private val baseUrl: String) {
    private val params = mutableMapOf<String, String>()
    private var path = ""
    
    fun path(segment: String): UrlBuilder {
        path = if (segment.startsWith("/")) segment else "/$segment"
        return this
    }
    
    fun param(key: String, value: String): UrlBuilder {
        if (value.isNotBlank()) {
            params[key] = value
        }
        return this
    }
    
    fun param(key: String, value: Int): UrlBuilder {
        params[key] = value.toString()
        return this
    }
    
    fun paramIfNotBlank(key: String, value: String?): UrlBuilder {
        if (!value.isNullOrBlank()) {
            params[key] = value
        }
        return this
    }
    
    /**
     * Apply filters to URL params
     */
    fun applyFilters(filters: FilterList, mappings: Map<String, String> = emptyMap()): UrlBuilder {
        filters.forEach { filter ->
            when (filter) {
                is Filter.Text -> {
                    val key = mappings[filter.name] ?: filter.name.lowercase().replace(" ", "_")
                    paramIfNotBlank(key, filter.value)
                }
                is Filter.Select -> {
                    if (filter.value > 0) { // Skip "All" option (usually index 0)
                        val key = mappings[filter.name] ?: filter.name.lowercase().replace(" ", "_")
                        val value = filter.options.getOrNull(filter.value) ?: ""
                        paramIfNotBlank(key, value)
                    }
                }
                is Filter.Check -> {
                    if (filter.value == true) {
                        val key = mappings[filter.name] ?: filter.name.lowercase().replace(" ", "_")
                        param(key, "1")
                    }
                }
                is Filter.Sort -> {
                    filter.value?.let { selection ->
                        val key = mappings[filter.name] ?: "sort"
                        val value = filter.options.getOrNull(selection.index) ?: ""
                        paramIfNotBlank(key, value)
                        if (selection.ascending) {
                            param("order", "asc")
                        }
                    }
                }
                is Filter.Group -> {
                    // Handle genre groups
                    val selectedGenres = filter.filters
                        .filterIsInstance<Filter.Check>()
                        .filter { it.value == true }
                        .map { it.name }
                    
                    if (selectedGenres.isNotEmpty()) {
                        val key = mappings[filter.name] ?: "genres"
                        param(key, selectedGenres.joinToString(","))
                    }
                }
                else -> {}
            }
        }
        return this
    }
    
    fun build(): String {
        val url = StringBuilder(baseUrl.trimEnd('/'))
        
        if (path.isNotBlank()) {
            url.append(path)
        }
        
        if (params.isNotEmpty()) {
            url.append("?")
            url.append(params.entries.joinToString("&") { (key, value) ->
                "${encodeUrl(key)}=${encodeUrl(value)}"
            })
        }
        
        return url.toString()
    }
    
    private fun encodeUrl(value: String): String {
        // Simple URL encoding for common characters
        return value
            .replace(" ", "%20")
            .replace("&", "%26")
            .replace("=", "%3D")
            .replace("+", "%2B")
            .replace("#", "%23")
    }
}

/**
 * Create a URL builder
 */
fun buildUrl(baseUrl: String, block: UrlBuilder.() -> Unit): String {
    return UrlBuilder(baseUrl).apply(block).build()
}

/**
 * Extension to get filter value by name
 */
fun FilterList.getTextValue(name: String): String? {
    return filterIsInstance<Filter.Text>()
        .find { it.name.equals(name, ignoreCase = true) }
        ?.value
        ?.takeIf { it.isNotBlank() }
}

fun FilterList.getSelectValue(name: String): Int? {
    return filterIsInstance<Filter.Select>()
        .find { it.name.equals(name, ignoreCase = true) }
        ?.value
}

fun FilterList.getSelectOption(name: String): String? {
    val filter = filterIsInstance<Filter.Select>()
        .find { it.name.equals(name, ignoreCase = true) }
    return filter?.options?.getOrNull(filter.value)
}

fun FilterList.isChecked(name: String): Boolean {
    return filterIsInstance<Filter.Check>()
        .find { it.name.equals(name, ignoreCase = true) }
        ?.value == true
}

fun FilterList.getSelectedGenres(): List<String> {
    return filterIsInstance<Filter.Group>()
        .flatMap { it.filters }
        .filterIsInstance<Filter.Check>()
        .filter { it.value == true }
        .map { it.name }
}
