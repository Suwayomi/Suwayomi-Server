package org.ireader.coreapi.source

/**
 * IReader Source interfaces - mirrors the structure from IReader's source-api
 * These are loaded from IReader extension APKs (converted to JARs)
 */
interface Source {
    val id: Long
    val name: String
    val lang: String
}

interface CatalogSource : Source {
    // Catalog source for browsing
}

interface HttpSource : CatalogSource {
    val baseUrl: String
}

interface SourceFactory {
    fun createSources(): List<Source>
}
