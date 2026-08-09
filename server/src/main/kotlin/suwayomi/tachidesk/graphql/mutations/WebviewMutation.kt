@file:Suppress("RedundantNullableReturnType", "unused")

package suwayomi.tachidesk.graphql.mutations

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.CookieHandler
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div

@OptIn(ExperimentalPathApi::class)
class WebviewMutation {
    private val applicationDirs by lazy { Injekt.get<ApplicationDirs>() }
    private val cacheDir by lazy { Path(applicationDirs.dataRoot) / "cache" }

    data class ClearCookiesAndCacheInput(
        val clientMutationId: String? = null,
    )

    data class ClearCookiesAndCachePayload(
        val clientMutationId: String?,
    )

    @RequireAuth
    fun clearCookiesAndCache(input: ClearCookiesAndCacheInput? = null): ClearCookiesAndCachePayload {
        val cookieHandler = CookieHandler.getDefault() as java.net.CookieManager
        cookieHandler.cookieStore.removeAll()
        cacheDir.deleteRecursively()

        return ClearCookiesAndCachePayload(
            clientMutationId = input?.clientMutationId,
        )
    }
}
