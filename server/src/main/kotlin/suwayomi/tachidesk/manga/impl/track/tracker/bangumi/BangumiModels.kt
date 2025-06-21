package suwayomi.tachidesk.manga.impl.track.tracker.bangumi

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch

@Serializable
// Incomplete DTO with only our needed attributes
data class BGMUser(
    val username: String,
)

@Serializable
data class BGMSearchResult(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val data: List<BGMSubject> = emptyList(),
)

@Serializable
// Incomplete DTO with only our needed attributes
data class BGMSubject(
    val id: Long,
    @SerialName("name_cn")
    val nameCn: String,
    val name: String,
    val summary: String?,
    val date: String?, // YYYY-MM-DD
    val images: BGMSubjectImages?,
    val volumes: Long = 0,
    val eps: Long = 0,
    val rating: BGMSubjectRating?,
    val platform: String?,
) {
    fun toTrackSearch(trackId: Int): TrackSearch =
        TrackSearch.create(TrackerManager.BANGUMI).apply {
            remote_id = this@BGMSubject.id
            title = nameCn.ifBlank { name }
            cover_url = images?.common.orEmpty()
            summary =
                if (nameCn.isNotBlank()) {
                    "作品原名：$name" + this@BGMSubject.summary?.let { "\n${it.trim()}" }.orEmpty()
                } else {
                    this@BGMSubject.summary?.trim().orEmpty()
                }
            tracking_url = "https://bangumi.tv/subject/${this@BGMSubject.id}"
            total_chapters = eps.toInt()
            start_date = date ?: ""
        }
}

@Serializable
// Incomplete DTO with only our needed attributes
data class BGMSubjectImages(
    val common: String?,
)

@Serializable
// Incomplete DTO with only our needed attributes
data class BGMSubjectRating(
    val score: Double?,
)

@Serializable
data class BGMOAuth(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("created_at")
    @EncodeDefault
    val createdAt: Long = System.currentTimeMillis() / 1000,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("refresh_token")
    val refreshToken: String?,
    @SerialName("user_id")
    val userId: Long?,
)

// Access token refresh before expired
fun BGMOAuth.isExpired() = (System.currentTimeMillis() / 1000) > (createdAt + expiresIn - 3600)

@Serializable
// Incomplete DTO with only our needed attributes
data class BGMCollectionResponse(
    val rate: Int?,
    val type: Int?,
    @SerialName("ep_status")
    val epStatus: Int? = 0,
    @SerialName("vol_status")
    val volStatus: Int? = 0,
    val private: Boolean = false,
    val subject: BGMSlimSubject? = null,
) {
    fun getStatus(): Int =
        when (type) {
            1 -> Bangumi.PLAN_TO_READ
            2 -> Bangumi.COMPLETED
            3 -> Bangumi.READING
            4 -> Bangumi.ON_HOLD
            5 -> Bangumi.DROPPED
            else -> throw NotImplementedError("Unknown status: $type")
        }
}

@Serializable
// Incomplete DTO with only our needed attributes
data class BGMSlimSubject(
    val volumes: Int?,
    val eps: Int?,
)
