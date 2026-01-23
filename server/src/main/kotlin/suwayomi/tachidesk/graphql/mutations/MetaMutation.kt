package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import org.jetbrains.exposed.sql.LikePattern
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.global.impl.GlobalMeta
import suwayomi.tachidesk.global.model.table.GlobalMetaTable
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.GlobalMetaType
import suwayomi.tachidesk.graphql.types.MetaInput

class MetaMutation {
    data class SetGlobalMetaInput(
        val clientMutationId: String? = null,
        val meta: GlobalMetaType,
    )

    data class SetGlobalMetaPayload(
        val clientMutationId: String?,
        val meta: GlobalMetaType,
    )

    @RequireAuth
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

    @RequireAuth
    fun deleteGlobalMeta(input: DeleteGlobalMetaInput): DataFetcherResult<DeleteGlobalMetaPayload?> {
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

    data class SetGlobalMetasInput(
        val clientMutationId: String? = null,
        val metas: List<MetaInput>,
    )

    data class SetGlobalMetasPayload(
        val clientMutationId: String?,
        val metas: List<GlobalMetaType>,
    )

    @RequireAuth
    fun setGlobalMetas(input: SetGlobalMetasInput): DataFetcherResult<SetGlobalMetasPayload?> {
        val (clientMutationId, metas) = input

        return asDataFetcherResult {
            val metaMap = metas.associate { it.key to it.value }
            GlobalMeta.modifyMetas(metaMap)

            val updatedMetas =
                transaction {
                    GlobalMetaTable
                        .selectAll()
                        .where { GlobalMetaTable.key inList metaMap.keys }
                        .map { GlobalMetaType(it) }
                }

            SetGlobalMetasPayload(clientMutationId, updatedMetas)
        }
    }

    data class DeleteGlobalMetasInput(
        val clientMutationId: String? = null,
        val keys: List<String>? = null,
        val prefixes: List<String>? = null,
    )

    data class DeleteGlobalMetasPayload(
        val clientMutationId: String?,
        val metas: List<GlobalMetaType>,
    )

    @RequireAuth
    fun deleteGlobalMetas(input: DeleteGlobalMetasInput): DataFetcherResult<DeleteGlobalMetasPayload?> {
        val (clientMutationId, keys, prefixes) = input

        return asDataFetcherResult {
            require(!keys.isNullOrEmpty() || !prefixes.isNullOrEmpty()) {
                "Either 'keys' or 'prefixes' must be provided"
            }

            val metas =
                transaction {
                    val keyCondition: Op<Boolean>? = keys?.takeIf { it.isNotEmpty() }?.let { GlobalMetaTable.key inList it }

                    val prefixCondition: Op<Boolean>? =
                        prefixes
                            ?.filter { it.isNotEmpty() }
                            ?.map { (GlobalMetaTable.key like LikePattern("$it%")) as Op<Boolean> }
                            ?.reduceOrNull { acc, op -> acc or op }

                    val finalCondition =
                        if (keyCondition != null && prefixCondition != null) {
                            keyCondition or prefixCondition
                        } else {
                            keyCondition ?: prefixCondition!!
                        }

                    val metas =
                        GlobalMetaTable
                            .selectAll()
                            .where { finalCondition }
                            .map { GlobalMetaType(it) }

                    GlobalMetaTable.deleteWhere { finalCondition }

                    metas
                }

            DeleteGlobalMetasPayload(clientMutationId, metas)
        }
    }
}
