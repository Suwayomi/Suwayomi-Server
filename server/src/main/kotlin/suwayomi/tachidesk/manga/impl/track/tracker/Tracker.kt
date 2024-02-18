package suwayomi.tachidesk.manga.impl.track.tracker

import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.OkHttpClient
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.io.IOException

abstract class Tracker(val id: Int, val name: String) {
    val trackPreferences = TrackerPreferences
    private val networkService: NetworkHelper by injectLazy()

    open val client: OkHttpClient
        get() = networkService.client

    // Application and remote support for reading dates
    open val supportsReadingDates: Boolean = false

    abstract fun getLogo(): String

    abstract fun getStatusList(): List<Int>

    abstract fun getStatus(status: Int): String?

    abstract fun getReadingStatus(): Int

    abstract fun getRereadingStatus(): Int

    abstract fun getCompletionStatus(): Int

    abstract fun getScoreList(): List<String>

    open fun indexToScore(index: Int): Float {
        return index.toFloat()
    }

    abstract fun displayScore(track: Track): String

    abstract suspend fun update(
        track: Track,
        didReadChapter: Boolean = false,
    ): Track

    abstract suspend fun bind(
        track: Track,
        hasReadChapters: Boolean = false,
    ): Track

    abstract suspend fun search(query: String): List<TrackSearch>

    abstract suspend fun refresh(track: Track): Track

    open fun authUrl(): String? {
        return null
    }

    open suspend fun authCallback(url: String) {}

    abstract suspend fun login(
        username: String,
        password: String,
    )

    open fun logout() {
        trackPreferences.setTrackCredentials(this, "", "")
    }

    open val isLoggedIn: Boolean
        get() {
            return getUsername().isNotEmpty() &&
                getPassword().isNotEmpty()
        }

    fun getUsername() = trackPreferences.getTrackUsername(this) ?: ""

    fun getPassword() = trackPreferences.getTrackPassword(this) ?: ""

    fun saveCredentials(
        username: String,
        password: String,
    ) {
        trackPreferences.setTrackCredentials(this, username, password)
    }

    fun getIfAuthExpired(): Boolean {
        return trackPreferences.trackAuthExpired(this)
    }

    fun setAuthExpired() {
        trackPreferences.setTrackTokenExpired(this)
    }
}

fun String.extractToken(key: String): String? {
    val regex = "$key=(.*?)$".toRegex()
    for (s in this.split("&")) {
        val matchResult = regex.find(s)
        if (matchResult?.groups?.get(1) != null) {
            return matchResult.groups[1]!!.value
        }
    }
    return null
}

class TokenExpired : IOException("Token is expired, re-logging required")

class TokenRefreshFailed : IOException("Token refresh failed")
