package suwayomi.tachidesk.manga.impl.util.storage

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileDeletionHelperTest {
    private fun tempDir(): File = createTempDirectory("fileDeletionHelperTest").toFile()

    @Test
    fun emptyParentFoldersAreRemovedUpToTheRoot() {
        val root = tempDir()
        try {
            val nested = File(root, "a/b/c").apply { mkdirs() }
            // the file is already gone — the helper only inspects parent folders
            FileDeletionHelper.cleanupParentFoldersFor(File(nested, "page.jpg"), root.absolutePath)

            assertTrue(root.isDirectory)
            assertFalse(File(root, "a").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun trailingSlashAndDotDotInRootStillStopAtRoot() {
        val root = tempDir()
        try {
            val nested = File(root, "a/b").apply { mkdirs() }

            FileDeletionHelper.cleanupParentFoldersFor(File(nested, "f"), "${root.absolutePath}/a/../")

            assertTrue(root.isDirectory, "the root folder itself must never be deleted")
            assertFalse(File(root, "a").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun nonEmptyFolderStopsTheWalk() {
        val root = tempDir()
        try {
            File(root, "a/b").mkdirs()
            File(root, "a/keep.txt").writeBytes(byteArrayOf(1))

            FileDeletionHelper.cleanupParentFoldersFor(File(root, "a/b/f"), root.absolutePath)

            assertFalse(File(root, "a/b").exists())
            assertTrue(File(root, "a").isDirectory, "a/ still holds keep.txt and must remain")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun fileDirectlyUnderRootDeletesNothing() {
        val root = tempDir()
        try {
            FileDeletionHelper.cleanupParentFoldersFor(File(root, "f"), root.absolutePath)

            assertTrue(root.isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun fileOutsideRootDoesNotClimb() {
        val root = tempDir()
        val other = tempDir()
        try {
            val nested = File(other, "x/y").apply { mkdirs() }

            FileDeletionHelper.cleanupParentFoldersFor(File(nested, "f"), root.absolutePath)

            assertTrue(nested.isDirectory, "folders outside the root must be untouched")
            assertTrue(root.isDirectory)
        } finally {
            root.deleteRecursively()
            other.deleteRecursively()
        }
    }

    @Test
    fun fileWithoutParentFolderReturnsSafely() {
        val root = tempDir()
        try {
            // a bare relative file name has a null parentFile — the helper must not throw
            FileDeletionHelper.cleanupParentFoldersFor(File("no-parent"), root.absolutePath)
            FileDeletionHelper.cleanupParentFoldersFor(File(root, "missing/f"), root.absolutePath)
        } finally {
            root.deleteRecursively()
        }
    }
}
