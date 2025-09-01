package suwayomi.tachidesk.graphql.types

import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupImport

enum class BackupRestoreState {
    IDLE,
    SUCCESS,
    FAILURE,
    RESTORING_CATEGORIES,
    RESTORING_MANGA,
    RESTORING_META,
    RESTORING_SETTINGS,
}

data class BackupRestoreStatus(
    val state: BackupRestoreState,
    val totalManga: Int,
    val mangaProgress: Int,
)

fun ProtoBackupImport.BackupRestoreState.toStatus(): BackupRestoreStatus =
    when (this) {
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
                mangaProgress = current,
            )
        is ProtoBackupImport.BackupRestoreState.RestoringMeta ->
            BackupRestoreStatus(
                state = BackupRestoreState.RESTORING_META,
                totalManga = totalManga,
                mangaProgress = current,
            )
        is ProtoBackupImport.BackupRestoreState.RestoringSettings ->
            BackupRestoreStatus(
                state = BackupRestoreState.RESTORING_SETTINGS,
                totalManga = totalManga,
                mangaProgress = current,
            )
        is ProtoBackupImport.BackupRestoreState.RestoringManga ->
            BackupRestoreStatus(
                state = BackupRestoreState.RESTORING_MANGA,
                totalManga = totalManga,
                mangaProgress = current,
            )
    }
