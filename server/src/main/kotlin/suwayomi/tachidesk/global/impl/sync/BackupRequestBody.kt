package suwayomi.tachidesk.global.impl.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.GzipSink
import okio.buffer
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import java.io.IOException

/**
 * Streams a backup as per-manga `MangaChunk` records followed by one trailing record holding
 * the rest of the backup — the wire layout SyncYomi servers (and their restore) expect.
 *
 * `contentLength()` stays -1 (chunked): with gzip the compressed size is unknown until the
 * stream is written anyway.
 */
internal class BackupRequestBody(
    private val backup: Backup,
    private val protoBuf: ProtoBuf,
    private val gzip: Boolean = false,
) : RequestBody() {
    /**
     * The trailing record: the backup with its manga list stripped. Encoded lazily so the
     * full encode only happens when the emptiness check or the actual upload needs it.
     */
    val metaBytes: ByteArray by lazy {
        protoBuf.encodeToByteArray(Backup.serializer(), backup.copy(backupManga = emptyList()))
    }

    override fun contentType(): MediaType? = "application/octet-stream".toMediaType()

    override fun contentLength(): Long = -1L

    override fun writeTo(sink: BufferedSink) {
        try {
            if (gzip) {
                GzipSink(sink).buffer().use { writeRecords(it) }
            } else {
                writeRecords(sink)
            }
        } catch (e: OutOfMemoryError) {
            throw IOException("Not enough memory to sync this library", e)
        }
    }

    private fun writeRecords(sink: BufferedSink) {
        val serializer = MangaChunk.serializer()
        for (manga in backup.backupManga) {
            sink.write(protoBuf.encodeToByteArray(serializer, MangaChunk(manga)))
        }
        sink.write(metaBytes)
    }

    @Serializable
    private class MangaChunk(
        @ProtoNumber(1) val manga: BackupManga,
    )
}
