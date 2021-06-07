package eu.kanade.tachiyomi.animesource.model

import android.net.Uri
import eu.kanade.tachiyomi.network.ProgressListener
import rx.subjects.Subject

open class Video(
    val url: String = "",
    val quality: String = "",
    var videoUrl: String? = null,
    @Transient var uri: Uri? = null // Deprecated but can't be deleted due to extensions
) : ProgressListener {

    @Transient
    @Volatile
    var status: Int = 0
        set(value) {
            field = value
            statusSubject?.onNext(value)
            statusCallback?.invoke(this)
        }

    @Transient
    @Volatile
    var progress: Int = 0
        set(value) {
            field = value
            statusCallback?.invoke(this)
        }

    @Transient
    private var statusSubject: Subject<Int, Int>? = null

    @Transient
    private var statusCallback: ((Video) -> Unit)? = null

    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
        progress = if (contentLength > 0) {
            (100 * bytesRead / contentLength).toInt()
        } else {
            -1
        }
    }

    fun setStatusSubject(subject: Subject<Int, Int>?) {
        this.statusSubject = subject
    }

    fun setStatusCallback(f: ((Video) -> Unit)?) {
        statusCallback = f
    }

    companion object {
        const val QUEUE = 0
        const val LOAD_VIDEO = 1
        const val DOWNLOAD_IMAGE = 2
        const val READY = 3
        const val ERROR = 4
    }
}

//fun Video.toVideoUrl(): VideoUrl {
//    return VideoUrl(
//        url = this.videoUrl ?: this.url
//    )
//}
//
//fun VideoUrl.toVideo(index: Int): Video {
//    return Video(
//        videoUrl = this.url
//    )
//}
