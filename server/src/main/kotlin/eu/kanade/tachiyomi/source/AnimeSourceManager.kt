package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.source.online.AnimeHttpSource
import rx.Observable

open class AnimeSourceManager(private val context: Context) {

    private val sourcesMap = mutableMapOf<Long, AnimeSource>()

    private val stubSourcesMap = mutableMapOf<Long, StubSource>()

    init {
        createInternalSources().forEach { registerSource(it) }
    }

    open fun get(sourceKey: Long): AnimeSource? {
        return sourcesMap[sourceKey]
    }

    fun getOrStub(sourceKey: Long): AnimeSource {
        return sourcesMap[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            StubSource(sourceKey)
        }
    }

    fun getOnlineSources() = sourcesMap.values.filterIsInstance<AnimeHttpSource>()

    fun getCatalogueSources() = sourcesMap.values.filterIsInstance<AnimeCatalogueSource>()

    internal fun registerSource(source: AnimeSource) {
        if (!sourcesMap.containsKey(source.id)) {
            sourcesMap[source.id] = source
        }
        if (!stubSourcesMap.containsKey(source.id)) {
            stubSourcesMap[source.id] = StubSource(source.id)
        }
    }

    internal fun unregisterSource(source: AnimeSource) {
        sourcesMap.remove(source.id)
    }

    private fun createInternalSources(): List<AnimeSource> = listOf(
//        LocalAnimeSource(context)
    )

    inner class StubSource(override val id: Long) : AnimeSource {

        override val name: String
            get() = id.toString()

        override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun fetchEpisodeLink(episode: SEpisode): Observable<String> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun toString(): String {
            return name
        }

        private fun getSourceNotInstalledException(): Exception {
//            return Exception(context.getString(R.string.source_not_installed, id.toString()))
            return Exception("source not found")
        }
    }
}
