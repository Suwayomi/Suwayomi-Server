package eu.kanade.tachiyomi.source.local.filter

import eu.kanade.tachiyomi.source.model.Filter

sealed class OrderBy(selection: Selection) : Filter.Sort(
    "Order by",
    arrayOf("Title", "Date"),
    selection,
) {
    class Popular() : OrderBy(Selection(0, true))

    class Latest() : OrderBy(Selection(1, false))
}
