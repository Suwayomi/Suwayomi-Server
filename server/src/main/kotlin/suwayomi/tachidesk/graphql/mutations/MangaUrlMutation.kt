package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.server.extensions.toGraphQLError
import graphql.execution.DataFetcherResult
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.core.eq
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.MangaUrlResolver
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class MangaUrlMutation {
    enum class AddMangaFromUrlStatus {
        FOUND,
        EXTENSION_INSTALLED,
        NO_SOURCE_FOR_URL,
        INVALID_URL,
    }

    data class AddMangaFromUrlInput(
        val clientMutationId: String? = null,
        val url: String,
        val autoInstallExtension: Boolean? = null,
        val addToLibrary: Boolean? = null,
        val categoryIds: List<Int>? = null,
    )

    data class AddMangaFromUrlPayload(
        val clientMutationId: String?,
        val status: AddMangaFromUrlStatus,
        val manga: MangaType?,
        val installedExtensionPkgName: String?,
        val message: String?,
    )

    @RequireAuth
    fun addMangaFromUrl(input: AddMangaFromUrlInput): CompletableFuture<DataFetcherResult<AddMangaFromUrlPayload?>> {
        val (clientMutationId, url, autoInstallExtension, addToLibrary, categoryIds) = input

        return future {
            val effectiveAutoInstall = autoInstallExtension ?: true
            val effectiveAddToLibrary = addToLibrary ?: false
            val effectiveCategoryIds = categoryIds.orEmpty()

            var result: MangaUrlResolver.Result? = null
            val error =
                try {
                    result =
                        MangaUrlResolver.resolveUrl(
                            url = url,
                            autoInstallExtension = effectiveAutoInstall,
                            addToLibrary = effectiveAddToLibrary,
                        )
                    if (result.manga != null && effectiveAddToLibrary && effectiveCategoryIds.isNotEmpty()) {
                        CategoryManga.addMangasToCategories(listOf(result.manga.id), effectiveCategoryIds)
                    }
                    null
                } catch (e: Exception) {
                    e
                }

            val mangaType =
                result?.manga?.id?.let { id ->
                    transaction {
                        MangaTable
                            .selectAll()
                            .where { MangaTable.id eq id }
                            .firstOrNull()
                            ?.let { MangaType(it) }
                    }
                }

            @Suppress("UNCHECKED_CAST")
            DataFetcherResult
                .newResult<AddMangaFromUrlPayload>()
                .data(
                    AddMangaFromUrlPayload(
                        clientMutationId = clientMutationId,
                        status = result?.let { AddMangaFromUrlStatus.valueOf(it.status.name) }
                            ?: AddMangaFromUrlStatus.INVALID_URL,
                        manga = mangaType,
                        installedExtensionPkgName = result?.installedExtensionPkgName,
                        message = result?.message ?: error?.message,
                    ),
                ).also {
                    if (error != null) {
                        it.error(error.toGraphQLError())
                    }
                }.build() as DataFetcherResult<AddMangaFromUrlPayload?>
        }
    }
}

