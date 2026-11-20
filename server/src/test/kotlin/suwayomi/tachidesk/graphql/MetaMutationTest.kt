package suwayomi.tachidesk.graphql

import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.global.model.table.GlobalMetaTable
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import kotlin.test.Test
import kotlin.test.assertEquals

class MetaMutationTest : GraphQLTest() {
    @Test
    fun setGlobalMeta() {
        val response =
            graphql(
                """
                mutation(${'$'}input: SetGlobalMetaInput!) {
                    setGlobalMeta(input: ${'$'}input) {
                        meta {
                            key
                            value
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("meta" to mapOf("key" to "gKey", "value" to "gValue"))),
            )

        response.assertNoErrors()
        assertEquals("gKey", response.dataPath("setGlobalMeta", "meta", "key"))
        assertEquals("gValue", response.dataPath("setGlobalMeta", "meta", "value"))
    }

    @Test
    fun deleteGlobalMeta() {
        graphql(
            """
            mutation(${'$'}input: SetGlobalMetaInput!) {
                setGlobalMeta(input: ${'$'}input) {
                    meta {
                        key
                    }
                }
            }
            """.trimIndent(),
            mapOf("input" to mapOf("meta" to mapOf("key" to "gKey", "value" to "gValue"))),
        )

        val response =
            graphql(
                """
                mutation(${'$'}input: DeleteGlobalMetaInput!) {
                    deleteGlobalMeta(input: ${'$'}input) {
                        meta {
                            key
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("key" to "gKey")),
            )

        response.assertNoErrors()
        assertEquals("gKey", response.dataPath("deleteGlobalMeta", "meta", "key"))
    }

    @Test
    fun setGlobalMetas() {
        val response =
            graphql(
                """
                mutation(${'$'}input: SetGlobalMetasInput!) {
                    setGlobalMetas(input: ${'$'}input) {
                        metas {
                            key
                            value
                        }
                    }
                }
                """.trimIndent(),
                mapOf(
                    "input" to
                        mapOf(
                            "metas" to
                                listOf(
                                    mapOf("key" to "k1", "value" to "v1"),
                                    mapOf("key" to "k2", "value" to "v2"),
                                ),
                        ),
                ),
            )

        response.assertNoErrors()
        assertEquals(2, (response.dataPath("setGlobalMetas", "metas") as List<*>).size)
    }

    @Test
    fun deleteGlobalMetasByKeys() {
        graphql(
            """
            mutation(${'$'}input: SetGlobalMetasInput!) {
                setGlobalMetas(input: ${'$'}input) {
                    metas {
                        key
                    }
                }
            }
            """.trimIndent(),
            mapOf(
                "input" to
                    mapOf(
                        "metas" to
                            listOf(
                                mapOf("key" to "k1", "value" to "v1"),
                                mapOf("key" to "k2", "value" to "v2"),
                            ),
                    ),
            ),
        )

        val response =
            graphql(
                """
                mutation(${'$'}input: DeleteGlobalMetasInput!) {
                    deleteGlobalMetas(input: ${'$'}input) {
                        metas {
                            key
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("keys" to listOf("k1", "k2"))),
            )

        response.assertNoErrors()
        assertEquals(2, (response.dataPath("deleteGlobalMetas", "metas") as List<*>).size)
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(GlobalMetaTable)
    }
}
