package suwayomi.tachidesk.manga.impl.backup.proto

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import okio.gzip
import okio.source
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import java.io.IOException
import java.io.InputStream

open class ProtoBackupBase {
    val parser = ProtoBuf

    companion object {
        /**
         * Bound on the *decompressed* payload. The upload cap [Backup.MAX_UPLOAD_SIZE]
         * covers the wire (gzipped) size only; without a second bound a small gzip bomb
         * would expand unboundedly into a byte array. 2GiB is both near the JVM array
         * limit and far above any plausible decompressed backup.
         */
        internal const val MAX_DECOMPRESSED_SIZE: Long = 2L * 1024 * 1024 * 1024

        /**
         * A [Source] that fails once more than [maxBytes] have flowed through — bounds the
         * decompressor's output, not the compressed input.
         */
        private fun Source.bounded(maxBytes: Long): Source =
            object : ForwardingSource(this) {
                private var total = 0L

                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long {
                    val read = super.read(sink, byteCount)
                    if (read > 0) {
                        total += read
                        if (total > maxBytes) {
                            throw IOException(
                                "Backup exceeds the maximum decompressed size of $maxBytes bytes",
                            )
                        }
                    }
                    return read
                }
            }

        /**
         * Reads [sourceStream] fully, inflating when [gzipped], bounded by
         * [MAX_DECOMPRESSED_SIZE] ([maxBytes] is overridable for tests).
         */
        internal fun readBackupBytes(
            sourceStream: InputStream,
            gzipped: Boolean,
            maxBytes: Long = MAX_DECOMPRESSED_SIZE,
        ): ByteArray =
            sourceStream
                .source()
                .let { if (gzipped) it.gzip() else it }
                .bounded(maxBytes)
                .buffer()
                .use { it.readByteArray() }
    }
}
