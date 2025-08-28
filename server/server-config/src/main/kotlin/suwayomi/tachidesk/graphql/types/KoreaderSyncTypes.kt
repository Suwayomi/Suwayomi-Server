package suwayomi.tachidesk.graphql.types

enum class KoreaderSyncChecksumMethod {
    BINARY,
    FILENAME,
}

enum class KoreaderSyncStrategy {
    PROMPT, // Ask on conflict
    SILENT, // Always use latest
    SEND, // Send changes only
    RECEIVE, // Receive changes only
    DISABLED,
}
