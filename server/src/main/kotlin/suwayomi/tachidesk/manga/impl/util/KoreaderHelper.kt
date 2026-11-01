package suwayomi.tachidesk.manga.impl.util

import java.io.EOFException
import java.io.InputStream
import java.security.MessageDigest

object KoreaderHelper {
    // Helper function to convert ByteArray to Hex String
    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    /**
     * Hashes the document according to a custom Koreader hashing algorithm.
     * https://github.com/koreader/koreader/blob/master/frontend/util.lua#L1040
     * @param stream The document to hash, which is read and closed.
     * @return The lowercase MD5 hash or null if hashing is not possible.
     */
    fun hashContents(stream: InputStream): String? {
        try {
            stream.use { input ->
                val step = 1024L
                val size = 1024
                val md5 = MessageDigest.getInstance("MD5")
                val buffer = ByteArray(size)
                var position = 0L

                for (i in -1..10) {
                    // KOReader's first shift by -2 wraps to offset 0
                    val sampleStart = if (i < 0) 0L else step shl (2 * i)
                    try {
                        input.skipNBytes(sampleStart - position)
                    } catch (_: EOFException) {
                        break
                    }
                    val bytesRead = input.readNBytes(buffer, 0, size)
                    if (bytesRead == 0) break
                    md5.update(buffer, 0, bytesRead)
                    position = sampleStart + bytesRead
                }
                return md5.digest().toHexString()
            }
        } catch (e: Exception) {
            // TODO: Should we log this error?
            return null
        }
    }
}
