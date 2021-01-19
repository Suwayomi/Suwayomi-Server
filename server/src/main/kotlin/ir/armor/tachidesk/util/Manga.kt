package ir.armor.tachidesk.util

import eu.kanade.tachiyomi.source.model.SManga
import ir.armor.tachidesk.database.dataclass.ChapterDataClass
import ir.armor.tachidesk.database.dataclass.MangaDataClass
import ir.armor.tachidesk.database.table.MangaStatus
import ir.armor.tachidesk.database.table.MangaTable
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

fun getManga(mangaId: Int): MangaDataClass {
    return transaction {
        var mangaEntry = MangaTable.select { MangaTable.id eq mangaId }.firstOrNull()!!

        return@transaction if (mangaEntry[MangaTable.initialized]) {
            MangaDataClass(
                    mangaId,
                    mangaEntry[MangaTable.sourceReference].value,

                    mangaEntry[MangaTable.url],
                    mangaEntry[MangaTable.title],
                    mangaEntry[MangaTable.thumbnail_url],

                    true,

                    mangaEntry[MangaTable.artist],
                    mangaEntry[MangaTable.author],
                    mangaEntry[MangaTable.description],
                    mangaEntry[MangaTable.genre],
                    MangaStatus.valueOf(mangaEntry[MangaTable.status]).name,
            )
        } else { // initialize manga
            val source = getHttpSource(mangaEntry[MangaTable.sourceReference].value)
            val fetchedManga = source.fetchMangaDetails(
                    SManga.create().apply {
                        url = mangaEntry[MangaTable.url]
                        title = mangaEntry[MangaTable.title]
                    }
            ).toBlocking().first()

            // update database
            MangaTable.update({ MangaTable.id eq mangaId }) {
//            it[url] = fetchedManga.url
//            it[title] = fetchedManga.title
                it[initialized] = true

                it[artist] = fetchedManga.artist
                it[author] = fetchedManga.author
                it[description] = fetchedManga.description
                it[genre] = fetchedManga.genre
                it[status] = fetchedManga.status
                if (fetchedManga.thumbnail_url != null && fetchedManga.thumbnail_url!!.isNotEmpty())
                    it[thumbnail_url] = fetchedManga.thumbnail_url
            }

            mangaEntry = MangaTable.select { MangaTable.id eq mangaId }.firstOrNull()!!

            MangaDataClass(
                    mangaId,
                    mangaEntry[MangaTable.sourceReference].value,


                    mangaEntry[MangaTable.url],
                    mangaEntry[MangaTable.title],
                    mangaEntry[MangaTable.thumbnail_url],

                    true,

                    mangaEntry[MangaTable.artist],
                    mangaEntry[MangaTable.author],
                    mangaEntry[MangaTable.description],
                    mangaEntry[MangaTable.genre],
                    MangaStatus.valueOf(mangaEntry[MangaTable.status]).name,
            )
        }
    }
}

fun getChapterList(mangaId: Int): List<ChapterDataClass> {
    val mangaDetails = getManga(mangaId)
    val source = getHttpSource(mangaDetails.sourceId)

    val chapterList = source.fetchChapterList(
            SManga.create().apply {
                title = mangaDetails.title
                url = mangaDetails.url
            }
    ).toBlocking().first()

    return chapterList.map {
        ChapterDataClass(
                it.url,
                it.name,
                it.date_upload.toString(),
                it.chapter_number,
                it.scanlator,
        )
    }
}