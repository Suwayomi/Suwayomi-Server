package suwayomi.tachidesk.manga.impl.backup.proto.models

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import suwayomi.tachidesk.manga.impl.util.lang.JsonObjectEmptyBytes

/**
 * Every scalar field of the backup models needs a default: proto3 peers (the SyncYomi server
 * re-encodes backups with proto3 semantics) and other clients (Mihon, TachiyomiSY) omit
 * zero-valued scalars on the wire, so a required field fails to decode a library that
 * originated elsewhere with "Field 'x' is required ... but it was missing".
 *
 * [EncodeDefault] on the historically required fields keeps emitting their zero values so
 * that Mihon/TachiyomiSY, whose models still require them, can import Suwayomi backups.
 */
@Serializable
data class BackupManga(
    // in 1.x some of these values have different names
    @ProtoNumber(1) @EncodeDefault var source: Long = 0,
    // url is called key in 1.x
    @ProtoNumber(2) @EncodeDefault var url: String = "",
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
    // syncyomi
    @ProtoNumber(106) var lastModifiedAt: Long = 0,
    @ProtoNumber(109) var version: Long = 0,
    @ProtoNumber(111) var initialized: Boolean = false,
    @ProtoNumber(112) var memo: ByteArray = JsonObjectEmptyBytes,
    // suwayomi
    @ProtoNumber(9000) var meta: Map<String, String> = emptyMap(),
) {
    // memo is a ByteArray: the generated data class equals would compare it by reference,
    // so equality is hand-rolled with contentEquals
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupManga) return false

        return source == other.source &&
            url == other.url &&
            title == other.title &&
            artist == other.artist &&
            author == other.author &&
            description == other.description &&
            genre == other.genre &&
            status == other.status &&
            thumbnailUrl == other.thumbnailUrl &&
            dateAdded == other.dateAdded &&
            viewer == other.viewer &&
            chapters == other.chapters &&
            categories == other.categories &&
            tracking == other.tracking &&
            favorite == other.favorite &&
            chapterFlags == other.chapterFlags &&
            viewer_flags == other.viewer_flags &&
            history == other.history &&
            updateStrategy == other.updateStrategy &&
            lastModifiedAt == other.lastModifiedAt &&
            version == other.version &&
            initialized == other.initialized &&
            memo.contentEquals(other.memo) &&
            meta == other.meta
    }

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (author?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + genre.hashCode()
        result = 31 * result + status
        result = 31 * result + (thumbnailUrl?.hashCode() ?: 0)
        result = 31 * result + dateAdded.hashCode()
        result = 31 * result + viewer
        result = 31 * result + chapters.hashCode()
        result = 31 * result + categories.hashCode()
        result = 31 * result + tracking.hashCode()
        result = 31 * result + favorite.hashCode()
        result = 31 * result + chapterFlags
        result = 31 * result + (viewer_flags ?: 0)
        result = 31 * result + history.hashCode()
        result = 31 * result + updateStrategy.hashCode()
        result = 31 * result + lastModifiedAt.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + initialized.hashCode()
        result = 31 * result + memo.contentHashCode()
        result = 31 * result + meta.hashCode()
        return result
    }
}
