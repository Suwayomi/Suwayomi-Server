package ir.armor.tachidesk.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.FormBody
import okhttp3.OkHttpClient
import java.net.URLEncoder

class MangaDexHelper(private val mangaDexSource: HttpSource) {

    private fun clientBuilder(): OkHttpClient = clientBuilder(0)

    private fun clientBuilder(
        r18Toggle: Int,
        okHttpClient: OkHttpClient = mangaDexSource.network.client
    ): OkHttpClient = okHttpClient.newBuilder()
        .addNetworkInterceptor { chain ->
            val originalCookies = chain.request().header("Cookie") ?: ""
            val newReq = chain
                .request()
                .newBuilder()
                .header("Cookie", "$originalCookies; ${cookiesHeader(r18Toggle)}")
                .build()
            chain.proceed(newReq)
        }.build()

    private fun cookiesHeader(r18Toggle: Int): String {
        val cookies = mutableMapOf<String, String>()
        cookies["mangadex_h_toggle"] = r18Toggle.toString()
        return buildCookies(cookies)
    }

    private fun buildCookies(cookies: Map<String, String>) =
        cookies.entries.joinToString(separator = "; ", postfix = ";") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

//    fun isLogged(): Boolean {
//        val httpUrl = mangaDexSource.baseUrl.toHttpUrlOrNull()!!
//        return network.cookieManager.get(httpUrl).any { it.name == REMEMBER_ME }
//    }

    fun login(username: String, password: String, twoFactorCode: String = ""): Boolean {
        val formBody = FormBody.Builder()
            .add("login_username", username)
            .add("login_password", password)
            .add("no_js", "1")
            .add("remember_me", "1")

        twoFactorCode.let {
            formBody.add("two_factor", it)
        }

        val response = clientBuilder().newCall(
            POST(
                "${mangaDexSource.baseUrl}/ajax/actions.ajax.php?function=login",
                mangaDexSource.headers,
                formBody.build()
            )
        ).execute()
        return response.body!!.string().isEmpty()
    }
//
//    fun logout(): Boolean {
//        return withContext(Dispatchers.IO) {
//            // https://mangadex.org/ajax/actions.ajax.php?function=logout
//            val httpUrl = baseUrl.toHttpUrlOrNull()!!
//            val listOfDexCookies = network.cookieManager.get(httpUrl)
//            val cookie = listOfDexCookies.find { it.name == REMEMBER_ME }
//            val token = cookie?.value
//            if (token.isNullOrEmpty()) {
//                return@withContext true
//            }
//            val result = clientBuilder().newCall(
//                    POSTWithCookie(
//                            "$baseUrl/ajax/actions.ajax.php?function=logout",
//                            REMEMBER_ME,
//                            token,
//                            headers
//                    )
//            ).execute()
//            val resultStr = result.body!!.string()
//            if (resultStr.contains("success", true)) {
//                network.cookieManager.remove(httpUrl)
//                return@withContext true
//            }
//
//            false
//        }
//    }
}
