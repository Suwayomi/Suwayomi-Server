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
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.Context
import io.javalin.http.UploadedFile
import io.javalin.json.fromJsonStream
import io.javalin.json.fromJsonString

class JavalinGraphQLRequestParser : GraphQLRequestParser<Context> {
    private val logger = KotlinLogging.logger {}

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override suspend fun parseRequest(context: Context): GraphQLServerRequest? {
        // client mistakes (missing 'operations' form field, malformed map json, oversized
        // batches, ...) are 400s, not server faults: returning null makes the controller
        // answer 400, so every decode/validation failure lands here
        return try {
            val jsonMapper = context.jsonMapper()
            val contentType = context.contentType()
            val isFormContentType =
                contentType?.contains("application/x-www-form-urlencoded", true) == true ||
                    contentType?.contains("multipart/form-data", true) == true

            if (!isFormContentType) {
                return context
                    .bodyInputStream()
                    .use { jsonMapper.fromJsonStream<GraphQLServerRequest>(it) }
                    .also { it.requireMaxBatchSize() }
            }

            val request =
                jsonMapper.fromJsonString<GraphQLServerRequest>(
                    context.formParam("operations")
                        ?: throw IllegalArgumentException("Cannot find 'operations' body"),
                )
            request.requireMaxBatchSize()

            val map =
                context
                    .formParam("map")
                    ?.let {
                        jsonMapper.fromJsonString<Map<String, List<String>>>(it)
                    }.orEmpty()

            val mapItems =
                map
                    .flatMap { (key, variables) ->
                        val file = context.uploadedFile(key)
                        if (file == null) {
                            logger.warn { "Upload map references missing file part '$key'" }
                        }
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
        } catch (e: Exception) {
            logger.error(e) { "Error when parsing request" }
            null
        }
    }

    private fun GraphQLServerRequest.requireMaxBatchSize() {
        if (this is GraphQLBatchRequest) {
            require(requests.size <= MAX_BATCH_SIZE) {
                "Batch request size ${requests.size} exceeds the limit of $MAX_BATCH_SIZE"
            }
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
        val unmatched = map.keys - keys
        if (unmatched.isNotEmpty()) {
            logger.warn { "Upload map references variables not present in 'operations': $unmatched" }
        }
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

    companion object {
        private const val MAX_BATCH_SIZE = 20
    }
}
