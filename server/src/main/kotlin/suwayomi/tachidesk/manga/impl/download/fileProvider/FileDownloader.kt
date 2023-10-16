package suwayomi.tachidesk.manga.impl.download.fileProvider

fun interface FileDownload {
    suspend fun executeDownload(vararg args: Any): Boolean
}

fun interface FileDownload0Args : FileDownload {
    suspend fun execute(): Boolean

    override suspend fun executeDownload(vararg args: Any): Boolean {
        return execute()
    }
}

@Suppress("UNCHECKED_CAST")
fun interface FileDownload3Args<A, B, C> : FileDownload {
    suspend fun execute(
        a: A,
        b: B,
        c: C,
    ): Boolean

    override suspend fun executeDownload(vararg args: Any): Boolean {
        return execute(args[0] as A, args[1] as B, args[2] as C)
    }
}

fun interface FileDownloader {
    fun download(): FileDownload
}
