package suwayomi.tachidesk.manga.impl.backup.proto.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import java.text.SimpleDateFormat
import java.util.Date

@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga>,
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    // Bump by 100 to specify this is a 0.x value
    @ProtoNumber(100) var brokenBackupSources: List<BrokenBackupSource> = emptyList(),
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
) {
    fun getSourceMap(): Map<Long, String> {
        return (brokenBackupSources.map { BackupSource(it.name, it.sourceId) } + backupSources)
            .associate { it.sourceId to it.name }
    }

    companion object {
        fun getFilename(): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm").format(Date())
            return "org.suwayomi.tachidesk_$date.tachibk"
        }
    }
}
