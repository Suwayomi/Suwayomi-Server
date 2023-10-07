package eu.kanade.tachiyomi.source.model

import android.net.Uri
import eu.kanade.tachiyomi.network.ProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

open class Page(
    val index: Int,
    val url: String = "",
    var imageUrl: String? = null,
    // Deprecated but can't be deleted due to extensions
    @Transient var uri: Uri? = null,
) : ProgressListener {
    private val _progress = MutableStateFlow(0)
    val progress = _progress.asStateFlow()

    override fun update(
        bytesRead: Long,
        contentLength: Long,
        done: Boolean,
    ) {
        _progress.value =
            if (contentLength > 0) {
                (100 * bytesRead / contentLength).toInt()
            } else {
                -1
            }
    }

    companion object {
        const val QUEUE = 0
        const val LOAD_PAGE = 1
        const val DOWNLOAD_IMAGE = 2
        const val READY = 3
        const val ERROR = 4
    }
}
