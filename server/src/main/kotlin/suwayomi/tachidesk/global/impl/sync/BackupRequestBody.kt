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

internal class BackupRequestBody(
    private val backup: Backup,
    private val protoBuf: ProtoBuf,
    private val gzip: Boolean = false,
) : RequestBody() {
    val metaBytes: ByteArray =
        protoBuf.encodeToByteArray(Backup.serializer(), backup.copy(backupManga = emptyList()))

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
