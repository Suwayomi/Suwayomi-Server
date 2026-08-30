package suwayomi.tachidesk.global.impl.sync

import kotlinx.serialization.protobuf.ProtoBuf
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCategory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupChapter
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncDeltaTest {
    private fun manga(
        url: String,
        modifiedAt: Long,
        vararg chapters: BackupChapter,
    ) = BackupManga(source = 1, url = url, title = url, lastModifiedAt = modifiedAt, chapters = chapters.toList())

    private fun chapter(
        url: String,
        modifiedAt: Long,
    ) = BackupChapter(url = url, name = url, lastModifiedAt = modifiedAt)

    @Test
    fun keepsOnlyWhatChangedSinceTheLastUpload() {
        val mangas =
            listOf(
                manga("/old", 10, chapter("/old/1", 10)),
                manga("/touched", 50, chapter("/touched/1", 10), chapter("/touched/2", 50)),
                manga("/chapter-only", 10, chapter("/chapter-only/1", 60)),
                manga("/boundary", 40),
            )

        val delta = changedSince(mangas, since = 40)

        assertEquals(listOf("/touched", "/chapter-only", "/boundary"), delta.map { it.url })
        assertEquals(listOf("/touched/2"), delta[0].chapters.map { it.url })
        assertEquals(listOf("/chapter-only/1"), delta[1].chapters.map { it.url })
        assertTrue(delta[2].chapters.isEmpty())
    }

    @Test
    fun everythingIsChangedSinceZero() {
        val mangas = listOf(manga("/a", 0, chapter("/a/1", 0)), manga("/b", 5))
        assertEquals(2, changedSince(mangas, since = 0).size)
        assertEquals(1, changedSince(mangas, since = 0)[0].chapters.size)
    }

    // A v2 delta response can carry categories but no manga; that must decode as an empty list.
    @Test
    fun decodesResponseWithoutManga() {
        val bytes =
            ProtoBuf.encodeToByteArray(
                Backup.serializer(),
                Backup(backupCategories = listOf(BackupCategory(name = "Reading", uid = 7))),
            )
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        assertTrue(decoded.backupManga.isEmpty())
        assertEquals("Reading", decoded.backupCategories.single().name)

        val empty = ProtoBuf.decodeFromByteArray(Backup.serializer(), ByteArray(0))
        assertTrue(empty.backupManga.isEmpty())
    }
}
