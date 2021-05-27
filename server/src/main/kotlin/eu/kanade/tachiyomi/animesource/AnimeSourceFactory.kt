package eu.kanade.tachiyomi.animesource

/**
 * A factory for creating sources at runtime.
 */
interface AnimeSourceFactory {
    /**
     * Create a new copy of the sources
     * @return The created sources
     */
    fun createSources(): List<AnimeSource>
}
