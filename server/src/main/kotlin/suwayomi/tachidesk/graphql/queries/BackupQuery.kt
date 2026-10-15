package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import io.javalin.http.UploadedFile
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.BackupRestoreStatus
import suwayomi.tachidesk.graphql.types.toStatus
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupImport
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupValidator
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class BackupQuery {
    data class ValidateBackupInput(
        val backup: UploadedFile,
    )

    data class ValidateBackupSource(
        val id: Long,
        val name: String,
    )

    data class ValidateBackupTracker(
        val name: String,
    )

    data class ValidateBackupResult(
        val missingSources: List<ValidateBackupSource>,
        val missingTrackers: List<ValidateBackupTracker>,
    )

    @RequireAuth
    fun validateBackup(
        @GraphQLIgnore
        userId: Int,
        input: ValidateBackupInput,
    ): CompletableFuture<ValidateBackupResult> =
        future {
            require(input.backup.size() <= Backup.MAX_UPLOAD_SIZE) {
                "Backup file exceeds the maximum size of ${Backup.MAX_UPLOAD_SIZE} bytes"
            }
            val result = ProtoBackupValidator.validate(userId, input.backup.content())
            ValidateBackupResult(
                result.missingSourceIds.map { ValidateBackupSource(it.first, it.second) },
                result.missingTrackers.map { ValidateBackupTracker(it) },
            )
        }

    @RequireAuth
    fun restoreStatus(
        @GraphQLIgnore
        userId: Int,
        id: String,
    ): BackupRestoreStatus? = ProtoBackupImport.getRestoreState(userId, id)?.toStatus()
}
