package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.global.impl.GlobalMeta
import suwayomi.tachidesk.global.model.table.GlobalMetaTable
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.types.GlobalMetaType

class MetaMutation {
    data class SetGlobalMetaInput(
        val clientMutationId: String? = null,
        val meta: GlobalMetaType,
    )

    data class SetGlobalMetaPayload(
        val clientMutationId: String?,
        val meta: GlobalMetaType,
    )

    fun setGlobalMeta(input: SetGlobalMetaInput): DataFetcherResult<SetGlobalMetaPayload?> {
        val (clientMutationId, meta) = input

        return asDataFetcherResult {
            GlobalMeta.modifyMeta(meta.key, meta.value)

            SetGlobalMetaPayload(clientMutationId, meta)
        }
    }

    data class DeleteGlobalMetaInput(
        val clientMutationId: String? = null,
        val key: String,
    )

    data class DeleteGlobalMetaPayload(
        val clientMutationId: String?,
        val meta: GlobalMetaType?,
    )

    fun deleteGlobalMeta(input: DeleteGlobalMetaInput): DataFetcherResult<DeleteGlobalMetaPayload?> {
        val (clientMutationId, key) = input

        return asDataFetcherResult {
            val meta =
                transaction {
                    val meta =
                        GlobalMetaTable
                            .select { GlobalMetaTable.key eq key }
                            .firstOrNull()

                    GlobalMetaTable.deleteWhere { GlobalMetaTable.key eq key }

                    if (meta != null) {
                        GlobalMetaType(meta)
                    } else {
                        null
                    }
                }

            DeleteGlobalMetaPayload(clientMutationId, meta)
        }
    }
}
