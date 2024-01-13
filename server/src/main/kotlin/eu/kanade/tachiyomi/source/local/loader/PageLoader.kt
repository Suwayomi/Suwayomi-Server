package eu.kanade.tachiyomi.source.local.loader

// adapted from eu.kanade.tachiyomi.ui.reader.loader.PageLoader
interface PageLoader {
    /**
     * Returns an observable containing the list of pages of a chapter. Only the first emission
     * will be used.
     */
    suspend fun getPages(): List<ReaderPage>

    fun recycle()
}
