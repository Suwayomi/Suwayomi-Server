package eu.kanade.tachiyomi.source

/**
 * A source that explicitly doesn't require traffic considerations.
 *
 * This typically applies for self-hosted sources.
 *
 * Note: dormant on the server side — currently no Suwayomi-Server component
 * implements or consumes this marker interface (extension-lib contract only).
 */
interface UnmeteredSource
