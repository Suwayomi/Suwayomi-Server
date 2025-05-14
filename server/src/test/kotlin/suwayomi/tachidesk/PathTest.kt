package suwayomi.tachidesk

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PathTest {
    @Test
    fun testCanonicalPath() {
        val path = Path("/test/path/")
        assert(path.resolve("child/path").startsWith(path))
        assertFalse(path.resolve("../parent/child/path").normalize().startsWith(path))
    }

    @Test
    fun testParentPath() {
        val path = Path("/test/path/")
        assertEquals(path.resolve("child.txt").parent, path)
        assertEquals(path.resolve("child.txt").normalize().parent, path)
    }
}
