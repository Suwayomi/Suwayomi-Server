package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.graphql.types.KoSyncConnectPayload
import suwayomi.tachidesk.graphql.types.LogoutKoSyncAccountPayload
import suwayomi.tachidesk.graphql.types.SettingsType
import suwayomi.tachidesk.graphql.types.SyncConflictInfoType
import suwayomi.tachidesk.manga.impl.sync.KoreaderSyncService
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.requireUser
import java.util.concurrent.CompletableFuture

class KoreaderSyncMutation {
    data class ConnectKoSyncAccountInput(
        val clientMutationId: String? = null,
        val username: String,
        val password: String,
    )

    fun connectKoSyncAccount(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: ConnectKoSyncAccountInput,
    ): CompletableFuture<KoSyncConnectPayload> =
        future {
            dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
            val result = KoreaderSyncService.connect(input.username, input.password)

            KoSyncConnectPayload(
                clientMutationId = input.clientMutationId,
                success = result.success,
                message = result.message,
                username = result.username,
                settings = SettingsType(),
            )
        }

    data class LogoutKoSyncAccountInput(
        val clientMutationId: String? = null,
    )

    fun logoutKoSyncAccount(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: LogoutKoSyncAccountInput,
    ): CompletableFuture<LogoutKoSyncAccountPayload> =
        future {
            dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
            KoreaderSyncService.logout()
            LogoutKoSyncAccountPayload(
                clientMutationId = input.clientMutationId,
                success = true,
                settings = SettingsType(),
            )
        }

    data class PushKoSyncProgressInput(
        val clientMutationId: String? = null,
        val chapterId: Int,
    )

    data class PushKoSyncProgressPayload(
        val clientMutationId: String?,
        val success: Boolean,
        val chapter: ChapterType?,
    )

    fun pushKoSyncProgress(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: PushKoSyncProgressInput,
    ): CompletableFuture<DataFetcherResult<PushKoSyncProgressPayload?>> =
        future {
            asDataFetcherResult {
                dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()

                KoreaderSyncService.pushProgress(input.chapterId)

                val chapter =
                    transaction {
                        ChapterTable
                            .selectAll()
                            .where { ChapterTable.id eq input.chapterId }
                            .firstOrNull()
                            ?.let { ChapterType(it) }
                    }

                PushKoSyncProgressPayload(
                    clientMutationId = input.clientMutationId,
                    success = true,
                    chapter = chapter,
                )
            }
        }

    data class PullKoSyncProgressInput(
        val clientMutationId: String? = null,
        val chapterId: Int,
    )

    data class PullKoSyncProgressPayload(
        val clientMutationId: String?,
        val chapter: ChapterType?,
        val syncConflict: SyncConflictInfoType?,
    )

    fun pullKoSyncProgress(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: PullKoSyncProgressInput,
    ): CompletableFuture<DataFetcherResult<PullKoSyncProgressPayload?>> =
        future {
            asDataFetcherResult {
                dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()

                val syncResult = KoreaderSyncService.checkAndPullProgress(input.chapterId)
                var syncConflictInfo: SyncConflictInfoType? = null

                if (syncResult != null) {
                    if (syncResult.isConflict) {
                        syncConflictInfo =
                            SyncConflictInfoType(
                                deviceName = syncResult.device,
                                remotePage = syncResult.pageRead,
                            )
                    }

                    if (syncResult.shouldUpdate) {
                        transaction {
                            ChapterTable.update({ ChapterTable.id eq input.chapterId }) {
                                it[lastPageRead] = syncResult.pageRead
                                it[lastReadAt] = syncResult.timestamp
                            }
                        }
                    }
                }

                val chapter =
                    transaction {
                        ChapterTable
                            .selectAll()
                            .where { ChapterTable.id eq input.chapterId }
                            .firstOrNull()
                            ?.let { ChapterType(it) }
                    }

                PullKoSyncProgressPayload(
                    clientMutationId = input.clientMutationId,
                    chapter = chapter,
                    syncConflict = syncConflictInfo,
                )
            }
        }
}
