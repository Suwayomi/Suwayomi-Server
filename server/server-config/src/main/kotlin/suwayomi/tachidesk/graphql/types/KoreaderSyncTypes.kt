package suwayomi.tachidesk.graphql.types

enum class KoreaderSyncChecksumMethod {
    BINARY,
    FILENAME,
}

/**
 * Defines the resolution strategy for synchronization conflicts.
 * This is applied separately for when the remote progress is newer (Forward)
 * or older (Backward) than the local progress.
 */
enum class KoreaderSyncConflictStrategy {
    /** Ask the client application to prompt the user for a decision. */
    PROMPT,
    /** Always keep the local progress, ignoring the remote version. */
    KEEP_LOCAL,
    /** Always overwrite local progress with the remote version. */
    KEEP_REMOTE,
    /** Do not perform any sync action for this scenario. */
    DISABLED,
}

/**
 * Legacy enum for migrating the old, single sync strategy setting.
 */
@Deprecated("Used for migration purposes only. Use KoreaderSyncConflictStrategy instead.")
enum class KoreaderSyncLegacyStrategy {
    PROMPT, // Ask on conflict
    SILENT, // Always use latest
    SEND, // Send changes only
    RECEIVE, // Receive changes only
    DISABLED,
}
