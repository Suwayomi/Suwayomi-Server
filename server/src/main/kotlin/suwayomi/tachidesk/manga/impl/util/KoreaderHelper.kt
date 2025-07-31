package suwayomi.tachidesk.manga.impl.util

import java.io.File
import java.security.MessageDigest

object KoreaderHelper {
    // Helper function to convert ByteArray to Hex String
    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    /**
     * Hashes the document according to a custom Koreader hashing algorithm.
     * https://github.com/koreader/koreader/blob/master/frontend/util.lua#L1040
     * Only applies to epub and cbz files.
     * @param file The file object to hash.
     * @return The lowercase MD5 hash or null if hashing is not possible.
     */
    fun hashContents(file: File): String? {
        val extension = file.extension.lowercase()
        if (!file.exists() || (extension != "epub" && extension != "cbz")) {
            return null
        }

        try {
            file.inputStream().use { fs ->
                val step = 1024
                val size = 1024
                val md5 = MessageDigest.getInstance("MD5")
                val buffer = ByteArray(size)

                for (i in -1 until 10) {
                    val position = (step shl (2 * i)).toLong()
                    if (position >= file.length()) break // Avoid seeking past the end of small files
                    fs.channel.position(position)
                    val bytesRead = fs.read(buffer, 0, size)
                    if (bytesRead > 0) {
                        md5.update(buffer, 0, bytesRead)
                    } else {
                        break
                    }
                }
                return md5.digest().toHexString().lowercase()
            }
        } catch (e: Exception) {
            // TODO: Should we log this error?
            return null
        }
    }
}
