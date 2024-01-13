package eu.kanade.tachiyomi.source.local.loader

import eu.kanade.tachiyomi.source.model.Page
import java.io.InputStream

class ReaderPage(
    index: Int,
    url: String = "",
    imageUrl: String? = null,
    var stream: (() -> InputStream)? = null,
) : Page(index, url, imageUrl, null)
