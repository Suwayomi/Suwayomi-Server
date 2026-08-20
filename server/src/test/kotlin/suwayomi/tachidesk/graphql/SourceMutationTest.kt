package suwayomi.tachidesk.graphql

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.model.table.SourceMetaTable
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables

class SourceMutationTest : GraphQLTest() {
    // the local source registered in the test setup has id 0
    private val sourceId: String = "0"

    @Test
    fun setSourceMeta() {
        val response =
            graphql(
                """
                mutation(${'$'}input: SetSourceMetaInput!) {
                    setSourceMeta(input: ${'$'}input) {
                        meta {
                            key
                            value
                            sourceId
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("meta" to mapOf("key" to "sKey", "value" to "sValue", "sourceId" to sourceId))),
            )

        response.assertNoErrors()
        assertEquals("sKey", response.dataPath("setSourceMeta", "meta", "key"))
        assertEquals(sourceId, response.dataPath("setSourceMeta", "meta", "sourceId"))
    }

    @Test
    fun deleteSourceMeta() {
        graphql(
            """
            mutation(${'$'}input: SetSourceMetaInput!) {
                setSourceMeta(input: ${'$'}input) {
                    meta {
                        key
                    }
                }
            }
            """.trimIndent(),
            mapOf("input" to mapOf("meta" to mapOf("key" to "sKey", "value" to "sValue", "sourceId" to sourceId))),
        )

        val response =
            graphql(
                """
                mutation(${'$'}input: DeleteSourceMetaInput!) {
                    deleteSourceMeta(input: ${'$'}input) {
                        meta {
                            key
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("sourceId" to sourceId, "key" to "sKey")),
            )

        response.assertNoErrors()
        assertEquals("sKey", response.dataPath("deleteSourceMeta", "meta", "key"))
    }

    @Test
    fun setSourceMetas() {
        val response =
            graphql(
                """
                mutation(${'$'}input: SetSourceMetasInput!) {
                    setSourceMetas(input: ${'$'}input) {
                        metas {
                            key
                        }
                    }
                }
                """.trimIndent(),
                mapOf(
                    "input" to mapOf(
                        "items" to listOf(
                            mapOf(
                                "sourceIds" to listOf(sourceId),
                                "metas" to listOf(
                                    mapOf("key" to "sk1", "value" to "sv1"),
                                    mapOf("key" to "sk2", "value" to "sv2"),
                                ),
                            ),
                        ),
                    ),
                ),
            )

        response.assertNoErrors()
        assertEquals(2, (response.dataPath("setSourceMetas", "metas") as List<*>).size)
    }

    @Test
    fun deleteSourceMetas() {
        graphql(
            """
            mutation(${'$'}input: SetSourceMetasInput!) {
                setSourceMetas(input: ${'$'}input) {
                    metas {
                        key
                    }
                }
            }
            """.trimIndent(),
            mapOf(
                "input" to mapOf(
                    "items" to listOf(
                        mapOf(
                            "sourceIds" to listOf(sourceId),
                            "metas" to listOf(
                                mapOf("key" to "sk1", "value" to "sv1"),
                                mapOf("key" to "sk2", "value" to "sv2"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val response =
            graphql(
                """
                mutation(${'$'}input: DeleteSourceMetasInput!) {
                    deleteSourceMetas(input: ${'$'}input) {
                        metas {
                            key
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("items" to listOf(mapOf("sourceIds" to listOf(sourceId), "keys" to listOf("sk1", "sk2"))))),
            )

        response.assertNoErrors()
        assertEquals(2, (response.dataPath("deleteSourceMetas", "metas") as List<*>).size)
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(SourceMetaTable)
    }
}
