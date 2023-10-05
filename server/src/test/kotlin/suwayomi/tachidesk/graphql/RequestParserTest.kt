package suwayomi.tachidesk.graphql

import com.expediagroup.graphql.server.types.GraphQLRequest
import io.javalin.http.Context
import io.javalin.http.UploadedFile
import io.javalin.plugin.json.JSON_MAPPER_KEY
import io.javalin.plugin.json.JavalinJackson
import io.javalin.plugin.json.JsonMapper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.graphql.server.JavalinGraphQLRequestParser
import java.io.ByteArrayInputStream
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class RequestParserTest {
    private val ctx = mockk<Context>(relaxed = true)
    private val requestParser = JavalinGraphQLRequestParser()

    @Test
    fun testZero() =
        runTest {
            every { ctx.appAttribute<JsonMapper>(JSON_MAPPER_KEY) } returns
                (JavalinJackson(JavalinJackson.defaultMapper()))
            every {
                ctx.formParam("operations")
            } returns
                """{ "query": "mutation (${'$'}file: Upload!) { 
                |singleUpload(file: ${'$'}file) { id } }", "variables": { "file": null } 
                |}
                """.trimMargin()
            every { ctx.formParam("map") } returns """{ "0": ["variables.file"] }"""
            every { ctx.uploadedFile("0") } returns
                UploadedFile(
                    ByteArrayInputStream(byteArrayOf()), "", "", "", 0,
                )
            val test = requestParser.parseRequest(ctx)
            assertIs<GraphQLRequest>(test)
            assertNotNull(test.variables?.get("file"))
            println("File: " + test.variables?.get("file"))
        }

    @Test
    fun testTest() =
        runTest {
            every { ctx.appAttribute<JsonMapper>(JSON_MAPPER_KEY) } returns
                (JavalinJackson(JavalinJackson.defaultMapper()))
            every {
                ctx.formParam("operations")
            } returns
                """{ "query": "mutation (${'$'}file: Upload!) { 
                |singleUpload(file: ${'$'}file) { id } }", "variables": { "file": null } 
                |}
                """.trimMargin()
            every { ctx.formParam("map") } returns """{ "test": ["variables.file"] }"""
            every { ctx.uploadedFile("test") } returns
                UploadedFile(
                    ByteArrayInputStream(byteArrayOf()), "", "", "", 0,
                )
            val test = requestParser.parseRequest(ctx)
            assertIs<GraphQLRequest>(test)
            assertNotNull(test.variables?.get("file"))
            println("File: " + test.variables?.get("file"))
        }

    @Test
    fun testList() =
        runTest {
            every { ctx.appAttribute<JsonMapper>(JSON_MAPPER_KEY) } returns
                (JavalinJackson(JavalinJackson.defaultMapper()))
            every {
                ctx.formParam("operations")
            } returns
                """{ "query": "mutation (${'$'}files: [Upload!]!) { 
                |singleUpload(files: ${'$'}files) { id } }", "variables": { "files": [null, null] } 
                |}
                """.trimMargin()
            every { ctx.formParam("map") } returns
                """
                { "test": ["variables.files.0"], "test2": ["variables.files.1"] }
                """.trimIndent()
            every { ctx.uploadedFile("test") } returns
                UploadedFile(
                    ByteArrayInputStream(byteArrayOf()), "", "", "", 0,
                )
            every { ctx.uploadedFile("test2") } returns
                UploadedFile(
                    ByteArrayInputStream(byteArrayOf()), "", "", "", 0,
                )
            val test = requestParser.parseRequest(ctx)
            assertIs<GraphQLRequest>(test)
            val files = test.variables?.get("files")
            assertIs<List<*>>(files)
            assert(files.all { it is UploadedFile })
            println("Files: $files")
        }
}
