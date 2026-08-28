@file:Suppress("RedundantNullableReturnType", "unused")

package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import eu.kanade.tachiyomi.source.local.LocalSource
import io.javalin.http.UploadedFile
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.directives.RequirePermissions
import suwayomi.tachidesk.graphql.types.ExtensionStoreType
import suwayomi.tachidesk.graphql.types.ExtensionType
import suwayomi.tachidesk.manga.impl.extension.Extension
import suwayomi.tachidesk.manga.impl.extension.ExtensionsList
import suwayomi.tachidesk.manga.model.table.ExtensionStoreTable
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.ForbiddenException
import suwayomi.tachidesk.server.user.Permissions
import suwayomi.tachidesk.server.user.hasPermission
import java.util.concurrent.CompletableFuture

class ExtensionMutation {
    data class UpdateExtensionPatch(
        val install: Boolean? = null,
        val update: Boolean? = null,
        val uninstall: Boolean? = null,
    )

    data class UpdateExtensionPayload(
        val clientMutationId: String?,
        val extension: ExtensionType?,
    )

    data class UpdateExtensionInput(
        val clientMutationId: String? = null,
        val id: String,
        val patch: UpdateExtensionPatch,
    )

    data class UpdateExtensionsPayload(
        val clientMutationId: String?,
        val extensions: List<ExtensionType>,
    )

    data class UpdateExtensionsInput(
        val clientMutationId: String? = null,
        val ids: List<String>,
        val patch: UpdateExtensionPatch,
    )

    private suspend fun updateExtensions(
        ids: List<String>,
        patch: UpdateExtensionPatch,
    ) {
        val extensions =
            transaction {
                ExtensionTable
                    .selectAll()
                    .where { ExtensionTable.pkgName inList ids }
                    .map { ExtensionType(it) }
            }

        if (patch.update == true) {
            extensions.filter { it.hasUpdate }.forEach {
                Extension.updateExtension(it.pkgName)
            }
        }

        if (patch.install == true) {
            extensions.filterNot { it.isInstalled }.forEach {
                Extension.installExtension(it.pkgName)
            }
        }

        if (patch.uninstall == true) {
            extensions.filter { it.isInstalled }.forEach {
                Extension.uninstallExtension(it.pkgName)
            }
        }
    }

    private fun checkUpdatePermissions(
        permissions: List<Permissions>,
        patch: UpdateExtensionPatch,
    ) {
        if (patch.install == true && !permissions.hasPermission(Permissions.INSTALL_EXTENSIONS)) {
            throw ForbiddenException()
        }

        if (patch.uninstall == true && !permissions.hasPermission(Permissions.UNINSTALL_EXTENSIONS)) {
            throw ForbiddenException()
        }
    }

    @RequireAuth
    fun updateExtension(
        @GraphQLIgnore
        permissions: List<Permissions>,
        input: UpdateExtensionInput,
    ): CompletableFuture<UpdateExtensionPayload?> {
        val (clientMutationId, id, patch) = input

        checkUpdatePermissions(permissions, patch)

        return future {
            updateExtensions(listOf(id), patch)

            val extension =
                transaction {
                    ExtensionTable
                        .selectAll()
                        .where { ExtensionTable.pkgName eq id }
                        .firstOrNull()
                        ?.let { ExtensionType(it) }
                }

            UpdateExtensionPayload(
                clientMutationId = clientMutationId,
                extension = extension,
            )
        }
    }

    @RequireAuth
    fun updateExtensions(
        @GraphQLIgnore
        permissions: List<Permissions>,
        input: UpdateExtensionsInput,
    ): CompletableFuture<UpdateExtensionsPayload?> {
        val (clientMutationId, ids, patch) = input

        checkUpdatePermissions(permissions, patch)

        return future {
            updateExtensions(ids, patch)

            val extensions =
                transaction {
                    ExtensionTable
                        .selectAll()
                        .where { ExtensionTable.pkgName inList ids }
                        .map { ExtensionType(it) }
                }

            UpdateExtensionsPayload(
                clientMutationId = clientMutationId,
                extensions = extensions,
            )
        }
    }

    data class FetchExtensionsInput(
        val clientMutationId: String? = null,
    )

    data class FetchExtensionsPayload(
        val clientMutationId: String?,
        val extensions: List<ExtensionType>,
        val extensionStores: List<ExtensionStoreType>,
    )

    @RequireAuth
    fun fetchExtensions(input: FetchExtensionsInput): CompletableFuture<FetchExtensionsPayload?> {
        val (clientMutationId) = input

        return future {
            ExtensionsList.fetchExtensions()

            val extensions =
                transaction {
                    ExtensionTable
                        .selectAll()
                        .where { ExtensionTable.name neq LocalSource.EXTENSION_NAME }
                        .map { ExtensionType(it) }
                }

            val extensionStores =
                transaction {
                    ExtensionStoreTable
                        .selectAll()
                        .map { ExtensionStoreType(it) }
                }

            FetchExtensionsPayload(
                clientMutationId = clientMutationId,
                extensions = extensions,
                extensionStores = extensionStores,
            )
        }
    }

    data class InstallExternalExtensionInput(
        val clientMutationId: String? = null,
        val extensionFile: UploadedFile,
    )

    data class InstallExternalExtensionPayload(
        val clientMutationId: String?,
        val extension: ExtensionType,
    )

    @RequireAuth
    @RequirePermissions(Permissions.INSTALL_UNTRUSTED_EXTENSIONS)
    fun installExternalExtension(input: InstallExternalExtensionInput): CompletableFuture<InstallExternalExtensionPayload?> {
        val (clientMutationId, extensionFile) = input

        return future {
            val pkgName = Extension.installExternalExtension(extensionFile.content(), extensionFile.filename())

            val dbExtension =
                transaction { ExtensionTable.selectAll().where { ExtensionTable.pkgName eq pkgName }.first() }

            InstallExternalExtensionPayload(
                clientMutationId,
                extension = ExtensionType(dbExtension),
            )
        }
    }
}
