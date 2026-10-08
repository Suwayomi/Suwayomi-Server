package suwayomi.tachidesk.manga.impl.track.tracker.mangabaka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaBakaOAuth(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("expires_at")
    val expiresAt: Long,
    @SerialName("token_type")
    val tokenType: String,
    val scope: String,
) {
    fun isExpired(): Boolean = (System.currentTimeMillis() / 1000 + 60) > expiresAt
}
