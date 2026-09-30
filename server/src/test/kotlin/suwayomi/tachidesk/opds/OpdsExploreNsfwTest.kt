package suwayomi.tachidesk.opds

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.model.dataclass.ContentWarning
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.opds.repository.MangaRepository
import suwayomi.tachidesk.opds.repository.NavigationRepository
import suwayomi.tachidesk.server.user.ForbiddenException
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpdsExploreNsfwTest : ApplicationTest() {
    companion object {
        private const val SAFE_SOURCE_ID = 424342L
        private const val NSFW_SOURCE_ID = 424343L
        private const val SAFE_EXT_NAME = "Opds Safe Test Extension"
        private const val NSFW_EXT_NAME = "Opds Nsfw Test Extension"
    }

    private class TestSource(
        sourceId: Long,
    ) : StubSource(sourceId) {
        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)

        override suspend fun getSearchManga(
            page: Int,
            query: String,
            filters: FilterList,
        ): MangasPage = MangasPage(emptyList(), false)

        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)
    }

    @AfterEach
    internal fun tearDown() {
        GetSource.unregisterSource(SAFE_SOURCE_ID)
        GetSource.unregisterSource(NSFW_SOURCE_ID)

        transaction {
            SourceTable.deleteWhere { SourceTable.id inList listOf(SAFE_SOURCE_ID, NSFW_SOURCE_ID) }
            ExtensionTable.deleteWhere { ExtensionTable.name inList listOf(SAFE_EXT_NAME, NSFW_EXT_NAME) }
        }
    }

    private fun insertExtension(
        name: String,
        contentWarning: ContentWarning,
    ): EntityID<Int> =
        transaction {
            ExtensionTable.insertAndGetId {
                it[ExtensionTable.apkName] = "$name.apk"
                it[ExtensionTable.name] = name
                it[ExtensionTable.pkgName] = name
                it[ExtensionTable.versionName] = "1.0"
                it[ExtensionTable.versionCode] = 1
                it[ExtensionTable.lang] = "en"
                it[ExtensionTable.extensionLib] = "1.0"
                it[ExtensionTable.contentWarning] = contentWarning.ordinal
                it[ExtensionTable.isInstalled] = true
            }
        }

    private fun insertSource(
        id: Long,
        name: String,
        contentWarning: ContentWarning,
        extension: EntityID<Int>,
    ) {
        transaction {
            SourceTable.insert {
                it[SourceTable.id] = EntityID(id, SourceTable)
                it[SourceTable.name] = name
                it[SourceTable.lang] = "en"
                it[SourceTable.extension] = extension
                it[SourceTable.contentWarning] = contentWarning.ordinal
            }
        }
    }

    private fun createNsfwAndSafeSources() {
        insertSource(SAFE_SOURCE_ID, SAFE_EXT_NAME, ContentWarning.SAFE, insertExtension(SAFE_EXT_NAME, ContentWarning.SAFE))
        insertSource(NSFW_SOURCE_ID, NSFW_EXT_NAME, ContentWarning.NSFW, insertExtension(NSFW_EXT_NAME, ContentWarning.NSFW))
        GetSource.registerSource(SAFE_SOURCE_ID to TestSource(SAFE_SOURCE_ID))
        GetSource.registerSource(NSFW_SOURCE_ID to TestSource(NSFW_SOURCE_ID))
    }

    @Test
    fun exploreSourcesHidesNsfwSourcesWithoutPermission() {
        createNsfwAndSafeSources()

        val (entries, total) = NavigationRepository.getExploreSources(1, 1, includeNsfw = false)
        val names = entries.map { it.name }.toSet()

        assertTrue("$SAFE_EXT_NAME (EN)" in names, "safe sources should be visible")
        assertTrue("$NSFW_EXT_NAME (EN)" !in names, "NSFW sources should be hidden from users without the NSFW permission")
        // the always-installed local source is the only other entry
        assertEquals(2, entries.size)
        assertEquals(2L, total)
    }

    @Test
    fun exploreSourcesShowsNsfwSourcesWithPermission() {
        createNsfwAndSafeSources()

        val (entries, total) = NavigationRepository.getExploreSources(1, 1, includeNsfw = true)
        val names = entries.map { it.name }.toSet()

        assertTrue("$SAFE_EXT_NAME (EN)" in names, "safe sources should be visible")
        assertTrue("$NSFW_EXT_NAME (EN)" in names, "NSFW sources should be visible to users with the NSFW permission")
        assertEquals(3, entries.size)
        assertEquals(3L, total)
    }

    @Test
    fun mangaBySourceForbiddenForNsfwSourceWithoutPermission() {
        createNsfwAndSafeSources()

        assertFailsWith<ForbiddenException> {
            runBlocking { MangaRepository.getMangaBySource(1, NSFW_SOURCE_ID, 1, "popular", includeNsfw = false) }
        }
    }

    @Test
    fun mangaBySourceAllowedForSafeSourceWithoutPermission() {
        createNsfwAndSafeSources()

        val (entries, hasNextPage) =
            runBlocking { MangaRepository.getMangaBySource(1, SAFE_SOURCE_ID, 1, "popular", includeNsfw = false) }

        assertTrue(entries.isEmpty())
        assertEquals(false, hasNextPage)
    }

    @Test
    fun mangaBySourceAllowedForNsfwSourceWithPermission() {
        createNsfwAndSafeSources()

        val (entries, hasNextPage) =
            runBlocking { MangaRepository.getMangaBySource(1, NSFW_SOURCE_ID, 1, "popular", includeNsfw = true) }

        assertTrue(entries.isEmpty())
        assertEquals(false, hasNextPage)
    }
}
