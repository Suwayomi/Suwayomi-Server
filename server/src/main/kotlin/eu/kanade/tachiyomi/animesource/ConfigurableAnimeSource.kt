package eu.kanade.tachiyomi.animesource

import android.support.v7.preference.PreferenceScreen

interface ConfigurableAnimeSource : AnimeSource {

    fun setupPreferenceScreen(screen: PreferenceScreen)
}
