package suwayomi.tachidesk.graphql.mutations

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.CategoryMetaType
import suwayomi.tachidesk.graphql.types.CategoryType
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.model.table.CategoryMetaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable

/**
 * TODO Mutations
 * - Name
 * - Order
 * - Default
 * - Create
 * - Delete
 */
class CategoryMutation {
    data class SetCategoryMetaInput(
        val clientMutationId: String? = null,
        val meta: CategoryMetaType
    )
    data class SetCategoryMetaPayload(
        val clientMutationId: String?,
        val meta: CategoryMetaType
    )
    fun setCategoryMeta(
        input: SetCategoryMetaInput
    ): SetCategoryMetaPayload {
        val (clientMutationId, meta) = input

        Category.modifyMeta(meta.categoryId, meta.key, meta.value)

        return SetCategoryMetaPayload(clientMutationId, meta)
    }

    data class DeleteCategoryMetaInput(
        val clientMutationId: String? = null,
        val categoryId: Int,
        val key: String
    )
    data class DeleteCategoryMetaPayload(
        val clientMutationId: String?,
        val meta: CategoryMetaType?,
        val category: CategoryType
    )
    fun deleteCategoryMeta(
        input: DeleteCategoryMetaInput
    ): DeleteCategoryMetaPayload {
        val (clientMutationId, categoryId, key) = input

        val (meta, category) = transaction {
            val meta = CategoryMetaTable.select { (CategoryMetaTable.ref eq categoryId) and (CategoryMetaTable.key eq key) }
                .firstOrNull()

            CategoryMetaTable.deleteWhere { (CategoryMetaTable.ref eq categoryId) and (CategoryMetaTable.key eq key) }

            val category = transaction {
                CategoryType(CategoryTable.select { CategoryTable.id eq categoryId }.first())
            }

            if (meta != null) {
                CategoryMetaType(meta)
            } else {
                null
            } to category
        }

        return DeleteCategoryMetaPayload(clientMutationId, meta, category)
    }
}
