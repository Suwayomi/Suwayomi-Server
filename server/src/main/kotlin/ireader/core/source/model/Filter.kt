

package ireader.core.source.model

/**
 * The list of filters a catalog can implement. New types of filters can be implemented through
 * inheritance, but they must inherit from one of the known (base) filters.
 *
 * All catalogs should implement the common filters, because those will also be used by Tachiyomi
 * when performing a global search.
 */
@Suppress("unused")
sealed class Filter<V>(val name: String, val initialValue: V) {

    /**
     * The value of this filter, with the initial value set.
     */
    var value = initialValue

    /**
     * Whether this filter has been updated. If this method returns true, the catalog won't receive
     * this filter when performing a search.
     */
    open fun isDefaultValue(): Boolean {
        return initialValue == value
    }
    
    /**
     * Reset filter to initial value
     */
    fun reset() {
        value = initialValue
    }
    
    /**
     * Check if filter has a valid value
     */
    open fun isValid(): Boolean = true

    /**
     * Base filters.
     */

    class Note(name: String) : Filter<Unit>(name, Unit)

    open class Text(name: String, value: String = "") : Filter<String>(name, value) {
        override fun isValid(): Boolean = value.length <= 200 // Reasonable limit
    }

    open class Check(
        name: String,
        val allowsExclusion: Boolean = false,
        value: Boolean? = null
    ) : Filter<Boolean?>(name, value)

    open class Select(
        name: String,
        val options: Array<String>,
        value: Int = 0
    ) : Filter<Int>(name, value) {
        override fun isValid(): Boolean = value in options.indices
        
        fun getSelectedOption(): String? {
            return options.getOrNull(value)
        }
    }

    open class Group(name: String, val filters: List<Filter<*>>) : Filter<Unit>(name, Unit)

    open class Sort(
        name: String,
        val options: Array<String>,
        value: Selection? = null
    ) : Filter<Sort.Selection?>(name, value) {

        data class Selection(val index: Int, val ascending: Boolean)
    }

    /**
     * Common filters.
     */

    class Title(name: String = "Title") : Text(name)

    class Author(name: String = "Author") : Text(name)

    class Artist(name: String = "Artist") : Text(name)

    class Genre(name: String, allowsExclusion: Boolean = false) : Check(name, allowsExclusion)
}
