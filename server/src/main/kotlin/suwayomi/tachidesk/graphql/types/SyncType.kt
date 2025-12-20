@file:Suppress("ktlint:standard:filename")

package suwayomi.tachidesk.graphql.types

enum class StartSyncResult {
    SUCCESS,
    SYNC_IN_PROGRESS,
    SYNC_DISABLED,
}
