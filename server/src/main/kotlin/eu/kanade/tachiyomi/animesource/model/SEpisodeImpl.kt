package eu.kanade.tachiyomi.animesource.model

import eu.kanade.tachiyomi.animesource.model.SEpisode

class SEpisodeImpl : SEpisode {

    override lateinit var url: String

    override lateinit var name: String

    override var date_upload: Long = 0

    override var episode_number: Float = -1f

    override var scanlator: String? = null
}
