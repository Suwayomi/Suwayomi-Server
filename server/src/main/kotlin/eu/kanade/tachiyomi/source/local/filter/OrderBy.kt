package eu.kanade.tachiyomi.source.local.filter

import eu.kanade.tachiyomi.source.model.Filter

sealed class OrderBy(
    selection: Selection,
) : Filter.Sort(
        "Order by",
        SORT_VALUES,
        selection,
    ) {
    class Popular : OrderBy(Selection(TITLE_INDEX, true))

    class Latest : OrderBy(Selection(DATE_INDEX, false))

    companion object {
        const val TITLE_INDEX = 0
        const val DATE_INDEX = 1

        private val SORT_VALUES = arrayOf("Title", "Date")
    }
}
