package suwayomi.tachidesk.global.impl.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCategory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupChapter
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupHistory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSource
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupTracking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * The SyncYomi server re-encodes backups with proto3 semantics and other clients never send
 * zero-valued scalars, so every backup model field must decode when it is missing on the wire.
 * Conversely Mihon/TachiyomiSY still require some of those fields, so Suwayomi must keep
 * emitting them even when they are zero.
 */
class BackupProtoCompatTest {
    // Mirrors of the backup models with the historically required fields left out entirely,
    // which is what a proto3 encoder produces for zero values.
    @Serializable
    private class P3Tracking(
        @ProtoNumber(5) val title: String,
    )

    @Serializable
    private class P3History(
        @ProtoNumber(3) val readDuration: Long,
    )

    @Serializable
    private class P3Chapter(
        @ProtoNumber(9) val chapterNumber: Float,
    )

    @Serializable
    private class P3Manga(
        @ProtoNumber(3) val title: String,
        @ProtoNumber(16) val chapters: List<P3Chapter>,
        @ProtoNumber(18) val tracking: List<P3Tracking>,
        @ProtoNumber(104) val history: List<P3History>,
    )

    @Serializable
    private class P3Category(
        @ProtoNumber(2) val order: Int,
    )

    @Serializable
    private class P3Source(
        @ProtoNumber(1) val name: String,
    )

    @Serializable
    private class P3Backup(
        @ProtoNumber(1) val backupManga: List<P3Manga>,
        @ProtoNumber(2) val backupCategories: List<P3Category>,
        @ProtoNumber(101) val backupSources: List<P3Source>,
    )

    // Mirrors of the Mihon models, where these fields are still required.
    @Serializable
    private class StrictTracking(
        @ProtoNumber(1) val syncId: Int,
        @ProtoNumber(2) val libraryId: Long,
    )

    @Serializable
    private class StrictHistory(
        @ProtoNumber(1) val url: String,
        @ProtoNumber(2) val lastRead: Long,
    )

    @Serializable
    private class StrictChapter(
        @ProtoNumber(1) val url: String,
        @ProtoNumber(2) val name: String,
    )

    @Serializable
    private class StrictManga(
        @ProtoNumber(1) val source: Long,
        @ProtoNumber(2) val url: String,
        @ProtoNumber(16) val chapters: List<StrictChapter>,
        @ProtoNumber(18) val tracking: List<StrictTracking>,
        @ProtoNumber(104) val history: List<StrictHistory>,
    )

    @Serializable
    private class StrictCategory(
        @ProtoNumber(1) val name: String,
    )

    @Serializable
    private class StrictSource(
        @ProtoNumber(2) val sourceId: Long,
    )

    @Serializable
    private class StrictBackup(
        @ProtoNumber(1) val backupManga: List<StrictManga>,
        @ProtoNumber(2) val backupCategories: List<StrictCategory>,
        @ProtoNumber(101) val backupSources: List<StrictSource>,
    )

    @Test
    fun decodesBackupWithZeroScalarsOmitted() {
        val bytes =
            ProtoBuf.encodeToByteArray(
                P3Backup.serializer(),
                P3Backup(
                    backupManga =
                        listOf(
                            P3Manga(
                                title = "Local",
                                chapters = listOf(P3Chapter(chapterNumber = 1.5F)),
                                tracking = listOf(P3Tracking(title = "tracked")),
                                history = listOf(P3History(readDuration = 42)),
                            ),
                        ),
                    backupCategories = listOf(P3Category(order = 3)),
                    backupSources = listOf(P3Source(name = "Local source")),
                ),
            )

        val backup = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)

        val manga = backup.backupManga.single()
        assertEquals(0L, manga.source)
        assertEquals("", manga.url)
        assertEquals("Local", manga.title)

        val chapter = manga.chapters.single()
        assertEquals("", chapter.url)
        assertEquals("", chapter.name)
        assertEquals(1.5F, chapter.chapterNumber)

        val tracking = manga.tracking.single()
        assertEquals(0, tracking.syncId)
        assertEquals(0L, tracking.libraryId)
        assertEquals("tracked", tracking.title)

        val history = manga.history.single()
        assertEquals("", history.url)
        assertEquals(0L, history.lastRead)

        val category = backup.backupCategories.single()
        assertEquals("", category.name)
        assertEquals(3, category.order)

        assertEquals(0L, backup.backupSources.single().sourceId)
    }

    @Test
    fun decodesEmptyNestedMessages() {
        // A Backup whose single BackupManga carries no fields at all.
        val backup = ProtoBuf.decodeFromByteArray(Backup.serializer(), byteArrayOf(0x0a, 0x00))
        val manga = backup.backupManga.single()
        assertEquals(0L, manga.source)
        assertEquals("", manga.url)
    }

    // Mihon still requires these fields, so their zero values must stay on the wire.
    @Test
    fun keepsEmittingZeroValuedRequiredFields() {
        val bytes =
            ProtoBuf.encodeToByteArray(
                Backup.serializer(),
                Backup(
                    backupManga =
                        listOf(
                            BackupManga(
                                source = 0,
                                url = "",
                                chapters = listOf(BackupChapter(url = "", name = "")),
                                tracking = listOf(BackupTracking(syncId = 0, libraryId = 0)),
                                history = listOf(BackupHistory(url = "", lastRead = 0)),
                            ),
                        ),
                    backupCategories = listOf(BackupCategory(name = "")),
                    backupSources = listOf(BackupSource(sourceId = 0)),
                ),
            )

        val strict = ProtoBuf.decodeFromByteArray(StrictBackup.serializer(), bytes)

        val manga = strict.backupManga.single()
        assertEquals(0L, manga.source)
        assertEquals("", manga.url)
        assertEquals("", manga.chapters.single().url)
        assertEquals(0L, manga.tracking.single().libraryId)
        assertEquals(0L, manga.history.single().lastRead)
        assertEquals("", strict.backupCategories.single().name)
        assertEquals(0L, strict.backupSources.single().sourceId)
    }

    // SyncManager decides whether to restore by comparing the category lists with ==.
    @Test
    fun categoriesCompareStructurally() {
        val local = BackupCategory(name = "Reading", order = 1, uid = 7, version = 2)
        assertEquals(local, BackupCategory(name = "Reading", order = 1, uid = 7, version = 2))

        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), Backup(backupCategories = listOf(local)))
        val remote = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes).backupCategories
        assertNotSame(local, remote.single())
        assertEquals(listOf(local), remote)
    }
}
