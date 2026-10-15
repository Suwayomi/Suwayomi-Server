package suwayomi.tachidesk.manga.impl.util

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object KoreaderHelper {
    private val logger = KotlinLogging.logger {}

    // Helper function to convert ByteArray to Hex String
    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    private const val SAMPLE_STEP = 1024L
    private const val SAMPLE_SIZE = 1024

    /**
     * Sampling offset for round [i] of KOReader's `util.partialMD5`
     * (https://github.com/koreader/koreader/blob/master/frontend/util.lua#L1096):
     *
     * ```lua
     * for i = -1, 10 do
     *     file:seek("set", lshift(1024, 2 * i))
     *     ...
     * end
     * ```
     *
     * LuaJIT's `bit.lshift` uses only the lower 5 bits of the shift count — a
     * negative count does NOT reverse the direction (that only applies to
     * `bit.rol`/`bit.ror`): `lshift(1024, -2)` is `lshift(1024, 30)`, a 32-bit
     * shift that wraps to 0. The JVM masks shift distances the same way
     * (`-2 & 0x3F == 62`, `1024L shl 62 == 0`), so a plain `shl` already mirrors
     * KOReader exactly. The real sample offsets are 0, 1024, 4096, …, 2^30.
     */
    private fun sampleOffset(i: Int): Long = SAMPLE_STEP shl (2 * i)

    /**
     * Hashes the document according to KOReader's partial-MD5 algorithm
     * (see [sampleOffset] for the KOReader semantics being mirrored).
     * Only applies to epub and cbz files.
     *
     * @param file The file object to hash.
     * @return The lowercase MD5 hash or null if hashing is not possible.
     */
    fun hashContents(file: File): String? {
        val extension = file.extension.lowercase()
        if (!file.exists() || (extension != "epub" && extension != "cbz")) {
            return null
        }

        return try {
            file.inputStream().use { fs ->
                val md5 = MessageDigest.getInstance("MD5")
                val buffer = ByteArray(SAMPLE_SIZE)

                // KOReader iterates i = -1..10 inclusive; `until` would drop the final sample.
                for (i in -1..10) {
                    val position = sampleOffset(i)
                    if (position >= file.length()) break // Avoid seeking past the end of small files
                    fs.channel.position(position)
                    val bytesRead = fs.read(buffer, 0, SAMPLE_SIZE)
                    if (bytesRead > 0) {
                        md5.update(buffer, 0, bytesRead)
                    } else {
                        break
                    }
                }
                md5.digest().toHexString()
            }
        } catch (e: Exception) {
            logger.debug(e) { "[KOSYNC HASH] Failed to compute partial md5 for ${file.path}" }
            null
        }
    }

    /**
     * Single-pass stream variant of [hashContents] for callers that only have an
     * [InputStream] (e.g. an on-the-fly created archive); [length] is the total number
     * of readable bytes in [stream].
     *
     * Sampling offsets are strictly increasing (0, 1024, 4096, …, 2^30) and
     * consecutive 1 KiB windows are disjoint. Up to [SAMPLE_SIZE] already-consumed
     * bytes are still retained so a window can be served even if it would overlap
     * the consumed range (e.g. should the upstream offsets ever change).
     */
    fun hashStream(
        stream: InputStream,
        length: Long,
    ): String {
        val md5 = MessageDigest.getInstance("MD5")
        var position = 0L
        // Tail of the previously read sample, kept to serve an overlapping next window.
        var carry = ByteArray(0)

        for (i in -1..10) {
            val offset = sampleOffset(i)
            if (offset >= length) break // Avoid reading past the end of small files

            val window = ByteArray(SAMPLE_SIZE)
            var filled = 0
            if (offset < position) {
                // The window overlaps bytes that were already consumed; serve that part
                // from the retained tail of the previous sample.
                val carryStart = (offset - (position - carry.size)).toInt()
                if (carryStart < 0) break // consumed range no longer retained; should not happen
                val fromCarry = minOf(carry.size - carryStart, SAMPLE_SIZE)
                System.arraycopy(carry, carryStart, window, 0, fromCarry)
                filled = fromCarry
            } else if (offset > position) {
                try {
                    stream.skipNBytes(offset - position)
                } catch (e: EOFException) {
                    break // stream ended earlier than `length` suggested
                }
                position = offset
                carry = ByteArray(0)
            }

            while (filled < SAMPLE_SIZE) {
                val read = stream.read(window, filled, SAMPLE_SIZE - filled)
                if (read <= 0) break
                filled += read
                position += read
            }
            if (filled == 0) break

            md5.update(window, 0, filled)
            carry = window.copyOf(filled)
        }
        return md5.digest().toHexString()
    }
}
