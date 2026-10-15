package suwayomi.tachidesk.graphql.types

enum class CbzMediaType(
    val mediaType: String,
) {
    MODERN("application/vnd.comicbook+zip"),
    LEGACY("application/x-cbz"),
    COMPATIBLE("application/x-cbr"),
    ;

    companion object {
        fun from(channel: String): CbzMediaType = entries.find { it.name.lowercase() == channel.lowercase() } ?: MODERN
    }
}
