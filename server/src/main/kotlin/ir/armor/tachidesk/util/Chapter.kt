package ir.armor.tachidesk.util

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import ir.armor.tachidesk.database.dataclass.ChapterDataClass
import ir.armor.tachidesk.database.dataclass.PageDataClass
import ir.armor.tachidesk.database.table.ChapterTable
import ir.armor.tachidesk.database.table.MangaTable
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

fun getChapterList(mangaId: Int): List<ChapterDataClass> {
    val mangaDetails = getManga(mangaId)
    val source = getHttpSource(mangaDetails.sourceId)

    val chapterList = source.fetchChapterList(
        SManga.create().apply {
            title = mangaDetails.title
            url = mangaDetails.url
        }
    ).toBlocking().first()

    return transaction {
        chapterList.forEach { fetchedChapter ->
            val chapterEntry = ChapterTable.select { ChapterTable.url eq fetchedChapter.url }.firstOrNull()
            if (chapterEntry == null) {
                ChapterTable.insertAndGetId {
                    it[url] = fetchedChapter.url
                    it[name] = fetchedChapter.name
                    it[date_upload] = fetchedChapter.date_upload
                    it[chapter_number] = fetchedChapter.chapter_number
                    it[scanlator] = fetchedChapter.scanlator

                    it[manga] = mangaId
                }
            }
        }

        return@transaction chapterList.map {
            ChapterDataClass(
                ChapterTable.select { ChapterTable.url eq it.url }.firstOrNull()!![ChapterTable.id].value,
                it.url,
                it.name,
                it.date_upload,
                it.chapter_number,
                it.scanlator,
                mangaId
            )
        }
    }
}

fun getPages(chapterId: Int, mangaId: Int): List<PageDataClass> {
    return transaction {
        val chapterEntry = ChapterTable.select { ChapterTable.id eq chapterId }.firstOrNull()!!
        assert(mangaId == chapterEntry[ChapterTable.manga].value) // sanity check
        val mangaEntry = MangaTable.select { MangaTable.id eq mangaId }.firstOrNull()!!
        val source = getHttpSource(mangaEntry[MangaTable.sourceReference].value)

        val pagesList = source.fetchPageList(
            SChapter.create().apply {
                url = chapterEntry[ChapterTable.url]
                name = chapterEntry[ChapterTable.name]
            }
        ).toBlocking().first()

        return@transaction pagesList.map {
            PageDataClass(
                it.index,
                getTrueImageUrl(it, source)
            )
        }
    }
}

fun getTrueImageUrl(page: Page, source: HttpSource): String {
    return if (page.imageUrl == null) {
        source.fetchImageUrl(page).toBlocking().first()!!
    } else page.imageUrl!!
}
