package ir.armor.tachidesk.util

import ir.armor.tachidesk.database.dataclass.PagedMangaListDataClass

fun sourceFilters(sourceId: Long) {
    val source = getHttpSource(sourceId)
    // source.getFilterList().toItems()
}

fun sourceSearch(sourceId: Long, searchTerm: String, pageNum: Int): PagedMangaListDataClass {
    val source = getHttpSource(sourceId)
    val searchManga = source.fetchSearchManga(pageNum, searchTerm, source.getFilterList()).toBlocking().first()
    return searchManga.processEntries(sourceId)
}

fun sourceGlobalSearch(searchTerm: String) {
}

data class FilterWrapper(
    val type: String,
    val filter: Any
)

// private fun FilterList.toFilterWrapper(): List<FilterWrapper> {
//    return mapNotNull { filter ->
//        when (filter) {
//            is Filter.Header -> FilterWrapper("Header",filter)
//            is Filter.Separator -> FilterWrapper("Separator",filter)
//            is Filter.CheckBox -> FilterWrapper("CheckBox",filter)
//            is Filter.TriState -> FilterWrapper("TriState",filter)
//            is Filter.Text -> FilterWrapper("Text",filter)
//            is Filter.Select<*> -> FilterWrapper("Select",filter)
//            is Filter.Group<*> -> {
//                val group = GroupItem(filter)
//                val subItems = filter.state.mapNotNull {
//                    when (it) {
//                        is Filter.CheckBox -> FilterWrapper("CheckBox",filter)
//                        is Filter.TriState -> FilterWrapper("TriState",filter)
//                        is Filter.Text -> FilterWrapper("Text",filter)
//                        is Filter.Select<*> -> FilterWrapper("Select",filter)
//                        else -> null
//                    } as? ISectionable<*, *>
//                }
//                subItems.forEach { it.header = group }
//                group.subItems = subItems
//                group
//            }
//            is Filter.Sort -> {
//                val group = SortGroup(filter)
//                val subItems = filter.values.map {
//                    SortItem(it, group)
//                }
//                group.subItems = subItems
//                group
//            }
//        }
//    }
// }
