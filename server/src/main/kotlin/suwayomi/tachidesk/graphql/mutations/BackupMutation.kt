package suwayomi.tachidesk.graphql.mutations

import io.javalin.http.UploadedFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.server.TemporaryFileStorage
import suwayomi.tachidesk.graphql.types.BackupRestoreStatus
import suwayomi.tachidesk.graphql.types.toStatus
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupImport
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

class BackupMutation {
    data class RestoreBackupInput(
        val clientMutationId: String? = null,
        val backup: UploadedFile,
    )

    data class RestoreBackupPayload(
        val clientMutationId: String?,
        val id: String,
        val status: BackupRestoreStatus?,
    )

    @RequireAuth
    fun restoreBackup(input: RestoreBackupInput): CompletableFuture<RestoreBackupPayload> {
        val (clientMutationId, backup) = input

        return future {
            val restoreId = ProtoBackupImport.restore(backup.content())

            withTimeout(10.seconds) {
                ProtoBackupImport.notifyFlow.first {
                    ProtoBackupImport.getRestoreState(restoreId) != null
                }
            }

            RestoreBackupPayload(clientMutationId, restoreId, ProtoBackupImport.getRestoreState(restoreId)?.toStatus())
        }
    }

    data class CreateBackupInput(
        val clientMutationId: String? = null,
        val includeChapters: Boolean? = null,
        val includeCategories: Boolean? = null,
        val includeTracking: Boolean? = null,
        val includeHistory: Boolean? = null,
        val includeClientData: Boolean? = null,
        val includeServerSettings: Boolean? = null,
    )

    data class CreateBackupPayload(
        val clientMutationId: String?,
        val url: String,
    )

    @RequireAuth
    fun createBackup(input: CreateBackupInput? = null): CreateBackupPayload {
        val filename = Backup.getFilename()

        val backup =
            ProtoBackupExport.createBackup(
                BackupFlags(
                    includeManga = true,
                    includeCategories = input?.includeCategories ?: true,
                    includeChapters = input?.includeChapters ?: true,
                    includeTracking = input?.includeTracking ?: true,
                    includeHistory = input?.includeHistory ?: true,
                    includeClientData = input?.includeClientData ?: true,
                    includeServerSettings = input?.includeServerSettings ?: true,
                ),
            )

        TemporaryFileStorage.saveFile(filename, backup)

        return CreateBackupPayload(
            clientMutationId = input?.clientMutationId,
            url = "/api/graphql/files/backup/$filename",
        )
    }
}
