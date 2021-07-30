package eu.kanade.tachiyomi.animesource

import androidx.preference.PreferenceScreen

interface ConfigurableAnimeSource : AnimeSource {

    fun setupPreferenceScreen(screen: PreferenceScreen)
}
