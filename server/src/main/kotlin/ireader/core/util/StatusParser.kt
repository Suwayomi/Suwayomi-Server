package ireader.core.util

import ireader.core.source.model.MangaInfo

/**
 * Utility object for parsing publication status from various string formats.
 * Normalizes different status representations into standard MangaInfo status codes.
 */
object StatusParser {

    private val ongoingKeywords = mutableSetOf(
        "ongoing", "on going", "on-going", "publishing", "serialization",
        "updating", "active", "continue", "en cours", "em andamento",
        "مستمر", "devam ediyor", "连载中"
    )

    private val completedKeywords = mutableSetOf(
        "completed", "complete", "finished", "end", "ended",
        "terminé", "completo", "concluído", "مكتمل", "tamamlandı", "完结"
    )

    private val hiatusKeywords = mutableSetOf(
        "hiatus", "on hold", "paused", "suspended", "en pause",
        "pausado", "متوقف", "ara verildi"
    )

    private val cancelledKeywords = mutableSetOf(
        "cancelled", "canceled", "dropped", "discontinued",
        "annulé", "cancelado", "ملغي", "iptal edildi"
    )

    /**
     * Parses a status string and returns the corresponding MangaInfo status code.
     *
     * @param statusStr The status string to parse (case-insensitive)
     * @return One of MangaInfo.ONGOING, MangaInfo.COMPLETED, MangaInfo.ON_HIATUS,
     *         MangaInfo.CANCELLED, or MangaInfo.UNKNOWN
     */
    fun parseStatus(statusStr: String): Long {
        val normalized = statusStr.lowercase().trim()

        return when {
            ongoingKeywords.any { it in normalized } -> MangaInfo.ONGOING
            completedKeywords.any { it in normalized } -> MangaInfo.COMPLETED
            hiatusKeywords.any { it in normalized } -> MangaInfo.ON_HIATUS
            cancelledKeywords.any { it in normalized } -> MangaInfo.CANCELLED
            else -> MangaInfo.UNKNOWN
        }
    }

    /**
     * Adds custom keywords for a specific status.
     * Useful for sources with unique status terminology.
     *
     * @param status The MangaInfo status code
     * @param keywords Additional keywords to recognize for this status
     */
    fun addCustomKeywords(status: Long, vararg keywords: String) {
        val targetSet = when (status) {
            MangaInfo.ONGOING -> ongoingKeywords
            MangaInfo.COMPLETED -> completedKeywords
            MangaInfo.ON_HIATUS -> hiatusKeywords
            MangaInfo.CANCELLED -> cancelledKeywords
            else -> return
        }
        targetSet.addAll(keywords.map { it.lowercase() })
    }
}
