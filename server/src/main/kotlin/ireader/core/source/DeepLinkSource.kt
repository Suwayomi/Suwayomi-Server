

package ireader.core.source

import ireader.core.source.model.DeepLink

interface DeepLinkSource : ireader.core.source.Source {

    fun handleLink(url: String): DeepLink?

    fun findMangaKey(chapterKey: String): String?
}
