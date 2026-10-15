package suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
// Optional fields get defaults — missing keys used to blow up decoding with
// MissingFieldException -> 500.
data class MALManga(
    val id: Long,
    val title: String = "",
    val synopsis: String = "",
    @SerialName("num_chapters")
    val numChapters: Int = 0,
    val mean: Double = 0.0,
    @SerialName("main_picture")
    val covers: MALMangaCovers? = null,
    val status: String = "",
    @SerialName("media_type")
    val mediaType: String = "",
    @SerialName("start_date")
    val startDate: String? = null,
)

@Serializable
data class MALMangaCovers(
    val large: String = "",
)
