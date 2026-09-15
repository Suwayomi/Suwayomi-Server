package suwayomi.tachidesk.server.database

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.TrackRecordTable
import suwayomi.tachidesk.test.ApplicationTest

class SyncYomiTriggersTest : ApplicationTest() {
    private val userId = 1

    private var mangaRowId: Int = 0
    private var chapterRowId: Int = 0
    private var categoryRowId: Int = 0
    private var mangaUserId: Int = 0
    private var chapterUserId: Int = 0

    @BeforeEach
    fun setUp() {
        transaction {
            mangaRowId =
                MangaTable
                    .insertAndGetId {
                        it[MangaTable.url] = "/m"
                        it[MangaTable.title] = "Manga"
                        it[MangaTable.description] = "d"
                        it[MangaTable.sourceReference] = 1
                    }.value

            chapterRowId =
                ChapterTable
                    .insertAndGetId {
                        it[ChapterTable.url] = "/c"
                        it[ChapterTable.name] = "c1"
                        it[ChapterTable.sourceOrder] = 1
                        it[ChapterTable.manga] = mangaRowId
                    }.value

            categoryRowId =
                CategoryTable
                    .insertAndGetId {
                        it[CategoryTable.name] = "Reading"
                        it[CategoryTable.user] = userId
                    }.value

            // the insert triggers stamp last_modified_at; clear the stamps so tests can tell
            // whether an update stamped
            mangaUserId =
                MangaUserTable
                    .insertAndGetId {
                        it[MangaUserTable.manga] = mangaRowId
                        it[MangaUserTable.user] = userId
                        it[MangaUserTable.inLibrary] = true
                    }.value
            MangaUserTable.update({ MangaUserTable.id eq mangaUserId }) {
                it[MangaUserTable.lastModifiedAt] = 0
            }

            chapterUserId =
                ChapterUserTable
                    .insertAndGetId {
                        it[ChapterUserTable.chapter] = chapterRowId
                        it[ChapterUserTable.user] = userId
                    }.value
            ChapterUserTable.update({ ChapterUserTable.id eq chapterUserId }) {
                it[ChapterUserTable.lastModifiedAt] = 0
            }
        }
    }

    @AfterEach
    fun tearDown() {
        transaction {
            CategoryMangaTable.deleteWhere {
                (CategoryMangaTable.manga eq mangaRowId) and (CategoryMangaTable.user eq userId)
            }
            TrackRecordTable.deleteWhere {
                (TrackRecordTable.mangaId eq mangaRowId) and (TrackRecordTable.user eq userId)
            }
            MangaUserTable.deleteWhere {
                (MangaUserTable.manga eq mangaRowId) and (MangaUserTable.user eq userId)
            }
            ChapterUserTable.deleteWhere {
                (ChapterUserTable.chapter eq chapterRowId) and (ChapterUserTable.user eq userId)
            }
            ChapterTable.deleteWhere { ChapterTable.id eq chapterRowId }
            MangaTable.deleteWhere { MangaTable.id eq mangaRowId }
            CategoryTable.deleteWhere { CategoryTable.id eq categoryRowId }
        }
    }

    private fun mangaUser(): Pair<Long, Long> =
        transaction {
            MangaUserTable
                .selectAll()
                .where { MangaUserTable.id eq mangaUserId }
                .single()
                .let { it[MangaUserTable.version] to it[MangaUserTable.lastModifiedAt] }
        }

    private fun chapterUser(): Pair<Long, Long> =
        transaction {
            ChapterUserTable
                .selectAll()
                .where { ChapterUserTable.id eq chapterUserId }
                .single()
                .let { it[ChapterUserTable.version] to it[ChapterUserTable.lastModifiedAt] }
        }

    @Test
    fun `marking a chapter read bumps only the chapter version`() {
        transaction {
            ChapterUserTable.update({ ChapterUserTable.id eq chapterUserId }) {
                it[ChapterUserTable.isRead] = true
            }
        }

        val (chapterVersion, chapterStamp) = chapterUser()
        assertEquals(1, chapterVersion)
        assertTrue(chapterStamp > 0)
        // chapters merge separately in v2; reads must not decide manga-level merges
        assertEquals(0L to 0L, mangaUser())
    }

