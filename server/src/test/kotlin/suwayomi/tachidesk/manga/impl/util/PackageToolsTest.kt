package suwayomi.tachidesk.manga.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.pm.PackageInfo
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipOutputStream
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * APK parsing/signature surface of [PackageTools] that does not need a real APK: garbage
 * bytes and unsigned archives must fail closed (null / exception) instead of producing a
 * fabricated signature or package entry.
 */
class PackageToolsTest {
    private fun tempFile(
        name: String,
        bytes: ByteArray,
    ) = Files.createTempDirectory("packagetools").resolve(name).also { it.writeBytes(bytes) }

    @Test
    fun getSignatureHashFailsClosedOnGarbageBytes() {
        val apk = tempFile("garbage.apk", "not an apk".toByteArray())

        // unsigned/unverifiable input -> null, never a fabricated signature hash
        assertNull(PackageTools.getSignatureHash(apk))
    }

    @Test
    fun getSignatureHashFailsClosedOnUnsignedZip() {
        // a syntactically valid zip — real EOCD record, not just a 0-byte
        // file — that is not a signed APK still has no verified signer
        val apk = tempFile("unsigned.apk", emptyZipBytes())
        assertNull(PackageTools.getSignatureHash(apk))
    }

    /** The minimal valid zip: no entries, just the end-of-central-directory record. */
    private fun emptyZipBytes(): ByteArray =
        ByteArrayOutputStream()
            .also { ZipOutputStream(it).close() }
            .toByteArray()

    @Test
    fun getPackageInfoRejectsNonApk() {
        val apk = tempFile("not-an-apk.apk", "definitely not an apk".toByteArray())

        assertFailsWith<Exception> { PackageTools.getPackageInfo(apk) }
    }

    @Test
    fun getSignatureHashOfPackageInfoWithoutSignaturesIsNull() {
        val pkgInfo = PackageInfo()

        assertNull(PackageTools.getSignatureHash(pkgInfo))
    }

    @Test
    fun blockJarUsageWhileRejectsUnlockedJar() =
        runTest {
            val lockedJar = tempFile("locked.jar", byteArrayOf())
            val otherJar = tempFile("other.jar", byteArrayOf())

            // the block must not be able to load a jar whose lock was not acquired — this is
            // what keeps concurrent source loading serialized per jar file
            assertFailsWith<IllegalStateException> {
                PackageTools.blockJarUsageWhile(listOf(lockedJar)) { load ->
                    load(otherJar, "anything")
                }
            }
        }
}
