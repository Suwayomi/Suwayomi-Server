package suwayomi.tachidesk.manga.impl.backup.proto.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// Scalars need defaults (proto3 peers omit zero values); see BackupManga.
@Serializable
data class BackupHistory(
    @ProtoNumber(1) @EncodeDefault var url: String = "",
    @ProtoNumber(2) @EncodeDefault var lastRead: Long = 0,
)
