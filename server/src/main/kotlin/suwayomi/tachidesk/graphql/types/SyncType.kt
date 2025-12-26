package suwayomi.tachidesk.graphql.types

import suwayomi.tachidesk.global.impl.sync.SyncManager

enum class StartSyncResult {
    SUCCESS,
    SYNC_IN_PROGRESS,
    SYNC_DISABLED,
}

enum class SyncState {
    STARTED,
    CREATING_BACKUP,
    DOWNLOADING,
    MERGING,
    UPLOADING,
    RESTORING,
    SUCCESS,
    ERROR,
}

data class SyncStatus(
    val state: SyncState,
    val backupRestoreId: String?,
    val completionDuration: Long?,
    val errorMessage: String?,
)

fun SyncManager.SyncState.toStatus(): SyncStatus =
    when (this) {
        SyncManager.SyncState.Started -> {
            SyncStatus(
                state = SyncState.STARTED,
                backupRestoreId = null,
                completionDuration = null,
                errorMessage = null,
            )
        }

        SyncManager.SyncState.CreatingBackup -> {
            SyncStatus(
                state = SyncState.CREATING_BACKUP,
                backupRestoreId = null,
                completionDuration = null,
                errorMessage = null,
            )
        }

        SyncManager.SyncState.Downloading -> {
            SyncStatus(
                state = SyncState.DOWNLOADING,
                backupRestoreId = null,
                completionDuration = null,
                errorMessage = null,
            )
        }

        SyncManager.SyncState.Merging -> {
            SyncStatus(
                state = SyncState.MERGING,
                backupRestoreId = null,
                completionDuration = null,
                errorMessage = null,
            )
        }

        SyncManager.SyncState.Uploading -> {
            SyncStatus(
                state = SyncState.UPLOADING,
                backupRestoreId = null,
                completionDuration = null,
                errorMessage = null,
            )
        }

        is SyncManager.SyncState.Restoring -> {
            SyncStatus(
                state = SyncState.RESTORING,
                backupRestoreId = restoreId,
                completionDuration = null,
                errorMessage = null,
            )
        }

        is SyncManager.SyncState.Success -> {
            SyncStatus(
                state = SyncState.SUCCESS,
                backupRestoreId = null,
                completionDuration = elapsedTime.inWholeMilliseconds,
                errorMessage = null,
            )
        }

        is SyncManager.SyncState.Error -> {
            SyncStatus(
                state = SyncState.ERROR,
                backupRestoreId = null,
                completionDuration = null,
                errorMessage = message,
            )
        }
    }
