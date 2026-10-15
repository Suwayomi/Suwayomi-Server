package suwayomi.tachidesk.manga.impl.backup.proto.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import xyz.nulldev.androidcompat.util.SafePath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

/*
 * Proto field-number registry for the backup envelope (nested models keep their own
 * registries inline):
 *   1-99      Mihon/Tachiyomi 1.x wire-compatible fields
 *   100-599   Suwayomi 0.x extensions (bump by 100 for values 1.x lacks)
 *   600-8999  reserved for SyncYomi sync payloads
 *   9000+     Suwayomi-only fields (client metadata, settings)
 */
@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    // Bump by 100 to specify this is a 0.x value
    // @ProtoNumber(100) var brokenBackupSources: List<BrokenBackupSource> = emptyList(),
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
    // suwayomi
    @ProtoNumber(9000) var meta: Map<String, String> = emptyMap(),
    @ProtoNumber(9001) var serverSettings: BackupServerSettings? = null,
    @ProtoNumber(9002) var userSettings: BackupUserSettings? = null,
) {
    fun getSourceMap(): Map<Long, String> =
        backupSources
            .associate { it.sourceId to it.name }

    companion object {
        /**
         * Upper bound for backup uploads (restore/validate); larger payloads are rejected
         * before they get decoded into memory.
         */
        const val MAX_UPLOAD_SIZE: Long = 512L * 1024 * 1024

        fun getBasename(name: String = ""): String {
            val namePrefix = "org.suwayomi.tachidesk"
            val namePrefixSeparator = if (name.isNotEmpty()) "." else ""

            return SafePath.buildValidFilename(namePrefix + namePrefixSeparator + name)
        }

        fun getFilename(name: String = ""): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm").format(Date())
            val ext = ".tachibk"
            // the random segment makes a stored backup's name unguessable (the file is served
            // for download for a day, so a timestamp-only name would be enumerable)
            val randomSuffix = UUID.randomUUID().toString().take(8)

            return getBasename(name + "_$date") + "-$randomSuffix" + ext
        }
    }
}
