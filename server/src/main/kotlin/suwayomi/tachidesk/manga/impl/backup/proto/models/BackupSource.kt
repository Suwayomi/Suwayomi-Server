package suwayomi.tachidesk.manga.impl.backup.proto.models

import eu.kanade.tachiyomi.source.Source
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupSource(
    @ProtoNumber(1) var name: String = "",
    @ProtoNumber(2) var sourceId: Long,
    // suwayomi
    @ProtoNumber(9000) var meta: Map<String, String> = emptyMap(),
) {
    companion object {
        fun copyFrom(source: Source): BackupSource =
            BackupSource(
                name = source.name,
                sourceId = source.id,
            )
    }
}
