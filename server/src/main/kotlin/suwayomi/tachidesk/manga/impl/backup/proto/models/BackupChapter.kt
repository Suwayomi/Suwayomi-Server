package suwayomi.tachidesk.manga.impl.backup.proto.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import suwayomi.tachidesk.manga.impl.util.lang.JsonObjectEmptyBytes

// Scalars need defaults (proto3 peers omit zero values); see BackupManga.
@Serializable
data class BackupChapter(
    // in 1.x some of these values have different names
    // url is called key in 1.x
    @ProtoNumber(1) @EncodeDefault var url: String = "",
    @ProtoNumber(2) @EncodeDefault var name: String = "",
    @ProtoNumber(3) var scanlator: String? = null,
    @ProtoNumber(4) var read: Boolean = false,
    @ProtoNumber(5) var bookmark: Boolean = false,
    // lastPageRead is called progress in 1.x
    @ProtoNumber(6) var lastPageRead: Int = 0,
    @ProtoNumber(7) var dateFetch: Long = 0,
    @ProtoNumber(8) var dateUpload: Long = 0,
    // chapterNumber is called number is 1.x
    @ProtoNumber(9) var chapterNumber: Float = 0F,
    @ProtoNumber(10) var sourceOrder: Int = 0,
    // syncyomi
    @ProtoNumber(11) var lastModifiedAt: Long = 0,
    @ProtoNumber(12) var version: Long = 0,
    @ProtoNumber(13) var memo: ByteArray = JsonObjectEmptyBytes,
    // suwayomi
    @ProtoNumber(9000) var meta: Map<String, String> = emptyMap(),
) {
    // memo is a ByteArray: the generated data class equals would compare it by reference,
    // so equality is hand-rolled with contentEquals
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupChapter) return false

        return url == other.url &&
            name == other.name &&
            scanlator == other.scanlator &&
            read == other.read &&
            bookmark == other.bookmark &&
            lastPageRead == other.lastPageRead &&
            dateFetch == other.dateFetch &&
            dateUpload == other.dateUpload &&
            chapterNumber == other.chapterNumber &&
            sourceOrder == other.sourceOrder &&
            lastModifiedAt == other.lastModifiedAt &&
            version == other.version &&
            memo.contentEquals(other.memo) &&
            meta == other.meta
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (scanlator?.hashCode() ?: 0)
        result = 31 * result + read.hashCode()
        result = 31 * result + bookmark.hashCode()
        result = 31 * result + lastPageRead
        result = 31 * result + dateFetch.hashCode()
        result = 31 * result + dateUpload.hashCode()
        result = 31 * result + chapterNumber.hashCode()
        result = 31 * result + sourceOrder
        result = 31 * result + lastModifiedAt.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + memo.contentHashCode()
        result = 31 * result + meta.hashCode()
        return result
    }
}
