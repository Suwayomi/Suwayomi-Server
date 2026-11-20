package suwayomi.tachidesk.graphql

import io.javalin.http.Context
import io.mockk.mockk
import suwayomi.tachidesk.graphql.server.JavalinGraphQLRequestParser

class RequestParserTest {
    private val ctx = mockk<Context>(relaxed = true)
    private val requestParser = JavalinGraphQLRequestParser()

    // @Test
    // fun testZero() =
    //     runTest {
    //         ctx.jsonMapper()
    //         every { ctx.appAttribute<JsonMapper>(JSON_MAPPER_KEY) } returns
    //             (JavalinJackson(JavalinJackson.defaultMapper()))
    //         every {
    //             ctx.formParam("operations")
    //         } returns
    //             """{ "query": "mutation (${'$'}file: Upload!) {
    //         |singleUpload(file: ${'$'}file) { id } }", "variables": { "file": null }
    //         |}
    //         """.trimMargin()
    //         every { ctx.formParam("map") } returns """{ "0": ["variables.file"] }"""
    //         every { ctx.uploadedFile("0") } returns
    //             UploadedFile(
    //                 ByteArrayInputStream(byteArrayOf()),
    //                 "",
    //                 "",
    //                 "",
    //                 0,
    //             )
    //         val test = requestParser.parseRequest(ctx)
    //         assertIs<GraphQLRequest>(test)
    //         assertNotNull(test.variables?.get("file"))
    //         println("File: " + test.variables?.get("file"))
    //     }
    //
    // @Test
    // fun testTest() =
    //     runTest {
    //         every { ctx.appAttribute<JsonMapper>(JSON_MAPPER_KEY) } returns
    //             (JavalinJackson(JavalinJackson.defaultMapper()))
    //         every {
    //             ctx.formParam("operations")
    //         } returns
    //             """{ "query": "mutation (${'$'}file: Upload!) {
    //         |singleUpload(file: ${'$'}file) { id } }", "variables": { "file": null }
    //         |}
    //         """.trimMargin()
    //         every { ctx.formParam("map") } returns """{ "test": ["variables.file"] }"""
    //         every { ctx.uploadedFile("test") } returns
    //             UploadedFile(
    //                 ByteArrayInputStream(byteArrayOf()),
    //                 "",
    //                 "",
    //                 "",
    //                 0,
    //             )
    //         val test = requestParser.parseRequest(ctx)
    //         assertIs<GraphQLRequest>(test)
    //         assertNotNull(test.variables?.get("file"))
    //         println("File: " + test.variables?.get("file"))
    //     }
    //
    // @Test
    // fun testList() =
    //     runTest {
    //         every { ctx.appAttribute<JsonMapper>(JSON_MAPPER_KEY) } returns
    //             (JavalinJackson(JavalinJackson.defaultMapper()))
    //         every {
    //             ctx.formParam("operations")
    //         } returns
    //             """{ "query": "mutation (${'$'}files: [Upload!]!) {
    //         |singleUpload(files: ${'$'}files) { id } }", "variables": { "files": [null, null] }
    //         |}
    //         """.trimMargin()
    //         every { ctx.formParam("map") } returns
    //             """
    //         { "test": ["variables.files.0"], "test2": ["variables.files.1"] }
    //         """.trimIndent()
    //         every { ctx.uploadedFile("test") } returns
    //             UploadedFile(
    //                 ByteArrayInputStream(byteArrayOf()),
    //                 "",
    //                 "",
    //                 "",
    //                 0,
    //             )
    //         every { ctx.uploadedFile("test2") } returns
    //             UploadedFile(
    //                 ByteArrayInputStream(byteArrayOf()),
    //                 "",
    //                 "",
    //                 "",
    //                 0,
    //             )
    //         val test = requestParser.parseRequest(ctx)
    //         assertIs<GraphQLRequest>(test)
    //         val files = test.variables?.get("files")
    //         assertIs<List<*>>(files)
    //         assert(files.all { it is UploadedFile })
    //         println("Files: $files")
    //     }
}
