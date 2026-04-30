package suwayomi.tachidesk.manga.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM at-rest encryption for sensitive ServerConfig values
 * (currently the SMTP password used by Send-to-Kindle).
 *
 * The master key lives at `${dataRoot}/secret.key`, generated on first
 * use with file permissions 600 on POSIX systems. This raises the bar
 * vs storing the password in plaintext inside server.conf or backups,
 * but does NOT protect against an attacker with full filesystem access
 * to the data directory. Document the threat model in the README.
 */
object SecretBox {
    private val logger = KotlinLogging.logger {}
    private val applicationDirs: ApplicationDirs by injectLazy()
    private val random = SecureRandom()

    private const val KEY_ALG = "AES"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val KEY_SIZE = 32 // bytes -> AES-256

    private val keyFile by lazy { File(applicationDirs.dataRoot, "secret.key") }

    @Volatile private var cachedKey: SecretKeySpec? = null

    private fun key(): SecretKeySpec {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            val raw =
                if (keyFile.exists()) {
                    keyFile.readBytes().also {
                        require(it.size == KEY_SIZE) { "Corrupt secret.key (expected $KEY_SIZE bytes, got ${it.size})" }
                    }
                } else {
                    val bytes = ByteArray(KEY_SIZE).also { random.nextBytes(it) }
                    keyFile.parentFile?.mkdirs()
                    keyFile.writeBytes(bytes)
                    runCatching {
                        Files.setPosixFilePermissions(
                            keyFile.toPath(),
                            PosixFilePermissions.fromString("rw-------"),
                        )
                    }.onFailure {
                        // Windows: no POSIX perms, leave as-is.
                        logger.debug { "Could not set 600 perms on secret.key: ${it.message}" }
                    }
                    // Belt and suspenders: try to drop "other" bits via java.io.
                    keyFile.setReadable(false, false)
                    keyFile.setReadable(true, true)
                    keyFile.setWritable(false, false)
                    keyFile.setWritable(true, true)
                    logger.info { "Generated new secret.key for at-rest encryption" }
                    bytes
                }
            val k = SecretKeySpec(raw, KEY_ALG)
            cachedKey = k
            return k
        }
    }

    /** Returns base64(iv || ciphertext+tag). Empty input → empty output. */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(IV_LEN + ct.size)
        System.arraycopy(iv, 0, packed, 0, IV_LEN)
        System.arraycopy(ct, 0, packed, IV_LEN, ct.size)
        return Base64.getEncoder().encodeToString(packed)
    }

    /** Reverses [encrypt]. Empty input → empty output. */
    fun decrypt(b64: String): String {
        if (b64.isEmpty()) return ""
        val packed = Base64.getDecoder().decode(b64)
        require(packed.size > IV_LEN) { "ciphertext too short" }
        val iv = packed.copyOfRange(0, IV_LEN)
        val ct = packed.copyOfRange(IV_LEN, packed.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        val plain = cipher.doFinal(ct)
        return String(plain, Charsets.UTF_8)
    }

    /**
     * Helper to detect already-encrypted strings so re-encrypting them
     * is a no-op. The ciphertext output from [encrypt] is always a
     * base64 string at least 24 chars long, so a quick heuristic is
     * "well-formed base64 and decodes successfully". Used by mutation
     * paths that may receive either plaintext or already-stored
     * ciphertext.
     */
    fun isProbablyEncrypted(value: String): Boolean {
        if (value.length < 24) return false
        return runCatching { decrypt(value) }.isSuccess
    }

    @Suppress("unused")
    private fun ensurePosix(file: File) {
        runCatching {
            val perms = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            Files.setPosixFilePermissions(file.toPath(), perms)
        }
    }
}
