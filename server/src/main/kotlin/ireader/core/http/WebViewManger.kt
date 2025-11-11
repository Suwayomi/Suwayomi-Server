package ireader.core.http

expect class WebViewManger {

    var isInit :Boolean
    var userAgent : String
    var selector: String?
    var html: org.jsoup.nodes.Document
    var webUrl: String?
    var inProgress: Boolean

    fun init() : Any

    fun update()

    fun destroy()
}