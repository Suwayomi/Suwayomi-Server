@file:Suppress("RedundantNullableReturnType", "unused")

package suwayomi.tachidesk.graphql.mutations

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.directives.RequirePermissions
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.user.UserPermission
import uy.kohesive.injekt.injectLazy

private val applicationDirs: ApplicationDirs by injectLazy()

class ImageMutation {
    data class ClearCachedImagesInput(
        val clientMutationId: String? = null,
        val downloadedThumbnails: Boolean? = null,
        val cachedThumbnails: Boolean? = null,
        val cachedPages: Boolean? = null,
    )

    data class ClearCachedImagesPayload(
        val clientMutationId: String? = null,
        val downloadedThumbnails: Boolean?,
        val cachedThumbnails: Boolean?,
        val cachedPages: Boolean?,
    )

    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_CACHE)
    fun clearCachedImages(input: ClearCachedImagesInput): ClearCachedImagesPayload {
        val (clientMutationId, downloadedThumbnails, cachedThumbnails, cachedPages) = input

        if (downloadedThumbnails == true || cachedThumbnails == true) {
            Manga.invalidateAllThumbnailFailures()
        }
        val thumbnailCleanup =
            Manga.clearThumbnailDirectories(
                downloadedThumbnails = downloadedThumbnails == true,
                cachedThumbnails = cachedThumbnails == true,
            )

        val cachedPagesResult =
            if (cachedPages == true) {
                ImageResponse.clearImages(applicationDirs.tempMangaCacheRoot)
            } else {
                null
            }

        return ClearCachedImagesPayload(
            clientMutationId,
            downloadedThumbnails = thumbnailCleanup.downloadedThumbnails,
            cachedThumbnails = thumbnailCleanup.cachedThumbnails,
            cachedPages = cachedPagesResult,
        )
    }
}
