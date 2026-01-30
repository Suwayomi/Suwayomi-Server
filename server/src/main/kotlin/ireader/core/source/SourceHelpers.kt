package ireader.core.source

import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.MangaInfo
import ireader.core.util.currentTimeMillis

/**
 * Helper utilities for source implementations
 * These are backward-compatible additions that don't break existing sources
 */
object SourceHelpers {
    
    /**
     * Validate a list of manga info objects
     */
    fun validateMangaList(mangas: List<MangaInfo>): List<MangaInfo> {
        return mangas.filter { it.isValid() }
    }
    
    /**
     * Validate a list of chapter info objects
     */
    fun validateChapterList(chapters: List<ChapterInfo>): List<ChapterInfo> {
        return chapters.filter { it.isValid() }
    }
    
    /**
     * Sort chapters by number (descending by default)
     */
    fun sortChaptersByNumber(chapters: List<ChapterInfo>, ascending: Boolean = false): List<ChapterInfo> {
        return if (ascending) {
            chapters.sortedBy { it.number }
        } else {
            chapters.sortedByDescending { it.number }
        }
    }
    
    /**
     * Sort chapters by date (newest first by default)
     */
    fun sortChaptersByDate(chapters: List<ChapterInfo>, ascending: Boolean = false): List<ChapterInfo> {
        return if (ascending) {
            chapters.sortedBy { it.dateUpload }
        } else {
            chapters.sortedByDescending { it.dateUpload }
        }
    }
    
    /**
     * Remove duplicate chapters based on key
     */
    fun removeDuplicateChapters(chapters: List<ChapterInfo>): List<ChapterInfo> {
        return chapters.distinctBy { it.key }
    }
    
    /**
     * Auto-assign chapter numbers if missing
     */
    fun autoAssignChapterNumbers(chapters: List<ChapterInfo>): List<ChapterInfo> {
        return chapters.map { it.withAutoNumber() }
    }
    
    /**
     * Normalize chapter list (remove duplicates, auto-assign numbers, sort)
     */
    fun normalizeChapterList(
        chapters: List<ChapterInfo>,
        sortByNumber: Boolean = true,
        ascending: Boolean = false
    ): List<ChapterInfo> {
        val normalized = removeDuplicateChapters(chapters)
            .map { it.withAutoNumber() }
        
        return if (sortByNumber) {
            sortChaptersByNumber(normalized, ascending)
        } else {
            sortChaptersByDate(normalized, ascending)
        }
    }
    
    /**
     * Clean manga description
     */
    fun cleanDescription(description: String): String {
        return description
            .replace(Regex("\\s+"), " ")
            .replace(Regex("<[^>]*>"), "") // Remove HTML tags
            .trim()
    }
    
    /**
     * Normalize URL by removing trailing slashes and ensuring protocol
     */
    fun normalizeUrl(url: String): String {
        var normalized = url.trim().trimEnd('/')
        
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            if (normalized.startsWith("//")) {
                normalized = "https:$normalized"
            } else {
                normalized = "https://$normalized"
            }
        }
        
        return normalized
    }
    
    /**
     * Build absolute URL from base and relative path
     */
    fun buildAbsoluteUrl(baseUrl: String, path: String): String {
        // If path is already absolute, return it as-is
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }
        
        // If path starts with //, add https:
        if (path.startsWith("//")) {
            return "https:$path"
        }
        
        // If baseUrl is empty, return path as-is
        if (baseUrl.isBlank()) {
            return path
        }
        
        // Normalize the base URL (this will add https:// if missing)
        val normalizedBase = normalizeUrl(baseUrl)
        
        // If path starts with /, append directly to base
        if (path.startsWith("/")) {
            return "$normalizedBase$path"
        }
        
        // For relative paths, ensure proper joining
        // Check if base already ends with / to avoid double slashes
        return if (normalizedBase.endsWith("/")) {
            "$normalizedBase$path"
        } else {
            "$normalizedBase/$path"
        }
    }
    
    /**
     * Extract domain from URL
     */
    fun extractDomain(url: String): String {
        val regex = Regex("""^(?:https?://)?(?:www\.)?([^/]+)""")
        return regex.find(url)?.groupValues?.get(1) ?: url
    }
    
    /**
     * Check if URL is valid
     */
    fun isValidUrl(url: String): Boolean {
        return url.matches(Regex("""^https?://[^\s/$.?#].[^\s]*$"""))
    }
    
    /**
     * Sanitize filename for safe storage
     */
    fun sanitizeFilename(filename: String): String {
        return filename
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(255) // Max filename length
    }
    
    /**
     * Parse date from common formats
     */
    fun parseDate(dateString: String): Long? {
        // This is a simplified version - in production you'd use proper date parsing
        val patterns = mapOf(
            Regex("""(\d{4})-(\d{2})-(\d{2})""") to "yyyy-MM-dd",
            Regex("""(\d{2})/(\d{2})/(\d{4})""") to "MM/dd/yyyy",
            Regex("""(\d{2})\.(\d{2})\.(\d{4})""") to "dd.MM.yyyy"
        )
        
        for ((pattern, _) in patterns) {
            if (pattern.containsMatchIn(dateString)) {
                // Return current time as placeholder
                // In production, use proper date parsing library
                return currentTimeMillis()
            }
        }
        
        return null
    }
    
    /**
     * Format relative time (e.g., "2 hours ago")
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000} minutes ago"
            diff < 86400_000 -> "${diff / 3600_000} hours ago"
            diff < 604800_000 -> "${diff / 86400_000} days ago"
            diff < 2592000_000 -> "${diff / 604800_000} weeks ago"
            else -> "${diff / 2592000_000} months ago"
        }
    }
}
