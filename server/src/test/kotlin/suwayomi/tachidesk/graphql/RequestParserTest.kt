package suwayomi.tachidesk.graphql

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.expediagroup.graphql.server.types.GraphQLRequest
import io.javalin.http.Context
import io.javalin.http.UploadedFile
import io.javalin.json.JavalinJackson3
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.Part
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.graphql.server.JavalinGraphQLRequestParser
import java.io.ByteArrayInputStream
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the multipart GraphQL request spec (operations/map/file-variable injection) and the
 * batch-size guard of [JavalinGraphQLRequestParser]. The Javalin [Context] is mocked: the
 * parser only needs `contentType`, `jsonMapper`, `bodyInputStream`, `formParam` and
 * `uploadedFile`.
 */
class RequestParserTest {
    private val requestParser = JavalinGraphQLRequestParser()

    private fun uploadedFile(content: ByteArray = byteArrayOf(1, 2, 3)): UploadedFile {
        val part = mockk<Part>()
        every { part.inputStream } returns ByteArrayInputStream(content)
        every { part.size } returns content.size.toLong()
        every { part.submittedFileName } returns "file.bin"
        return UploadedFile(part)
    }

    private fun multipartCtx(
        operations: String,
        map: String? = null,
        uploads: Map<String, UploadedFile> = emptyMap(),
    ): Context {
        val ctx = mockk<Context>()
        every { ctx.jsonMapper() } returns JavalinJackson3()
        every { ctx.contentType() } returns "multipart/form-data"
        every { ctx.formParam("operations") } returns operations
        every { ctx.formParam("map") } returns map
        uploads.forEach { (key, file) ->
            every { ctx.uploadedFile(key) } returns file
        }
        return ctx
    }

    @Test
    fun singleFileVariableIsInjectedFromNumericKey() =
        runTest {
            val ctx =
                multipartCtx(
                    operations =
                        """{ "query": "mutation (${'$'}file: Upload!) { singleUpload(file: ${'$'}file) { id } }", "variables": { "file": null } }""",
                    map = """{ "0": ["variables.file"] }""",
                    uploads = mapOf("0" to uploadedFile()),
                )

            val request = requestParser.parseRequest(ctx)

            assertIs<GraphQLRequest>(request)
            assertIs<UploadedFile>(request.variables?.get("file"))
        }

    @Test
    fun singleFileVariableIsInjectedFromNamedKey() =
        runTest {
            val ctx =
                multipartCtx(
                    operations =
                        """{ "query": "mutation (${'$'}file: Upload!) { singleUpload(file: ${'$'}file) { id } }", "variables": { "file": null } }""",
                    map = """{ "test": ["variables.file"] }""",
                    uploads = mapOf("test" to uploadedFile()),
                )

            val request = requestParser.parseRequest(ctx)

            assertIs<GraphQLRequest>(request)
            assertIs<UploadedFile>(request.variables?.get("file"))
        }

    @Test
    fun fileListVariablesAreInjectedByListIndex() =
        runTest {
            val ctx =
                multipartCtx(
                    operations =
                        """{ "query": "mutation (${'$'}files: [Upload!]!) { multiUpload(files: ${'$'}files) { id } }", "variables": { "files": [null, null] } }""",
                    map = """{ "test": ["variables.files.0"], "test2": ["variables.files.1"] }""",
                    uploads =
                        mapOf(
                            "test" to uploadedFile(byteArrayOf(1)),
                            "test2" to uploadedFile(byteArrayOf(2)),
                        ),
                )

            val request = requestParser.parseRequest(ctx)

            assertIs<GraphQLRequest>(request)
            val files = request.variables?.get("files")
            assertIs<List<*>>(files)
            assertTrue(files.all { it is UploadedFile }, "expected every list entry to be an UploadedFile, got $files")
        }

    @Test
    fun batchRequestsOverTheLimitAreRejected() =
        runTest {
            // 21 entries > MAX_BATCH_SIZE (20): the parser must fail the request (null -> 400)
            // instead of forwarding an oversized batch to the engine
            val operations = (1..21).joinToString(",", "[", "]") { """{ "query": "{ field$it }" }""" }
            val ctx =
                multipartCtx(
                    operations = operations,
                )

            assertNull(requestParser.parseRequest(ctx))
        }

    @Test
    fun jsonBodyBatchOverTheLimitIsRejected() =
        runTest {
            // same guard on the application/json code path (bodyInputStream)
            val body = (1..21).joinToString(",", "[", "]") { """{ "query": "{ field$it }" }""" }
            val ctx = mockk<Context>()
            every { ctx.jsonMapper() } returns JavalinJackson3()
            every { ctx.contentType() } returns "application/json"
            every { ctx.bodyInputStream() } returns ByteArrayInputStream(body.toByteArray())

            assertNull(requestParser.parseRequest(ctx))
        }

    @Test
    fun jsonBodySingleRequestIsParsed() =
        runTest {
            val ctx = mockk<Context>()
            every { ctx.jsonMapper() } returns JavalinJackson3()
            every { ctx.contentType() } returns "application/json"
            every { ctx.bodyInputStream() } returns
                ByteArrayInputStream("""{ "query": "{ field }" }""".toByteArray())

            val request = requestParser.parseRequest(ctx)

            assertIs<GraphQLRequest>(request)
            assertNotNull(request.query)
        }
}
