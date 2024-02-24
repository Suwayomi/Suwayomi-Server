package suwayomi.tachidesk.manga.impl.track.tracker

import android.app.Application
import android.content.Context
import mu.KotlinLogging
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.Anilist
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TrackerPreferences {
    private val preferenceStore =
        Injekt.get<Application>().getSharedPreferences("tracker", Context.MODE_PRIVATE)
    private val logger = KotlinLogging.logger {}

    fun getTrackUsername(sync: Tracker) = preferenceStore.getString(trackUsername(sync.id), "")

    fun getTrackPassword(sync: Tracker) = preferenceStore.getString(trackPassword(sync.id), "")

    fun trackAuthExpired(tracker: Tracker) =
        preferenceStore.getBoolean(
            trackTokenExpired(tracker.id),
            false,
        )

    fun setTrackCredentials(
        sync: Tracker,
        username: String,
        password: String,
    ) {
        logger.debug { "setTrackCredentials: id=${sync.id} username=$username" }
        preferenceStore.edit()
            .putString(trackUsername(sync.id), username)
            .putString(trackPassword(sync.id), password)
            .putBoolean(trackTokenExpired(sync.id), false)
            .apply()
    }

    fun getTrackToken(sync: Tracker) = preferenceStore.getString(trackToken(sync.id), "")

    fun setTrackToken(
        sync: Tracker,
        token: String?,
    ) {
        logger.debug { "setTrackToken: id=${sync.id} token=$token" }
        if (token == null) {
            preferenceStore.edit()
                .remove(trackToken(sync.id))
                .putBoolean(trackTokenExpired(sync.id), false)
                .apply()
        } else {
            preferenceStore.edit()
                .putString(trackToken(sync.id), token)
                .putBoolean(trackTokenExpired(sync.id), false)
                .apply()
        }
    }

    fun setTrackTokenExpired(sync: Tracker) {
        preferenceStore.edit()
            .putBoolean(trackTokenExpired(sync.id), true)
            .apply()
    }

    fun getScoreType(sync: Tracker) = preferenceStore.getString(scoreType(sync.id), Anilist.POINT_10)

    fun setScoreType(
        sync: Tracker,
        scoreType: String,
    ) = preferenceStore.edit()
        .putString(scoreType(sync.id), scoreType)
        .apply()

    fun autoUpdateTrack() = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    fun trackUsername(trackerId: Int) = "pref_mangasync_username_$trackerId"

    private fun trackPassword(trackerId: Int) = "pref_mangasync_password_$trackerId"

    private fun trackToken(trackerId: Int) = "track_token_$trackerId"

    private fun trackTokenExpired(trackerId: Int) = "track_token_expired_$trackerId"

    private fun scoreType(trackerId: Int) = "score_type_$trackerId"
}
