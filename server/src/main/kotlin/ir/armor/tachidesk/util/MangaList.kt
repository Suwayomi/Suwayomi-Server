package ir.armor.tachidesk.util

import ir.armor.tachidesk.database.dataclass.MangaDataClass
import ir.armor.tachidesk.database.table.MangaStatus

fun getPopularManga(sourceId: String): List<MangaDataClass> {
    val manguasPage = getHttpSource(sourceId.toLong()).fetchPopularManga(1).toBlocking().first()
    return manguasPage.mangas.map {
        MangaDataClass(
                sourceId.toLong(),

                it.url,
                it.title,
                it.thumbnail_url,

                it.initialized,

                it.artist,
                it.author,
                it.description,
                it.genre,
                MangaStatus.values().first { that -> it.status == that.status }.name,
        )
    }
}

fun getLatestManga(sourceId: String): List<MangaDataClass> {
    val manguasPage = getHttpSource(sourceId.toLong()).fetchLatestUpdates(1).toBlocking().first()
    return manguasPage.mangas.map {
        MangaDataClass(
                sourceId.toLong(),

                it.url,
                it.title,
                it.thumbnail_url,

                it.initialized,

                it.artist,
                it.author,
                it.description,
                it.genre,
                MangaStatus.values().first { that -> it.status == that.status }.name,
        )
    }
}