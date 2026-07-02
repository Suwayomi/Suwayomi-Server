package suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALManga(
    val id: Long,
    val title: String,
    val synopsis: String = "",
    @SerialName("num_chapters")
    val numChapters: Int,
    val mean: Double = -1.0,
    @SerialName("main_picture")
    val covers: MALMangaCovers?,
    val status: String,
    @SerialName("media_type")
    val mediaType: String,
    @SerialName("start_date")
    val startDate: String?,
    @SerialName("alternative_titles")
    val alternativeTitles: MALAlternativeTitles? = null,
)

@Serializable
data class MALMangaCovers(
    val large: String = "",
)

@Serializable
data class MALAlternativeTitles(
    val synonyms: List<String> = emptyList(),
    val en: String = "",
    val ja: String = "",
) {
    fun allTitles(): List<String> = (synonyms + en + ja).filter { it.isNotBlank() }
}
