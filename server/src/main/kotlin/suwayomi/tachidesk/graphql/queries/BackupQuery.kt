package suwayomi.tachidesk.graphql.queries

import graphql.schema.DataFetchingEnvironment
import io.javalin.http.UploadedFile
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.BackupRestoreStatus
import suwayomi.tachidesk.graphql.types.toStatus
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupImport
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupValidator
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.user.requireUser

class BackupQuery {
    data class ValidateBackupInput(
        val backup: UploadedFile,
    )

    data class ValidateBackupSource(
        val id: Long,
        val name: String,
    )

    data class ValidateBackupResult(
        val missingSources: List<ValidateBackupSource>,
    )

    fun validateBackup(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: ValidateBackupInput,
    ): ValidateBackupResult {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val result = ProtoBackupValidator.validate(input.backup.content)
        return ValidateBackupResult(
            result.missingSourceIds.map { ValidateBackupSource(it.first, it.second) },
        )
    }

    fun restoreStatus(dataFetchingEnvironment: DataFetchingEnvironment): BackupRestoreStatus {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return ProtoBackupImport.backupRestoreState.value.toStatus()
    }
}
