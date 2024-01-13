/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server

import com.expediagroup.graphql.server.execution.GraphQLRequestParser
import com.expediagroup.graphql.server.types.GraphQLBatchRequest
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.expediagroup.graphql.server.types.GraphQLServerRequest
import io.javalin.http.Context
import io.javalin.http.UploadedFile
import io.javalin.plugin.json.jsonMapper
import java.io.IOException

class JavalinGraphQLRequestParser : GraphQLRequestParser<Context> {
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST")
    override suspend fun parseRequest(context: Context): GraphQLServerRequest? {
        return try {
            val contentType = context.contentType()
            val formParam =
                if (
                    contentType?.contains("application/x-www-form-urlencoded") == true ||
                    contentType?.contains("multipart/form-data") == true
                ) {
                    context.formParam("operations")
                        ?: throw IllegalArgumentException("Cannot find 'operations' body")
                } else {
                    return context.bodyAsClass(GraphQLServerRequest::class.java)
                }

            val request =
                context.jsonMapper().fromJsonString(
                    formParam,
                    GraphQLServerRequest::class.java,
                )
            val map =
                context.formParam("map")?.let {
                    context.jsonMapper().fromJsonString(
                        it,
                        Map::class.java as Class<Map<String, List<String>>>,
                    )
                }.orEmpty()

            val mapItems =
                map.flatMap { (key, variables) ->
                    val file = context.uploadedFile(key)
                    variables.map { fullVariable ->
                        val variable = fullVariable.removePrefix("variables.").substringBefore('.')
                        val listIndex = fullVariable.substringAfterLast('.').toIntOrNull()
                        MapItem(
                            variable,
                            listIndex,
                            file,
                        )
                    }
                }.groupBy { it.variable }

            when (request) {
                is GraphQLRequest -> {
                    request.copy(variables = request.variables?.modifyFiles(mapItems))
                }
                is GraphQLBatchRequest -> {
                    request.copy(
                        requests =
                            request.requests.map {
                                it.copy(
                                    variables = it.variables?.modifyFiles(mapItems),
                                )
                            },
                    )
                }
            }
        } catch (e: IOException) {
            null
        }
    }

    data class MapItem(
        val variable: String,
        val listIndex: Int?,
        val file: UploadedFile?,
    )

    /**
     * Example [this]: { "file": null }
     * Example: '{ "query": "mutation ($file: Upload!) { singleUpload(file: $file) { id } }", "variables": { "file": null } }'
     * Example map "{ "0": ["variables.file"] }"
     * TODO nested objects
     */
    private fun Map<String, Any?>.modifyFiles(map: Map<String, List<MapItem>>): Map<String, Any?> {
        return mapValues { (name, value) ->
            if (map.containsKey(name)) {
                val items = map[name].orEmpty()
                if (items.size > 1) {
                    if (value is List<*>) {
                        value.mapIndexed { index, any ->
                            any ?: items.firstOrNull { it.listIndex == index }?.file
                        }
                    } else {
                        value
                    }
                } else {
                    value ?: items.firstOrNull()?.file
                }
            } else {
                value
            }
        }
    }
}
