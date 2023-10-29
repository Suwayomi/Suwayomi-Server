package suwayomi.tachidesk.graphql.mutations

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.types.CategoryMetaType
import suwayomi.tachidesk.graphql.types.CategoryType
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.Category.DEFAULT_CATEGORY_ID
import suwayomi.tachidesk.manga.impl.util.lang.isEmpty
import suwayomi.tachidesk.manga.impl.util.lang.isNotEmpty
import suwayomi.tachidesk.manga.model.dataclass.IncludeInUpdate
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryMetaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable

class CategoryMutation {
    data class SetCategoryMetaInput(
        val clientMutationId: String? = null,
        val meta: CategoryMetaType,
    )

    data class SetCategoryMetaPayload(
        val clientMutationId: String?,
        val meta: CategoryMetaType,
    )

    fun setCategoryMeta(input: SetCategoryMetaInput): SetCategoryMetaPayload {
        val (clientMutationId, meta) = input

        Category.modifyMeta(meta.categoryId, meta.key, meta.value)

        return SetCategoryMetaPayload(clientMutationId, meta)
    }

    data class DeleteCategoryMetaInput(
        val clientMutationId: String? = null,
        val categoryId: Int,
        val key: String,
    )

    data class DeleteCategoryMetaPayload(
        val clientMutationId: String?,
        val meta: CategoryMetaType?,
        val category: CategoryType,
    )