    @Test
    fun `a sync restore keeps its version and timestamp`() {
        transaction {
            ChapterUserTable.update({ ChapterUserTable.id eq chapterUserId }) {
                it[ChapterUserTable.isRead] = true
                it[ChapterUserTable.version] = 7
                it[ChapterUserTable.lastModifiedAt] = 1234
                it[ChapterUserTable.isSyncing] = true
            }
        }
        assertEquals(7L to 1234L, chapterUser())
        assertEquals(0L to 0L, mangaUser())

        transaction {
            ChapterUserTable.update({ ChapterUserTable.id eq chapterUserId }) {
                it[ChapterUserTable.isSyncing] = false
            }
        }
        assertEquals(7L to 1234L, chapterUser())
    }

    @Test
    fun `metadata only updates do not stamp`() {
        transaction {
            ChapterTable.update({ ChapterTable.id eq chapterRowId }) {
                it[ChapterTable.name] = "renamed"
            }
            MangaTable.update({ MangaTable.id eq mangaRowId }) {
                it[MangaTable.title] = "renamed"
            }
        }
        assertEquals(0L to 0L, chapterUser())
        assertEquals(0L to 0L, mangaUser())
    }

    @Test
    fun `manga metadata updates bump the user versions`() {
        transaction {
            MangaTable.update({ MangaTable.id eq mangaRowId }) {
                it[MangaTable.description] = "changed"
            }
        }
        assertEquals(1, mangaUser().first)
        assertTrue(mangaUser().second > 0)
    }

    @Test
    fun `category links and track records bump the user version`() {
        transaction {
            CategoryMangaTable.insert {
                it[CategoryMangaTable.category] = categoryRowId
                it[CategoryMangaTable.manga] = mangaRowId
                it[CategoryMangaTable.user] = userId
            }
        }
        assertEquals(1, mangaUser().first)

        transaction {
            CategoryMangaTable.deleteWhere {
                (CategoryMangaTable.manga eq mangaRowId) and (CategoryMangaTable.user eq userId)
            }
        }
        assertEquals(2, mangaUser().first)

        val trackRecordId =
            transaction {
                TrackRecordTable
                    .insertAndGetId {
                        it[TrackRecordTable.mangaId] = mangaRowId
                        it[TrackRecordTable.trackerId] = 2
                        it[TrackRecordTable.remoteId] = 0
                        it[TrackRecordTable.title] = "t"
                        it[TrackRecordTable.lastChapterRead] = 0.0
                        it[TrackRecordTable.totalChapters] = 0
                        it[TrackRecordTable.status] = 1
                        it[TrackRecordTable.score] = 0.0
                        it[TrackRecordTable.remoteUrl] = "/u"
                        it[TrackRecordTable.startDate] = 0
                        it[TrackRecordTable.finishDate] = 0
                        it[TrackRecordTable.user] = userId
                    }.value
            }
        assertEquals(3, mangaUser().first)

        transaction {
            TrackRecordTable.update({ TrackRecordTable.id eq trackRecordId }) {
                it[TrackRecordTable.status] = 2
            }
        }
        assertEquals(4, mangaUser().first)

        transaction {
            TrackRecordTable.deleteWhere { TrackRecordTable.id eq trackRecordId }
        }
        assertEquals(5, mangaUser().first)
        assertNotEquals(0, mangaUser().second)
    }

    @Test
    fun `nothing bumps while the user row is syncing`() {
        transaction {
            MangaUserTable.update({ MangaUserTable.id eq mangaUserId }) {
                it[MangaUserTable.isSyncing] = true
            }

            CategoryMangaTable.insert {
                it[CategoryMangaTable.category] = categoryRowId
                it[CategoryMangaTable.manga] = mangaRowId
                it[CategoryMangaTable.user] = userId
            }
            CategoryMangaTable.deleteWhere {
                (CategoryMangaTable.manga eq mangaRowId) and (CategoryMangaTable.user eq userId)
            }

            TrackRecordTable.insert {
                it[TrackRecordTable.mangaId] = mangaRowId
                it[TrackRecordTable.trackerId] = 2
                it[TrackRecordTable.remoteId] = 0
                it[TrackRecordTable.title] = "t"
                it[TrackRecordTable.lastChapterRead] = 0.0
                it[TrackRecordTable.totalChapters] = 0
                it[TrackRecordTable.status] = 1
                it[TrackRecordTable.score] = 0.0
                it[TrackRecordTable.remoteUrl] = "/u"
                it[TrackRecordTable.startDate] = 0
                it[TrackRecordTable.finishDate] = 0
                it[TrackRecordTable.user] = userId
            }

            MangaUserTable.update({ MangaUserTable.id eq mangaUserId }) {
                it[MangaUserTable.inLibrary] = false
            }
        }
        assertEquals(0L to 0L, mangaUser())
    }
}
