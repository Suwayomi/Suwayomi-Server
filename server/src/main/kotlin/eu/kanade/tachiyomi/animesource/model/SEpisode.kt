package eu.kanade.tachiyomi.animesource.model

// import tachiyomi.animesource.model.EpisodeInfo
import java.io.Serializable

interface SEpisode : Serializable {

    var url: String

    var name: String

    var date_upload: Long

    var episode_number: Float

    var scanlator: String?

    fun copyFrom(other: SEpisode) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        episode_number = other.episode_number
        scanlator = other.scanlator
    }

    companion object {
        fun create(): SEpisode {
            return SEpisodeImpl()
        }
    }
}

// fun SEpisode.toEpisodeInfo(): EpisodeInfo {
//    return EpisodeInfo(
//        dateUpload = this.date_upload,
//        key = this.url,
//        name = this.name,
//        number = this.episode_number,
//        scanlator = this.scanlator ?: ""
//    )
// }
//
// fun EpisodeInfo.toSEpisode(): SEpisode {
//    val episode = this
//    return SEpisode.create().apply {
//        url = episode.key
//        name = episode.name
//        date_upload = episode.dateUpload
//        episode_number = episode.number
//        scanlator = episode.scanlator
//    }
// }
