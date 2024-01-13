package suwayomi.tachidesk.manga.impl.download.fileProvider

import java.io.InputStream

fun interface RetrieveFile {
    fun executeGetImage(vararg args: Any): Pair<InputStream, String>
}

fun interface RetrieveFile0Args : RetrieveFile {
    fun execute(): Pair<InputStream, String>

    override fun executeGetImage(vararg args: Any): Pair<InputStream, String> {
        return execute()
    }
}

@Suppress("UNCHECKED_CAST")
fun interface RetrieveFile1Args<A> : RetrieveFile {
    fun execute(a: A): Pair<InputStream, String>

    override fun executeGetImage(vararg args: Any): Pair<InputStream, String> {
        return execute(args[0] as A)
    }
}

fun interface FileRetriever {
    fun getImage(): RetrieveFile
}
