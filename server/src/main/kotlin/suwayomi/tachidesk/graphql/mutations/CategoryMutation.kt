package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.types.CategoryMetaType
import suwayomi.tachidesk.graphql.types.CategoryType
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.util.lang.isEmpty
import suwayomi.tachidesk.manga.model.dataclass.IncludeOrExclude
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

    fun setCategoryMeta(input: SetCategoryMetaInput): DataFetcherResult<SetCategoryMetaPayload?> =
        asDataFetcherResult {
            val (clientMutationId, meta) = input

            Category.modifyMeta(meta.categoryId, meta.key, meta.value)

            SetCategoryMetaPayload(clientMutationId, meta)
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

    fun deleteCategoryMeta(input: DeleteCategoryMetaInput): DataFetcherResult<DeleteCategoryMetaPayload?> =
        asDataFetcherResult {
            val (clientMutationId, categoryId, key) = input

            val (meta, category) =
                transaction {
                    val meta =
                        CategoryMetaTable
                            .selectAll()
                            .where { (CategoryMetaTable.ref eq categoryId) and (CategoryMetaTable.key eq key) }
                            .firstOrNull()

                    CategoryMetaTable.deleteWhere { (CategoryMetaTable.ref eq categoryId) and (CategoryMetaTable.key eq key) }

                    val category =
                        transaction {
                            CategoryType(CategoryTable.selectAll().where { CategoryTable.id eq categoryId }.first())
                        }

                    if (meta != null) {
                        CategoryMetaType(meta)
                    } else {
                        null
                    } to category
                }

            DeleteCategoryMetaPayload(clientMutationId, meta, category)
        }

    data class UpdateCategoryPatch(
        val name: String? = null,
        val default: Boolean? = null,
        val includeInUpdate: IncludeOrExclude? = null,
        val includeInDownload: IncludeOrExclude? = null,
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
            if (patch.includeInDownload != null) {
                CategoryTable.update({ CategoryTable.id inList ids }) { update ->
                    patch.includeInDownload.also {
                        update[includeInDownload] = it.value
                    }
                }
            }
        }
    }

    fun updateCategory(input: UpdateCategoryInput): DataFetcherResult<UpdateCategoryPayload?> =
        asDataFetcherResult {
            val (clientMutationId, id, patch) = input

            updateCategories(listOf(id), patch)

            val category =
                transaction {
                    CategoryType(CategoryTable.selectAll().where { CategoryTable.id eq id }.first())
                }

            UpdateCategoryPayload(
                clientMutationId = clientMutationId,
                category = category,
            )
        }

    fun updateCategories(input: UpdateCategoriesInput): DataFetcherResult<UpdateCategoriesPayload?> =
        asDataFetcherResult {
            val (clientMutationId, ids, patch) = input

            updateCategories(ids, patch)

            val categories =
                transaction {
                    CategoryTable.selectAll().where { CategoryTable.id inList ids }.map { CategoryType(it) }
                }

            UpdateCategoriesPayload(
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

    fun updateCategoryOrder(input: UpdateCategoryOrderInput): DataFetcherResult<UpdateCategoryOrderPayload?> =
        asDataFetcherResult {
            val (clientMutationId, categoryId, position) = input
            require(position > 0) {
                "'order' must not be <= 0"
            }

            transaction {
                val currentOrder =
                    CategoryTable
                        .selectAll()
                        .where { CategoryTable.id eq categoryId }
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

            UpdateCategoryOrderPayload(
                clientMutationId = clientMutationId,
                categories = categories,
            )
        }

    data class CreateCategoryInput(
        val clientMutationId: String? = null,
        val name: String,
        val order: Int? = null,
        val default: Boolean? = null,
        val includeInUpdate: IncludeOrExclude? = null,
        val includeInDownload: IncludeOrExclude? = null,
    )

    data class CreateCategoryPayload(
        val clientMutationId: String?,
        val category: CategoryType,
    )

    fun createCategory(input: CreateCategoryInput): DataFetcherResult<CreateCategoryPayload?> =
        asDataFetcherResult {
            val (clientMutationId, name, order, default, includeInUpdate, includeInDownload) = input
            transaction {
                require(CategoryTable.selectAll().where { CategoryTable.name eq input.name }.isEmpty()) {
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
                            if (includeInDownload != null) {
                                it[CategoryTable.includeInDownload] = includeInDownload.value
                            }
                        }

                    Category.normalizeCategories()

                    CategoryType(CategoryTable.selectAll().where { CategoryTable.id eq id }.first())
                }

            CreateCategoryPayload(clientMutationId, category)
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

    fun deleteCategory(input: DeleteCategoryInput): DataFetcherResult<DeleteCategoryPayload?> {
        return asDataFetcherResult {
            val (clientMutationId, categoryId) = input
            if (categoryId == 0) { // Don't delete default category
                return@asDataFetcherResult DeleteCategoryPayload(
                    clientMutationId,
                    null,
                    emptyList(),
                )
            }

            val (category, mangas) =
                transaction {
                    val category =
                        CategoryTable
                            .selectAll()
                            .where { CategoryTable.id eq categoryId }
                            .firstOrNull()

                    val mangas =
                        transaction {
                            MangaTable
                                .innerJoin(CategoryMangaTable)
                                .selectAll()
                                .where { CategoryMangaTable.category eq categoryId }
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

            DeleteCategoryPayload(clientMutationId, category, mangas)
        }
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
                CategoryManga.addMangasToCategories(ids, patch.addToCategories)
            }
        }
    }

    fun updateMangaCategories(input: UpdateMangaCategoriesInput): DataFetcherResult<UpdateMangaCategoriesPayload?> =
        asDataFetcherResult {
            val (clientMutationId, id, patch) = input

            updateMangas(listOf(id), patch)

            val manga =
                transaction {
                    MangaType(MangaTable.selectAll().where { MangaTable.id eq id }.first())
                }

            UpdateMangaCategoriesPayload(
                clientMutationId = clientMutationId,
                manga = manga,
            )
        }

    fun updateMangasCategories(input: UpdateMangasCategoriesInput): DataFetcherResult<UpdateMangasCategoriesPayload?> =
        asDataFetcherResult {
            val (clientMutationId, ids, patch) = input

            updateMangas(ids, patch)

            val mangas =
                transaction {
                    MangaTable.selectAll().where { MangaTable.id inList ids }.map { MangaType(it) }
                }

            UpdateMangasCategoriesPayload(
                clientMutationId = clientMutationId,
                mangas = mangas,
            )
        }
}
