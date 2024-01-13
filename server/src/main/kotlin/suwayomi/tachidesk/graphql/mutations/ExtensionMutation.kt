package suwayomi.tachidesk.graphql.mutations

import eu.kanade.tachiyomi.source.local.LocalSource
import io.javalin.http.UploadedFile
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.ExtensionType
import suwayomi.tachidesk.manga.impl.extension.Extension
import suwayomi.tachidesk.manga.impl.extension.ExtensionsList
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.server.JavalinSetup.future
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
                ExtensionTable.select { ExtensionTable.pkgName inList ids }
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

    fun updateExtension(input: UpdateExtensionInput): CompletableFuture<UpdateExtensionPayload> {
        val (clientMutationId, id, patch) = input

        return future {
            updateExtensions(listOf(id), patch)
        }.thenApply {
            val extension =
                transaction {
                    ExtensionTable.select { ExtensionTable.pkgName eq id }.firstOrNull()
                        ?.let { ExtensionType(it) }
                }

            UpdateExtensionPayload(
                clientMutationId = clientMutationId,
                extension = extension,
            )
        }
    }

    fun updateExtensions(input: UpdateExtensionsInput): CompletableFuture<UpdateExtensionsPayload> {
        val (clientMutationId, ids, patch) = input

        return future {
            updateExtensions(ids, patch)
        }.thenApply {
            val extensions =
                transaction {
                    ExtensionTable.select { ExtensionTable.pkgName inList ids }
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
    )

    fun fetchExtensions(input: FetchExtensionsInput): CompletableFuture<FetchExtensionsPayload> {
        val (clientMutationId) = input

        return future {
            ExtensionsList.fetchExtensions()
        }.thenApply {
            val extensions =
                transaction {
                    ExtensionTable.select { ExtensionTable.name neq LocalSource.EXTENSION_NAME }
                        .map { ExtensionType(it) }
                }

            FetchExtensionsPayload(
                clientMutationId = clientMutationId,
                extensions = extensions,
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

    fun installExternalExtension(input: InstallExternalExtensionInput): CompletableFuture<InstallExternalExtensionPayload> {
        val (clientMutationId, extensionFile) = input

        return future {
            Extension.installExternalExtension(extensionFile.content, extensionFile.filename)
        }.thenApply {
            val dbExtension = transaction { ExtensionTable.select { ExtensionTable.apkName eq extensionFile.filename }.first() }

            InstallExternalExtensionPayload(
                clientMutationId,
                extension = ExtensionType(dbExtension),
            )
        }
    }
}
