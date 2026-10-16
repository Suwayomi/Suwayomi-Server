package suwayomi.tachidesk.manga.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.test.ApplicationTest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.androidcompat.io.AndroidFiles
import xyz.nulldev.androidcompat.pm.InstalledPackage
import xyz.nulldev.androidcompat.pm.PackageController
import java.io.File
import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the AndroidCompat package-manager surface that [PackageController] exposes to
 * extension install/uninstall: package names are attacker-controlled (manifest/store data),
 * so lookup must reject anything that isn't a well-formed dot-separated package name, and
 * file payloads must be real APKs.
 */
class PackageControllerTest : ApplicationTest() {
    private val packageController: PackageController get() = Injekt.get()
    private val androidFiles: AndroidFiles get() = Injekt.get()

    private fun bogusApk(): File =
        Files
            .createTempDirectory("pkgctl")
            .resolve("bogus.apk")
            .also { it.writeBytes("not an apk".toByteArray()) }
            .toFile()

    @Test
    fun findPackageRejectsTraversalAndMalformedNames() {
        // packageRootOrNull must keep every lookup inside packagesDir — "../" and any name
        // that is not a valid Android package name resolve to "not installed"
        listOf(
            "..",
            "../outside",
            "com.evil/../../escape",
            "com.evil/../x",
            "/abs",
            "1leading.digit",
            "double..dots",
            "with space",
            "trailing.",
            ".leading",
            "with-dash",
        ).forEach {
            assertNull(packageController.findPackage(it), "expected rejection of \"$it\"")
        }
    }

    @Test
    fun findPackageResolvesInstalledDirectory() {
        val packageName = "com.test.installed"
        val root = File(androidFiles.packagesDir, packageName)
        try {
            assertTrue(root.mkdirs() || root.isDirectory)

            val pack = packageController.findPackage(packageName)
            assertNotNull(pack)
            assertEquals(packageName, pack.root.name)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deletePackageRemovesRootAndNotifiesListeners() {
        val packageName = "com.test.deleted"
        val root = File(androidFiles.packagesDir, packageName)
        val notified = mutableListOf<String>()
        val listener: (String) -> Unit = { notified += it }

        packageController.registerUninstallListener(listener)
        try {
            assertTrue(root.mkdirs() || root.isDirectory)

            val pack = packageController.findPackage(packageName)
            assertNotNull(pack)
            packageController.deletePackage(InstalledPackage(root))

            assertFalse(root.exists(), "package root should be deleted")
            assertEquals(listOf(packageName), notified)
        } finally {
            packageController.unregisterUninstallListener(listener)
            root.deleteRecursively()
        }
    }

    @Test
    fun deletePackageFailsForNeverInstalled() {
        assertFailsWith<IllegalStateException> {
            packageController.deletePackage(InstalledPackage(File(androidFiles.packagesDir, "com.test.ghost")))
        }
    }

    @Test
    fun installPackageRejectsNonApkPayload() {
        // a manifest parse failure must propagate — installing a bogus file must not
        // silently produce a package entry
        assertFailsWith<Exception> {
            packageController.installPackage(bogusApk(), allowReinstall = false)
        }
    }
}
