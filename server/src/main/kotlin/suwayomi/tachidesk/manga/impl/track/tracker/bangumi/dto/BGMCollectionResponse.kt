package suwayomi.tachidesk.manga.impl.track.tracker.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.impl.track.tracker.bangumi.Bangumi

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
