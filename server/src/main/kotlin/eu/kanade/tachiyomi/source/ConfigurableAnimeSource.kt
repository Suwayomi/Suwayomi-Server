package eu.kanade.tachiyomi.source

import android.support.v7.preference.PreferenceScreen

interface ConfigurableAnimeSource : AnimeSource {

    fun setupPreferenceScreen(screen: PreferenceScreen)
}
