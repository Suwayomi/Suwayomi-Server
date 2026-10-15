package suwayomi.tachidesk

import suwayomi.tachidesk.manga.impl.extension.requireSafePathComponent
import xyz.nulldev.androidcompat.util.SafePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SafePathTest {
    @Test
    fun invalidCharactersAreReplacedAndEdgesAreTrimmed() {
        val input = " .a:b*c?d<e>f|g\\h/i. "

        val result = SafePath.buildValidFilename(input)

        assertEquals("a_b_c_d_e_f_g_h_i", result)
    }

    @Test
    fun emptyAfterTrimReturnsInvalidMarker() {
        assertEquals("(invalid)", SafePath.buildValidFilename(" ...   . "))
    }

    @Test
    fun resultIsTruncatedTo240Characters() {
        val input = "a".repeat(300)

        val result = SafePath.buildValidFilename(input)

        assertEquals(240, result.length)
        assertEquals("a".repeat(240), result)
    }

    @Test
    fun mixed256CharactersCanExceed255Utf8Bytes() {
        val mixed256 =
            buildString {
                repeat(128) {
                    append('a')
                    append('你')
                }
            }

        val result = SafePath.buildValidFilename(mixed256)

        assertEquals(120, result.length)
        assertEquals(mixed256.take(120), result)
        assertEquals(240, result.toByteArray(Charsets.UTF_8).size)
        assertTrue(result.toByteArray(Charsets.UTF_8).size == 240)
    }

    // The extension install/update/uninstall paths resolve pkgName/versionName/apkName into
    // filesystem components via requireSafePathComponent — the regression net for zip-slip
    // and traversal payloads coming from a malicious manifest or store index
    @Test
    fun requireSafePathComponentAcceptsPlainNames() {
        listOf(
            "eu.kanade.tachiyomi.extension",
            "extension.apk",
            "1.4.2",
            "name_with_underscores-1",
        ).forEach { requireSafePathComponent(it) }
    }

    @Test
    fun requireSafePathComponentRejectsTraversalAndSeparators() {
        listOf(
            "..",
            "../escape.apk",
            "a/../b.apk",
            "a/b.apk",
            "a\\b.apk",
            "/absolute.apk",
            "\\absolute.apk",
        ).forEach {
            assertFailsWith<IllegalArgumentException>("expected rejection of \"$it\"") {
                requireSafePathComponent(it)
            }
        }
    }

    @Test
    fun requireSafePathComponentRejectsNamesThatWouldBeRewritten() {
        // anything the sanitizer would change must be rejected outright (the file would not
        // land where the caller intended): edge dots/spaces, control chars, empty names
        listOf(
            "",
            " ",
            ".",
            " .apk",
            ".hidden.apk",
            "trailing.apk ",
            "trailing.apk.",
            "a:b.apk",
            "a?b.apk",
            "a*b.apk",
            "a|b.apk",
            "a<b.apk",
            "a>b.apk",
            "a\"b.apk",
        ).forEach {
            assertFailsWith<IllegalArgumentException>("expected rejection of \"$it\"") {
                requireSafePathComponent(it)
            }
        }
    }
}
