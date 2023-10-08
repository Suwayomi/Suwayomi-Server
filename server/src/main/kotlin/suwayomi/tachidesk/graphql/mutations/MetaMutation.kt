package suwayomi.tachidesk.graphql.mutations

import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.global.impl.GlobalMeta
import suwayomi.tachidesk.global.model.table.GlobalMetaTable
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.GlobalMetaType
import suwayomi.tachidesk.server.JavalinSetup.Attribute
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
    ): SetGlobalMetaPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, meta) = input

        GlobalMeta.modifyMeta(userId, meta.key, meta.value)

        return SetGlobalMetaPayload(clientMutationId, meta)
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
    ): DeleteGlobalMetaPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, key) = input

        val meta =
            transaction {
                val meta =
                    GlobalMetaTable.select { GlobalMetaTable.key eq key and (GlobalMetaTable.user eq userId) }
                        .firstOrNull()

                GlobalMetaTable.deleteWhere { GlobalMetaTable.key eq key and (GlobalMetaTable.user eq userId) }

                if (meta != null) {
                    GlobalMetaType(meta)
                } else {
                    null
                }
            }

        return DeleteGlobalMetaPayload(clientMutationId, meta)
    }
}
