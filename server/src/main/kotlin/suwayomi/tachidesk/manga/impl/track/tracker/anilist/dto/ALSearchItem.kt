package suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto

import kotlinx.serialization.Serializable

@Serializable
// The AniList schema marks nearly every field nullable; missing fields used
// to blow up decoding with MissingFieldException -> 500.
data class ALSearchItem(
    val id: Long,
    val title: ALItemTitle = ALItemTitle(),
    val coverImage: ItemCover? = null,
    val description: String? = null,
    val format: String = "",
    val status: String? = null,
    val startDate: ALFuzzyDate = ALFuzzyDate(),
    val chapters: Int? = null,
    val averageScore: Int? = null,
    val staff: ALStaff = ALStaff(),
) {
    fun toALManga(): ALManga =
        ALManga(
            remoteId = id,
            title = title.userPreferred,
            imageUrl = coverImage?.large.orEmpty(),
            description = description,
            format = format.replace("_", "-"),
            publishingStatus = status ?: "",
            startDateFuzzy = startDate.toEpochMilli(),
            totalChapters = chapters ?: 0,
            averageScore = averageScore ?: 0,
            staff = staff,
        )
}

@Serializable
data class ALItemTitle(
    val userPreferred: String = "",
)

@Serializable
data class ItemCover(
    val large: String? = null,
)

@Serializable
data class ALStaff(
    val edges: List<ALEdge> = emptyList(),
)

@Serializable
data class ALEdge(
    val role: String? = null,
    val id: Int? = null,
    val node: ALStaffNode = ALStaffNode(),
)

@Serializable
data class ALStaffNode(
    val name: ALStaffName = ALStaffName(),
)

@Serializable
data class ALStaffName(
    val userPreferred: String? = null,
    val native: String? = null,
    val full: String? = null,
) {
    operator fun invoke(): String? = userPreferred ?: full ?: native
}
