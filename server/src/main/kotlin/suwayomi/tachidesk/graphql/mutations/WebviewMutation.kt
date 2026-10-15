@file:Suppress("RedundantNullableReturnType", "unused")

package suwayomi.tachidesk.graphql.mutations

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.directives.RequirePermissions
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.user.UserPermission
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.CookieHandler
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively

@OptIn(ExperimentalPathApi::class)
class WebviewMutation {
    private val applicationDirs by lazy { Injekt.get<ApplicationDirs>() }

    data class ClearCookiesAndCacheInput(
        val clientMutationId: String? = null,
    )

    data class ClearCookiesAndCachePayload(
        val clientMutationId: String?,
    )

    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_CACHE)
    fun clearCookiesAndCache(input: ClearCookiesAndCacheInput? = null): ClearCookiesAndCachePayload {
        // the default handler isn't guaranteed to be a CookieManager — a bare `as` cast
        // would throw an opaque ClassCastException
        val cookieHandler =
            CookieHandler.getDefault() as? java.net.CookieManager
                ?: throw IllegalStateException("No CookieManager is installed as the default CookieHandler")
        cookieHandler.cookieStore.removeAll()
        Path(applicationDirs.cacheDir).deleteRecursively()

        return ClearCookiesAndCachePayload(
            clientMutationId = input?.clientMutationId,
        )
    }
}
