/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import graphql.execution.DataFetcherResult
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.UploadedFile
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.IReaderExtensionType
import suwayomi.tachidesk.manga.impl.extension.ireader.IReaderExtension
import suwayomi.tachidesk.manga.model.table.IReaderExtensionTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class IReaderExtensionMutation {
    private val logger = KotlinLogging.logger {}

    data class InstallIReaderExtensionInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Package name of the extension to install")
        val pkgName: String,
    )

    data class InstallIReaderExtensionPayload(
        val clientMutationId: String?,
        val extension: IReaderExtensionType?,
    )

    @RequireAuth
    @GraphQLDescription("Install an IReader extension by package name")
    fun installIReaderExtension(
        input: InstallIReaderExtensionInput,
    ): CompletableFuture<DataFetcherResult<InstallIReaderExtensionPayload?>> {
        val (clientMutationId, pkgName) = input

        return future {
            asDataFetcherResult {
                IReaderExtension.installExtension(pkgName)

                val extension =
                    transaction {
                        IReaderExtensionTable
                            .selectAll()
                            .where { IReaderExtensionTable.pkgName eq pkgName }
                            .firstOrNull()
                            ?.let { IReaderExtensionType.fromResultRow(it) }
                    }

                InstallIReaderExtensionPayload(
                    clientMutationId = clientMutationId,
                    extension = extension,
                )
            }
        }
    }

    data class InstallExternalIReaderExtensionInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("APK file of the IReader extension")
        val extensionFile: UploadedFile,
    )

    data class InstallExternalIReaderExtensionPayload(
        val clientMutationId: String?,
        val extension: IReaderExtensionType?,
    )

    @RequireAuth
    @GraphQLDescription("Install an external IReader extension from an APK file")
    fun installExternalIReaderExtension(
        input: InstallExternalIReaderExtensionInput,
    ): CompletableFuture<DataFetcherResult<InstallExternalIReaderExtensionPayload?>> {
        val (clientMutationId, extensionFile) = input

        return future {
            asDataFetcherResult {
                IReaderExtension.installExternalExtension(
                    extensionFile.content(),
                    extensionFile.filename(),
                )

                val extension =
                    transaction {
                        IReaderExtensionTable
                            .selectAll()
                            .where { IReaderExtensionTable.apkName eq extensionFile.filename() }
                            .firstOrNull()
                            ?.let { IReaderExtensionType.fromResultRow(it) }
                    }

                InstallExternalIReaderExtensionPayload(
                    clientMutationId = clientMutationId,
                    extension = extension,
                )
            }
        }
    }

    data class UpdateIReaderExtensionInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Package name of the extension to update")
        val pkgName: String,
    )

    data class UpdateIReaderExtensionPayload(
        val clientMutationId: String?,
        val extension: IReaderExtensionType?,
    )

    @RequireAuth
    @GraphQLDescription("Update an IReader extension")
    fun updateIReaderExtension(input: UpdateIReaderExtensionInput): CompletableFuture<DataFetcherResult<UpdateIReaderExtensionPayload?>> {
        val (clientMutationId, pkgName) = input

        return future {
            asDataFetcherResult {
                IReaderExtension.updateExtension(pkgName)

                val extension =
                    transaction {
                        IReaderExtensionTable
                            .selectAll()
                            .where { IReaderExtensionTable.pkgName eq pkgName }
                            .firstOrNull()
                            ?.let { IReaderExtensionType.fromResultRow(it) }
                    }

                UpdateIReaderExtensionPayload(
                    clientMutationId = clientMutationId,
                    extension = extension,
                )
            }
        }
    }

    data class UninstallIReaderExtensionInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Package name of the extension to uninstall")
        val pkgName: String,
    )

    data class UninstallIReaderExtensionPayload(
        val clientMutationId: String?,
        val success: Boolean,
    )

    @RequireAuth
    @GraphQLDescription("Uninstall an IReader extension")
    fun uninstallIReaderExtension(
        input: UninstallIReaderExtensionInput,
    ): CompletableFuture<DataFetcherResult<UninstallIReaderExtensionPayload?>> {
        val (clientMutationId, pkgName) = input

        return future {
            asDataFetcherResult {
                IReaderExtension.uninstallExtension(pkgName)

                UninstallIReaderExtensionPayload(
                    clientMutationId = clientMutationId,
                    success = true,
                )
            }
        }
    }

    data class UpdateAllIReaderExtensionsInput(
        val clientMutationId: String? = null,
    )

    data class UpdateAllIReaderExtensionsPayload(
        val clientMutationId: String?,
        @GraphQLDescription("Number of extensions queued for update")
        val updatedCount: Int,
    )

    @RequireAuth
    @GraphQLDescription("Update all IReader extensions that have updates available")
    fun updateAllIReaderExtensions(
        input: UpdateAllIReaderExtensionsInput,
    ): CompletableFuture<DataFetcherResult<UpdateAllIReaderExtensionsPayload?>> {
        val (clientMutationId) = input

        return future {
            asDataFetcherResult {
                val extensionsToUpdate =
                    transaction {
                        IReaderExtensionTable
                            .selectAll()
                            .where { IReaderExtensionTable.hasUpdate eq true }
                            .map { it[IReaderExtensionTable.pkgName] }
                    }

                extensionsToUpdate.forEach { pkgName ->
                    try {
                        IReaderExtension.updateExtension(pkgName)
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to update extension $pkgName" }
                    }
                }

                UpdateAllIReaderExtensionsPayload(
                    clientMutationId = clientMutationId,
                    updatedCount = extensionsToUpdate.size,
                )
            }
        }
    }
}
