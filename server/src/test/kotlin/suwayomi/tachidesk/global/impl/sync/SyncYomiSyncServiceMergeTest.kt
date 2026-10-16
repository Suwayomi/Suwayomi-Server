package suwayomi.tachidesk.global.impl.sync

import android.app.Application
import android.content.Context
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCategory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupChapter
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.test.ApplicationTest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [SyncYomiSyncService.mergeSyncData] — the v1 merge that decides which side
 * of every entry survives and, crucially, which entries are inferred as deleted.
 *
 * `mergeSyncData` is not suspend and only reads the shared "sync" preferences plus user
 * settings, so the merge can be exercised without any HTTP layer.
 */
class SyncYomiSyncServiceMergeTest : ApplicationTest() {
    private val userId = 1

    private val syncPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("sync", Context.MODE_PRIVATE)
    }

    @AfterEach
    fun tearDown() {
        syncPreferences.edit().remove("last_sync_timestamp_$userId").apply()
    }

    private fun manga(
        url: String,
        title: String = url,
        version: Long = 0,
        modifiedAt: Long = 0,
        chapters: List<BackupChapter> = emptyList(),
    ) = BackupManga(
        source = 1,
        url = url,
        title = title,
        version = version,
        lastModifiedAt = modifiedAt,
        chapters = chapters,
    )

    private fun chapter(
        url: String,
        version: Long = 0,
        modifiedAt: Long = 0,
        sourceOrder: Int = 0,
    ) = BackupChapter(url = url, name = url, version = version, lastModifiedAt = modifiedAt, sourceOrder = sourceOrder)

    private fun syncData(vararg mangas: BackupManga) = SyncData(Backup(backupManga = mangas.toList()))

    private fun mergedManga(
        local: SyncData,
        remote: SyncData,
    ) = SyncYomiSyncService.mergeSyncData(userId, local, remote).backup!!.backupManga

    // U2-L: the watermark is epoch-millis while lastModifiedAt is epoch-seconds; a remote
    // change in the same second as the last sync cannot be proven to predate it and must be
    // kept — dropping it is a deletion that propagates to the server and every other device
    @Test
    fun remoteMangaModifiedInTheSameSecondAsTheLastSyncIsKept() {
        // watermark 500ms into epoch-second 1_700_000_000
        syncPreferences.edit().putLong("last_sync_timestamp_$userId", 1_700_000_000_500L).apply()

        val remote = manga("/r", modifiedAt = 1_700_000_000L)
        val merged = mergedManga(syncData(), syncData(remote))

        assertEquals(listOf("/r"), merged.map { it.url })
    }

    @Test
    fun remoteMangaProvablyOlderThanTheLastSyncIsStillInferredAsDeleted() {
        syncPreferences.edit().putLong("last_sync_timestamp_$userId", 1_700_000_000_500L).apply()

        val remote = manga("/r", modifiedAt = 1_699_999_999L)
        val merged = mergedManga(syncData(), syncData(remote))

        assertTrue(merged.isEmpty())
    }

    @Test
    fun localMangaModifiedInTheSameSecondAsTheLastSyncIsKept() {
        syncPreferences.edit().putLong("last_sync_timestamp_$userId", 1_700_000_000_500L).apply()

        val local = manga("/l", modifiedAt = 1_700_000_000L)
        val merged = mergedManga(syncData(local), syncData())

        assertEquals(listOf("/l"), merged.map { it.url })
    }

    @Test
    fun lastSyncTimestampAcceptsLegacySecondsValues() {
        // a seconds-scale watermark (< 1e12) is treated as seconds, not as January 1970 in ms
        syncPreferences.edit().putLong("last_sync_timestamp_$userId", 1_700_000_000L).apply()

        assertEquals(1_700_000_000_000L, SyncYomiSyncService.lastSyncTimestampMs(userId))
        // and a remote change inside that same second is still kept
        val remote = manga("/r", modifiedAt = 1_700_000_000L)
        assertEquals(listOf("/r"), mergedManga(syncData(), syncData(remote)).map { it.url })
    }

    // U2-K: equal versions need a deterministic winner — newer lastModifiedAt first, and a
    // full tie takes remote so all devices converge on the server copy
    @Test
    fun equalVersionsPreferTheNewerLastModifiedAt() {
        val local = manga("/m", title = "local", version = 3, modifiedAt = 100)
        val remote = manga("/m", title = "remote", version = 3, modifiedAt = 200)

        assertEquals("remote", mergedManga(syncData(local), syncData(remote)).single().title)

        // strictly newer local content still wins
        val localNewer = manga("/m", title = "local", version = 3, modifiedAt = 300)
        assertEquals("local", mergedManga(syncData(localNewer), syncData(remote)).single().title)

        // strictly higher local version wins regardless of timestamps
        val localHigherVersion = manga("/m", title = "local", version = 4, modifiedAt = 50)
        assertEquals("local", mergedManga(syncData(localHigherVersion), syncData(remote)).single().title)

        // a full tie (same version AND same lastModifiedAt) converges on remote
        val localTied = manga("/m", title = "local", version = 3, modifiedAt = 200)
        assertEquals("remote", mergedManga(syncData(localTied), syncData(remote)).single().title)
    }

    // U2-K: when local content wins a shared chapter, the position still comes from the
    // remote list — the list sizes are not a proxy for whether renumbering is needed
    @Test
    fun localWinningChapterKeepsTheRemoteSourceOrder() {
        val localChapter = chapter("/m/c", version = 5, sourceOrder = 1)
        val remoteChapter = chapter("/m/c", version = 1, sourceOrder = 9)
        val local = manga("/m", version = 5, chapters = listOf(localChapter))
        val remote = manga("/m", version = 1, chapters = listOf(remoteChapter))

        val merged = mergedManga(syncData(local), syncData(remote)).single()
        val mergedChapter = merged.chapters.single()
        assertEquals(9, mergedChapter.sourceOrder)
        assertEquals(5, mergedChapter.version)
    }

    // U2-M: two local categories may be structurally identical and are still distinct rows;
    // a data-class-equality processed-set would treat matching one as matching both
    @Test
    fun identicalLocalCategoriesAreNotCollapsed() {
        val local =
            Backup(
                backupCategories =
                    listOf(
                        BackupCategory(name = "Same", order = 1, uid = 0, version = 1, lastModifiedAt = 0),
                        BackupCategory(name = "Same", order = 1, uid = 0, version = 1, lastModifiedAt = 0),
                    ),
            )
        val remote =
            Backup(backupCategories = listOf(BackupCategory(name = "Same", order = 1, uid = 0, version = 1, lastModifiedAt = 0)))

        val merged =
            SyncYomiSyncService
                .mergeSyncData(userId, SyncData(local), SyncData(remote))
                .backup!!
                .backupCategories

        assertEquals(2, merged.size)
    }

    // U2-M: a same-named category with a different uid is a different category — the name
    // fallback must not let it claim the other's match
    @Test
    fun sameNameDifferentUidDoesNotMatchByName() {
        val local =
            Backup(
                backupCategories =
                    listOf(
                        BackupCategory(name = "Shared", order = 1, uid = 5, version = 1, lastModifiedAt = 0),
                        BackupCategory(name = "Shared", order = 2, uid = 9, version = 1, lastModifiedAt = 0),
                    ),
            )
        val remote = Backup(backupCategories = listOf(BackupCategory(name = "Shared", order = 1, uid = 9, version = 2, lastModifiedAt = 0)))

        val merged =
            SyncYomiSyncService
                .mergeSyncData(userId, SyncData(local), SyncData(remote))
                .backup!!
                .backupCategories

        // uid 9 matched by uid (remote wins on version); uid 5 stays as a local-only entry
        assertEquals(2, merged.size)
        assertEquals(2, merged.single { it.uid == 9L }.version)
    }
}
