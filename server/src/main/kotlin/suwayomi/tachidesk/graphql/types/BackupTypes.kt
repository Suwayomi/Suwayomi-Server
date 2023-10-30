package suwayomi.tachidesk.graphql.types

import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupImport

enum class BackupRestoreState {
    IDLE,
    SUCCESS,
    FAILURE,
    RESTORING_CATEGORIES,
    RESTORING_MANGA,
}

data class BackupRestoreStatus(
    val state: BackupRestoreState,
    val totalManga: Int,
    val mangaProgress: Int,
)

fun ProtoBackupImport.BackupRestoreState.toStatus(): BackupRestoreStatus {
    return when (this) {
        ProtoBackupImport.BackupRestoreState.Idle ->
            BackupRestoreStatus(
                state = BackupRestoreState.IDLE,
                totalManga = 0,
                mangaProgress = 0,
            )
        is ProtoBackupImport.BackupRestoreState.Success ->
            BackupRestoreStatus(
                state = BackupRestoreState.SUCCESS,
                totalManga = 0,
                mangaProgress = 0,
            )
        is ProtoBackupImport.BackupRestoreState.Failure ->
            BackupRestoreStatus(
                state = BackupRestoreState.FAILURE,
                totalManga = 0,
                mangaProgress = 0,
            )
        is ProtoBackupImport.BackupRestoreState.RestoringCategories ->
            BackupRestoreStatus(
                state = BackupRestoreState.RESTORING_CATEGORIES,
                totalManga = totalManga,
                mangaProgress = 0,
            )
        is ProtoBackupImport.BackupRestoreState.RestoringManga ->
            BackupRestoreStatus(
                state = BackupRestoreState.RESTORING_MANGA,
                totalManga = totalManga,
                mangaProgress = current,
            )
    }
}
