package suwayomi.tachidesk.manga.impl.backup.proto.models

import eu.kanade.tachiyomi.source.Source
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BrokenBackupSource(
    @ProtoNumber(0) var name: String = "",
    @ProtoNumber(1) var sourceId: Long
)

@Serializable
data class BackupSource(
    @ProtoNumber(1) var name: String = "",
    @ProtoNumber(2) var sourceId: Long
) {
    companion object {
        fun copyFrom(source: Source): BackupSource {
            return BackupSource(
                name = source.name,
                sourceId = source.id
            )
        }
    }
}
