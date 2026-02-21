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
    val startDate: Long,
    val endDate: Long? = null,
    val backupRestoreId: String? = null,
    val errorMessage: String? = null,
)

fun SyncManager.SyncState.toStatus(): SyncStatus =
    when (this) {
        is SyncManager.SyncState.Started -> {
            SyncStatus(
                state = SyncState.STARTED,
                startDate = startDate.toEpochMilliseconds(),
            )
        }

        is SyncManager.SyncState.CreatingBackup -> {
            SyncStatus(
                state = SyncState.CREATING_BACKUP,
                startDate = startDate.toEpochMilliseconds(),
            )
        }

        is SyncManager.SyncState.Downloading -> {
            SyncStatus(
                state = SyncState.DOWNLOADING,
                startDate = startDate.toEpochMilliseconds(),
            )
        }

        is SyncManager.SyncState.Merging -> {
            SyncStatus(
                state = SyncState.MERGING,
                startDate = startDate.toEpochMilliseconds(),
            )
        }

        is SyncManager.SyncState.Uploading -> {
            SyncStatus(
                state = SyncState.UPLOADING,
                startDate = startDate.toEpochMilliseconds(),
            )
        }

        is SyncManager.SyncState.Restoring -> {
            SyncStatus(
                state = SyncState.RESTORING,
                startDate = startDate.toEpochMilliseconds(),
                backupRestoreId = restoreId,
            )
        }

        is SyncManager.SyncState.Success -> {
            SyncStatus(
                state = SyncState.SUCCESS,
                startDate = startDate.toEpochMilliseconds(),
                endDate = endDate.toEpochMilliseconds(),
            )
        }

        is SyncManager.SyncState.Error -> {
            SyncStatus(
                state = SyncState.ERROR,
                startDate = startDate.toEpochMilliseconds(),
                endDate = endDate.toEpochMilliseconds(),
                errorMessage = message,
            )
        }
    }
