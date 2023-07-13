package suwayomi.tachidesk.manga.impl.download.fileProvider

import java.io.InputStream

@FunctionalInterface
interface RetrieveFile {
    fun executeGetImage(vararg args: Any): Pair<InputStream, String>
}

@FunctionalInterface
interface RetrieveFile0Args : RetrieveFile {
    fun execute(): Pair<InputStream, String>

    override fun executeGetImage(vararg args: Any): Pair<InputStream, String> {
        return execute()
    }
}

@FunctionalInterface
interface RetrieveFile1Args<A> : RetrieveFile {
    fun execute(a: A): Pair<InputStream, String>

    override fun executeGetImage(vararg args: Any): Pair<InputStream, String> {
        return execute(args[0] as A)
    }
}

@FunctionalInterface
interface FileRetriever {
    fun getImage(): RetrieveFile
}
