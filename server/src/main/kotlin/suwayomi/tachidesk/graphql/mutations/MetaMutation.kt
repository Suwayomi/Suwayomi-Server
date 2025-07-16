package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.global.impl.GlobalMeta
import suwayomi.tachidesk.global.model.table.GlobalMetaTable
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.GlobalMetaType
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser

class MetaMutation {
    data class SetGlobalMetaInput(
        val clientMutationId: String? = null,
        val meta: GlobalMetaType,
    )

    data class SetGlobalMetaPayload(
        val clientMutationId: String?,
        val meta: GlobalMetaType,
    )

    fun setGlobalMeta(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: SetGlobalMetaInput,
    ): DataFetcherResult<SetGlobalMetaPayload?> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
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

    fun deleteGlobalMeta(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: DeleteGlobalMetaInput,
    ): DataFetcherResult<DeleteGlobalMetaPayload?> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, key) = input

        return asDataFetcherResult {
            val meta =
                transaction {
                    val meta =
                        GlobalMetaTable
                            .selectAll()
                            .where { GlobalMetaTable.key eq key }
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
