package suwayomi.tachidesk.global.controller

import eu.kanade.tachiyomi.network.NetworkHelper
import io.javalin.http.Context
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import suwayomi.tachidesk.server.mutableConfigValueScope
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object CookieController {
    data class CookieInput(
        val name: String,
        val value: String,
        val domain: String,
        val path: String? = "/",
        val secure: Boolean = false,
        val httpOnly: Boolean = false,
        val expiresAt: Long? = null,
    )

    data class UpdateCookiesRequest(
        val cookies: List<CookieInput>,
        val userAgent: String? = null,
    )

    val updateCookies: (Context) -> Unit = { ctx ->
        val body = ctx.bodyAsClass(UpdateCookiesRequest::class.java)
        val networkHelper = Injekt.get<NetworkHelper>()

        if (!body.userAgent.isNullOrBlank()) {
            mutableConfigValueScope.launch {
            }
        }

        val cookieStore = networkHelper.cookieStore

        val cookiesByDomain = body.cookies.groupBy { it.domain }

        cookiesByDomain.forEach { (domain, inputs) ->
            val url = "http://${domain.removePrefix(".")}".toHttpUrlOrNull() ?: return@forEach

            val okCookies =
                inputs.map { input ->
                    Cookie
                        .Builder()
                        .name(input.name)
                        .value(input.value)
                        .domain(input.domain.removePrefix("."))
                        .path(input.path ?: "/")
                        .apply {
                            if (input.secure) secure()
                            if (input.httpOnly) httpOnly()
                            if (input.expiresAt != null) expiresAt(input.expiresAt)
                            if (input.domain.startsWith(".")) {
                                domain(input.domain.removePrefix("."))
                            } else {
                                hostOnlyDomain(input.domain)
                            }
                        }.build()
                }

            cookieStore.addAll(url, okCookies)
        }

        ctx.status(200).json(mapOf("success" to true, "count" to body.cookies.size))
    }
}
