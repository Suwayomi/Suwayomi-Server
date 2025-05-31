package suwayomi.tachidesk.manga.impl.backup.proto.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import xyz.nulldev.androidcompat.util.SafePath
import java.text.SimpleDateFormat
import java.util.Date

@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga>,
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    // Bump by 100 to specify this is a 0.x value
    // @ProtoNumber(100) var brokenBackupSources: List<BrokenBackupSource> = emptyList(),
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
    // suwayomi
    @ProtoNumber(9000) var meta: Map<String, String> = emptyMap(),
) {
    fun getSourceMap(): Map<Long, String> =
        backupSources
            .associate { it.sourceId to it.name }

    companion object {
        fun getBasename(name: String = ""): String {
            val namePrefix = "org.suwayomi.tachidesk"
            val namePrefixSeparator = if (name.isNotEmpty()) "." else ""

            return SafePath.buildValidFilename(namePrefix + namePrefixSeparator + name)
        }

        fun getFilename(name: String = ""): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm").format(Date())
            val ext = ".tachibk"

            return getBasename(name + "_$date") + ext
        }
    }
}
