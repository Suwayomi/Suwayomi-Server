package suwayomi.tachidesk.graphql.types

import suwayomi.tachidesk.global.impl.sync.SyncManager
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupImport

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
    val backupRestoreStatus: BackupRestoreStatus?,
    val completionDuration: Long?,
    val errorMessage: String?,
)

fun SyncManager.SyncState.toStatus(): SyncStatus =
    when (this) {
        SyncManager.SyncState.Started -> {
            SyncStatus(
                state = SyncState.STARTED,
                backupRestoreStatus = null,
                completionDuration = null,
                errorMessage = null,
            )
        }

        SyncManager.SyncState.CreatingBackup -> {
            SyncStatus(
                state = SyncState.CREATING_BACKUP,
                backupRestoreStatus = null,
                completionDuration = null,
                errorMessage = null,
            )
        }

        SyncManager.SyncState.Downloading -> {
            SyncStatus(
                state = SyncState.DOWNLOADING,
                backupRestoreStatus = null,
                completionDuration = null,
                errorMessage = null,
            )
        }

        SyncManager.SyncState.Merging -> {
            SyncStatus(
                state = SyncState.MERGING,
                backupRestoreStatus = null,
                completionDuration = null,
                errorMessage = null,
            )
        }

        SyncManager.SyncState.Uploading -> {
            SyncStatus(
                state = SyncState.UPLOADING,
                backupRestoreStatus = null,
                completionDuration = null,
                errorMessage = null,
            )
        }

        is SyncManager.SyncState.Restoring -> {
            SyncStatus(
                state = SyncState.RESTORING,
                backupRestoreStatus = ProtoBackupImport.getRestoreState(restoreId)?.toStatus(),
                completionDuration = null,
                errorMessage = null,
            )
        }

        is SyncManager.SyncState.Success -> {
            SyncStatus(
                state = SyncState.SUCCESS,
                backupRestoreStatus = null,
                completionDuration = elapsedTime.inWholeMilliseconds,
                errorMessage = null,
            )
        }

        is SyncManager.SyncState.Error -> {
            SyncStatus(
                state = SyncState.ERROR,
                backupRestoreStatus = null,
                completionDuration = null,
                errorMessage = message,
            )
        }
    }
