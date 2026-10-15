package suwayomi.tachidesk.opds.util

/**
 * Helpers to keep OPDS pagination consistent and safe.
 *
 * OPDS page numbers are 1-based; out-of-range client input (`pageNumber=0`, negatives,
 * or values close to [Int.MAX_VALUE]) must never reach SQL `OFFSET` / [List.subList]
 * or feed link generation, where they would turn into SQL errors, `IndexOutOfBounds`
 * or `Int` overflow.
 */
object OpdsPaging {
    /**
     * Clamps a client-provided page number to the valid domain (>= 1).
     */
    fun sanitizePageNum(pageNum: Int): Int = pageNum.coerceAtLeast(1)

    /**
     * SQL `OFFSET` for a (possibly hostile) 1-based [pageNum]. Multiplication is done in
     * [Long] so `pageNum` near [Int.MAX_VALUE] cannot overflow.
     */
    fun offsetFor(
        pageNum: Int,
        perPage: Int,
    ): Long = (sanitizePageNum(pageNum) - 1).toLong() * perPage.toLong()

    /**
     * In-memory pagination slice over [items] for a (possibly hostile) 1-based [pageNum];
     * always returns a valid slice instead of throwing for out-of-range pages.
     */
    fun <T> pageSlice(
        items: List<T>,
        pageNum: Int,
        perPage: Int,
    ): List<T> {
        val fromIndex = offsetFor(pageNum, perPage).coerceAtMost(items.size.toLong()).toInt()
        val toIndex = minOf(fromIndex + perPage, items.size)
        return items.subList(fromIndex, toIndex)
    }
}
