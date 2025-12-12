package ireader.core.source.simple

import ireader.core.source.model.ImageUrl
import ireader.core.source.model.Page
import ireader.core.source.model.Text

/**
 * Chapter content types
 */
sealed class ChapterContent {
    /**
     * Text-based content (novels)
     */
    data class TextContent(val paragraphs: List<String>) : ChapterContent() {
        /**
         * Get full text joined with newlines
         */
        fun fullText(): String = paragraphs.joinToString("\n\n")
        
        /**
         * Get word count
         */
        fun wordCount(): Int = paragraphs.sumOf { it.split(Regex("\\s+")).size }
        
        /**
         * Check if content is empty
         */
        fun isEmpty(): Boolean = paragraphs.isEmpty() || paragraphs.all { it.isBlank() }
    }
    
    /**
     * Image-based content (manga/manhwa)
     */
    data class ImageContent(val urls: List<String>) : ChapterContent() {
        /**
         * Get page count
         */
        fun pageCount(): Int = urls.size
        
        /**
         * Check if content is empty
         */
        fun isEmpty(): Boolean = urls.isEmpty()
    }
    
    /**
     * Mixed content (text + images)
     */
    data class MixedContent(val items: List<ContentItem>) : ChapterContent() {
        /**
         * Get all text items
         */
        fun textItems(): List<String> = items.filterIsInstance<ContentItem.TextItem>().map { it.text }
        
        /**
         * Get all image items
         */
        fun imageItems(): List<String> = items.filterIsInstance<ContentItem.ImageItem>().map { it.url }
        
        /**
         * Check if content is empty
         */
        fun isEmpty(): Boolean = items.isEmpty()
    }
    
    /**
     * Convert to legacy Page list
     */
    fun toPages(): List<Page> = when (this) {
        is TextContent -> paragraphs.map { Text(it) }
        is ImageContent -> urls.map { ImageUrl(it) }
        is MixedContent -> items.map { item ->
            when (item) {
                is ContentItem.TextItem -> Text(item.text)
                is ContentItem.ImageItem -> ImageUrl(item.url)
            }
        }
    }
    
    companion object {
        /**
         * Create text content from paragraphs
         */
        fun text(paragraphs: List<String>): ChapterContent = TextContent(paragraphs)
        
        /**
         * Create text content from single string (split by double newlines)
         */
        fun text(content: String): ChapterContent = TextContent(
            content.split(Regex("\n{2,}")).filter { it.isNotBlank() }
        )
        
        /**
         * Create image content
         */
        fun images(urls: List<String>): ChapterContent = ImageContent(urls)
        
        /**
         * Create mixed content
         */
        fun mixed(items: List<ContentItem>): ChapterContent = MixedContent(items)
        
        /**
         * Create from legacy Page list
         */
        fun fromPages(pages: List<Page>): ChapterContent {
            val textPages = pages.filterIsInstance<Text>()
            val imagePages = pages.filterIsInstance<ImageUrl>()
            
            return when {
                textPages.isNotEmpty() && imagePages.isEmpty() -> 
                    TextContent(textPages.map { it.text })
                imagePages.isNotEmpty() && textPages.isEmpty() -> 
                    ImageContent(imagePages.map { it.url })
                else -> MixedContent(pages.mapNotNull { page ->
                    when (page) {
                        is Text -> ContentItem.TextItem(page.text)
                        is ImageUrl -> ContentItem.ImageItem(page.url)
                        else -> null
                    }
                })
            }
        }
    }
}

/**
 * Content item for mixed content
 */
sealed class ContentItem {
    data class TextItem(val text: String) : ContentItem()
    data class ImageItem(val url: String) : ContentItem()
}
