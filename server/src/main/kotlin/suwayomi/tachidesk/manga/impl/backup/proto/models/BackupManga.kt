package suwayomi.tachidesk.manga.impl.backup.proto.models

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import suwayomi.tachidesk.manga.impl.backup.models.ChapterImpl
import suwayomi.tachidesk.manga.impl.backup.models.MangaImpl
import suwayomi.tachidesk.manga.impl.backup.models.TrackImpl

@Serializable
data class BackupManga(
    // in 1.x some of these values have different names
    @ProtoNumber(1) var source: Long,
    // url is called key in 1.x
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    // thumbnailUrl is called cover in 1.x
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    // @ProtoNumber(10) val customCover: String = "", 1.x value, not used in 0.x
    // @ProtoNumber(11) val lastUpdate: Long = 0, 1.x value, not used in 0.x
    // @ProtoNumber(12) val lastInit: Long = 0, 1.x value, not used in 0.x
    @ProtoNumber(13) var dateAdded: Long = 0,
    @ProtoNumber(14) var viewer: Int = 0, // Replaced by viewer_flags
    // @ProtoNumber(15) val flags: Int = 0, 1.x value, not used in 0.x
    @ProtoNumber(16) var chapters: List<BackupChapter> = emptyList(),
    @ProtoNumber(17) var categories: List<Int> = emptyList(),
    @ProtoNumber(18) var tracking: List<BackupTracking> = emptyList(),
    // Bump by 100 for values that are not saved/implemented in 1.x but are used in 0.x
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var chapterFlags: Int = 0,
    // @ProtoNumber(102) var brokenHistory: List<BrokenBackupHistory> = emptyList(),
    @ProtoNumber(103) var viewer_flags: Int? = null,
    @ProtoNumber(104) var history: List<BackupHistory> = emptyList(),
    @ProtoNumber(105) var updateStrategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
    // suwayomi
    @ProtoNumber(9000) var meta: Map<String, String> = emptyMap(),
) {
    fun getMangaImpl(): MangaImpl =
        MangaImpl().apply {
            url = this@BackupManga.url
            title = this@BackupManga.title
            artist = this@BackupManga.artist
            author = this@BackupManga.author
            description = this@BackupManga.description
            genre = this@BackupManga.genre.joinToString()
            status = this@BackupManga.status
            thumbnail_url = this@BackupManga.thumbnailUrl
            favorite = this@BackupManga.favorite
            source = this@BackupManga.source
            date_added = this@BackupManga.dateAdded
            viewer_flags = this@BackupManga.viewer_flags ?: this@BackupManga.viewer
            chapter_flags = this@BackupManga.chapterFlags
            update_strategy = this@BackupManga.updateStrategy
            meta = this@BackupManga.meta
        }

    fun getChaptersImpl(): List<ChapterImpl> =
        chapters.map {
            it.toChapterImpl()
        }

    fun getTrackingImpl(): List<TrackImpl> =
        tracking.map {
            it.getTrackingImpl()
        }
}