    fun deleteCategoryMeta(input: DeleteCategoryMetaInput): DeleteCategoryMetaPayload {
        val (clientMutationId, categoryId, key) = input

        val (meta, category) =
            transaction {
                val meta =
                    CategoryMetaTable.select { (CategoryMetaTable.ref eq categoryId) and (CategoryMetaTable.key eq key) }
                        .firstOrNull()

                CategoryMetaTable.deleteWhere { (CategoryMetaTable.ref eq categoryId) and (CategoryMetaTable.key eq key) }

                val category =
                    transaction {
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

    data class UpdateCategoryPatch(
        val name: String? = null,
        val default: Boolean? = null,
        val includeInUpdate: IncludeInUpdate? = null,
    )

    data class UpdateCategoryPayload(
        val clientMutationId: String?,
        val category: CategoryType,
    )

    data class UpdateCategoryInput(
        val clientMutationId: String? = null,
        val id: Int,
        val patch: UpdateCategoryPatch,
    )

    data class UpdateCategoriesPayload(
        val clientMutationId: String?,
        val categories: List<CategoryType>,
    )

    data class UpdateCategoriesInput(
        val clientMutationId: String? = null,
        val ids: List<Int>,
        val patch: UpdateCategoryPatch,
    )

    private fun updateCategories(
        ids: List<Int>,
        patch: UpdateCategoryPatch,
    ) {
        transaction {
            if (patch.name != null) {
                CategoryTable.update({ CategoryTable.id inList ids }) { update ->
                    patch.name.also {
                        update[name] = it
                    }
                }
            }
            if (patch.default != null) {
                CategoryTable.update({ CategoryTable.id inList ids }) { update ->
                    patch.default.also {
                        update[isDefault] = it
                    }
                }
            }
            if (patch.includeInUpdate != null) {
                CategoryTable.update({ CategoryTable.id inList ids }) { update ->
                    patch.includeInUpdate.also {
                        update[includeInUpdate] = it.value
                    }
                }
            }
        }
    }

    fun updateCategory(input: UpdateCategoryInput): UpdateCategoryPayload {
        val (clientMutationId, id, patch) = input

        updateCategories(listOf(id), patch)

        val category =
            transaction {
                CategoryType(CategoryTable.select { CategoryTable.id eq id }.first())
            }

        return UpdateCategoryPayload(
            clientMutationId = clientMutationId,
            category = category,
        )
    }

    fun updateCategories(input: UpdateCategoriesInput): UpdateCategoriesPayload {
        val (clientMutationId, ids, patch) = input

        updateCategories(ids, patch)

        val categories =
            transaction {
                CategoryTable.select { CategoryTable.id inList ids }.map { CategoryType(it) }
            }

        return UpdateCategoriesPayload(
            clientMutationId = clientMutationId,
            categories = categories,
        )
    }

    data class UpdateCategoryOrderPayload(
        val clientMutationId: String?,
        val categories: List<CategoryType>,
    )

    data class UpdateCategoryOrderInput(
        val clientMutationId: String? = null,
        val id: Int,
        val position: Int,
    )

    fun updateCategoryOrder(input: UpdateCategoryOrderInput): UpdateCategoryOrderPayload {
        val (clientMutationId, categoryId, position) = input
        require(position > 0) {
            "'order' must not be <= 0"
        }

        transaction {
            val currentOrder =
                CategoryTable
                    .select { CategoryTable.id eq categoryId }
                    .first()[CategoryTable.order]

            if (currentOrder != position) {
                if (position < currentOrder) {
                    CategoryTable.update({ CategoryTable.order greaterEq position }) {
                        it[CategoryTable.order] = CategoryTable.order + 1
                    }
                } else {
                    CategoryTable.update({ CategoryTable.order lessEq position }) {
                        it[CategoryTable.order] = CategoryTable.order - 1
                    }
                }

                CategoryTable.update({ CategoryTable.id eq categoryId }) {
                    it[CategoryTable.order] = position
                }
            }
        }

        Category.normalizeCategories()

        val categories =
            transaction {
                CategoryTable.selectAll().orderBy(CategoryTable.order).map { CategoryType(it) }
            }

        return UpdateCategoryOrderPayload(
            clientMutationId = clientMutationId,
            categories = categories,
        )
    }

    data class CreateCategoryInput(
        val clientMutationId: String? = null,
        val name: String,
        val order: Int? = null,
        val default: Boolean? = null,
        val includeInUpdate: IncludeInUpdate? = null,
    )

    data class CreateCategoryPayload(
        val clientMutationId: String?,
        val category: CategoryType,
    )

    fun createCategory(input: CreateCategoryInput): CreateCategoryPayload {
        val (clientMutationId, name, order, default, includeInUpdate) = input
        transaction {
            require(CategoryTable.select { CategoryTable.name eq input.name }.isEmpty()) {
                "'name' must be unique"
            }
        }
        require(!name.equals(Category.DEFAULT_CATEGORY_NAME, ignoreCase = true)) {
            "'name' must not be ${Category.DEFAULT_CATEGORY_NAME}"
        }
        if (order != null) {
            require(order > 0) {
                "'order' must not be <= 0"
            }
        }

        val category =
            transaction {
                if (order != null) {
                    CategoryTable.update({ CategoryTable.order greaterEq order }) {
                        it[CategoryTable.order] = CategoryTable.order + 1
                    }
                }

                val id =
                    CategoryTable.insertAndGetId {
                        it[CategoryTable.name] = input.name
                        it[CategoryTable.order] = order ?: Int.MAX_VALUE
                        if (default != null) {
                            it[CategoryTable.isDefault] = default
                        }
                        if (includeInUpdate != null) {
                            it[CategoryTable.includeInUpdate] = includeInUpdate.value
                        }
                    }

                Category.normalizeCategories()

                CategoryType(CategoryTable.select { CategoryTable.id eq id }.first())
            }

        return CreateCategoryPayload(clientMutationId, category)
    }

    data class DeleteCategoryInput(
        val clientMutationId: String? = null,
        val categoryId: Int,
    )

    data class DeleteCategoryPayload(
        val clientMutationId: String?,
        val category: CategoryType?,
        val mangas: List<MangaType>,
    )

    fun deleteCategory(input: DeleteCategoryInput): DeleteCategoryPayload {
        val (clientMutationId, categoryId) = input
        if (categoryId == 0) { // Don't delete default category
            return DeleteCategoryPayload(
                clientMutationId,
                null,
                emptyList(),
            )
        }

        val (category, mangas) =
            transaction {
                val category =
                    CategoryTable.select { CategoryTable.id eq categoryId }
                        .firstOrNull()

                val mangas =
                    transaction {
                        MangaTable.innerJoin(CategoryMangaTable)
                            .select { CategoryMangaTable.category eq categoryId }
                            .map { MangaType(it) }
                    }

                CategoryTable.deleteWhere { CategoryTable.id eq categoryId }

                Category.normalizeCategories()

                if (category != null) {
                    CategoryType(category)
                } else {
                    null
                } to mangas
            }

        return DeleteCategoryPayload(clientMutationId, category, mangas)
    }

    data class UpdateMangaCategoriesPatch(
        val clearCategories: Boolean? = null,
        val addToCategories: List<Int>? = null,
        val removeFromCategories: List<Int>? = null,
    )

    data class UpdateMangaCategoriesPayload(
        val clientMutationId: String?,
        val manga: MangaType,
    )

    data class UpdateMangaCategoriesInput(
        val clientMutationId: String? = null,
        val id: Int,
        val patch: UpdateMangaCategoriesPatch,
    )

    data class UpdateMangasCategoriesPayload(
        val clientMutationId: String?,
        val mangas: List<MangaType>,
    )

    data class UpdateMangasCategoriesInput(
        val clientMutationId: String? = null,
        val ids: List<Int>,
        val patch: UpdateMangaCategoriesPatch,
    )

    private fun updateMangas(
        ids: List<Int>,
        patch: UpdateMangaCategoriesPatch,
    ) {
        transaction {
            if (patch.clearCategories == true) {
                CategoryMangaTable.deleteWhere { CategoryMangaTable.manga inList ids }
            } else if (!patch.removeFromCategories.isNullOrEmpty()) {
                CategoryMangaTable.deleteWhere {
                    (CategoryMangaTable.manga inList ids) and (CategoryMangaTable.category inList patch.removeFromCategories)
                }
            }
            if (!patch.addToCategories.isNullOrEmpty()) {
                val newCategories =
                    buildList {
                        ids.filter { it != DEFAULT_CATEGORY_ID }.forEach { mangaId ->
                            patch.addToCategories.forEach { categoryId ->
                                val existingMapping =
                                    CategoryMangaTable.select {
                                        (CategoryMangaTable.manga eq mangaId) and (CategoryMangaTable.category eq categoryId)
                                    }.isNotEmpty()

                                if (!existingMapping) {
                                    add(mangaId to categoryId)
                                }
                            }
                        }
                    }

                CategoryMangaTable.batchInsert(newCategories) { (manga, category) ->
                    this[CategoryMangaTable.manga] = manga
                    this[CategoryMangaTable.category] = category
                }
            }
        }
    }

    fun updateMangaCategories(input: UpdateMangaCategoriesInput): UpdateMangaCategoriesPayload {
        val (clientMutationId, id, patch) = input

        updateMangas(listOf(id), patch)

        val manga =
            transaction {
                MangaType(MangaTable.select { MangaTable.id eq id }.first())
            }

        return UpdateMangaCategoriesPayload(
            clientMutationId = clientMutationId,
            manga = manga,
        )
    }

    fun updateMangasCategories(input: UpdateMangasCategoriesInput): UpdateMangasCategoriesPayload {
        val (clientMutationId, ids, patch) = input

        updateMangas(ids, patch)

        val mangas =
            transaction {
                MangaTable.select { MangaTable.id inList ids }.map { MangaType(it) }
            }

        return UpdateMangasCategoriesPayload(
            clientMutationId = clientMutationId,
            mangas = mangas,
        )
    }
}
