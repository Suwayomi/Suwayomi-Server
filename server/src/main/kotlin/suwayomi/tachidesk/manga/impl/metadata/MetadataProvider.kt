package suwayomi.tachidesk.manga.impl.metadata

interface MetadataProvider {
    val name: String

    suspend fun search(
        query: String,
        author: String? = null,
    ): List<MetadataSearchResult>

    suspend fun getDetails(externalId: String): MetadataDetails
}

data class MetadataSearchResult(
    val externalId: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val year: Int?,
    val description: String?,
)

data class MetadataDetails(
    val title: String?,
    val author: String?,
    val artist: String?,
    val description: String?,
    val genre: List<String>?,
    val status: Int?,
    val coverUrl: String?,
)
