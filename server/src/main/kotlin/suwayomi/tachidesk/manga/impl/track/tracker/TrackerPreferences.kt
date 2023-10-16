package suwayomi.tachidesk.manga.impl.track.tracker

import android.app.Application
import android.content.Context
import mu.KotlinLogging
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.Anilist
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrackerPreferences {
    private val preferenceStore =
        Injekt.get<Application>().getSharedPreferences("tracker", Context.MODE_PRIVATE)
    private val logger = KotlinLogging.logger {}

    fun getTrackUsername(sync: Tracker) = preferenceStore.getString(trackUsername(sync.id), "")

    fun getTrackPassword(sync: Tracker) = preferenceStore.getString(trackPassword(sync.id), "")

    fun setTrackCredentials(
        sync: Tracker,
        username: String,
        password: String,
    ) {
        logger.debug { "setTrackCredentials: id=${sync.id} username=$username password=$password" }
        preferenceStore.edit()
            .putString(trackUsername(sync.id), username)
            .putString(trackPassword(sync.id), password)
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
                .apply()
        } else {
            preferenceStore.edit()
                .putString(trackToken(sync.id), token)
                .apply()
        }
    }

    fun getScoreType(sync: Tracker) = preferenceStore.getString(scoreType(sync.id), Anilist.POINT_10)

    fun setScoreType(
        sync: Tracker,
        scoreType: String,
    ) = preferenceStore.edit()
        .putString(scoreType(sync.id), scoreType)
        .apply()

    fun autoUpdateTrack() = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    companion object {
        fun trackUsername(syncId: Long) = "pref_mangasync_username_$syncId"

        private fun trackPassword(syncId: Long) = "pref_mangasync_password_$syncId"

        private fun trackToken(syncId: Long) = "track_token_$syncId"

        private fun scoreType(syncId: Long) = "score_type_$syncId"
    }
}
