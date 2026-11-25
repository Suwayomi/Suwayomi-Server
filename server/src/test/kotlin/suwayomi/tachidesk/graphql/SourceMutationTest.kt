package suwayomi.tachidesk.graphql

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.chapter.SourceException
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceMetaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
                    "input" to
                        mapOf(
                            "items" to
                                listOf(
                                    mapOf(
                                        "sourceIds" to listOf(sourceId),
                                        "metas" to
                                            listOf(
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
                "input" to
                    mapOf(
                        "items" to
                            listOf(
                                mapOf(
                                    "sourceIds" to listOf(sourceId),
                                    "metas" to
                                        listOf(
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

    private fun fetchLatest(
        sourceId: Long,
        user: UserType = admin,
    ) = graphql(
        """
        mutation(${'$'}input: FetchSourceMangaInput!) {
            fetchSourceManga(input: ${'$'}input) {
                mangas {
                    title
                }
                hasNextPage
            }
        }
        """.trimIndent(),
        mapOf("input" to mapOf("source" to sourceId.toString(), "type" to "LATEST", "page" to 1)),
        user = user,
    )

    @Test
    fun fetchLatestOnAMissingSourceIsNotFound() {
        val error = fetchLatest(987654321L).errors!!.single()

        assertEquals("NOT_FOUND", error.extensions?.get("code"))
        assertEquals("Source not found", error.message)
        assertEquals(listOf("fetchSourceManga"), error.path)
        assertEquals(1, error.locations?.size)
    }

    @Test
    fun fetchLatestOnAStubSourceIsNotFound() {
        val sourceId = 424243L
        GetSource.registerSource(sourceId to StubSource(sourceId))
        try {
            val error = fetchLatest(sourceId).errors!!.single()

            assertEquals("NOT_FOUND", error.extensions?.get("code"))
            assertEquals("Source not found", error.message)
        } finally {
            GetSource.unregisterSource(sourceId)
        }
    }

    @Test
    fun fetchLatestOnASourceWithoutLatestSupportIsABadRequest() {
        val sourceId = 424242L
        val source =
            mockk<Source> {
                every { supportsLatest } returns false
            }
        GetSource.registerSource(sourceId to source)
        try {
            val error = fetchLatest(sourceId).errors!!.single()

            assertEquals("BAD_REQUEST", error.extensions?.get("code"))
            assertEquals("Source does not support latest", error.message)
        } finally {
            GetSource.unregisterSource(sourceId)
        }
    }

    @Test
    fun fetchLatestOnAnExtensionWithoutAnApkIsALoadFailure() {
        val sourceId = 424246L
        val extensionId =
            transaction {
                ExtensionTable.insertAndGetId {
                    it[apkName] = null
                    it[name] = "noapk"
                    it[pkgName] = "noapk"
                    it[versionName] = "1.0"
                    it[versionCode] = 1
                    it[lang] = "en"
                    it[contentWarning] = 0
                    it[extensionLib] = "1.5"
                    it[classFQName] = "noapk.Extension"
                }
            }
        transaction {
            SourceTable.insert {
                it[id] = EntityID(sourceId, SourceTable)
                it[name] = "noapk"
                it[lang] = "en"
                it[extension] = extensionId
            }
        }
        try {
            val error = fetchLatest(sourceId).errors!!.single()

            assertEquals("INTERNAL_SERVER_ERROR", error.extensions?.get("code"))
            assertEquals("Source $sourceId failed to load", error.message)
        } finally {
            GetSource.unregisterSource(sourceId)
            transaction {
                SourceTable.deleteWhere { SourceTable.id eq sourceId }
                ExtensionTable.deleteWhere { ExtensionTable.id eq extensionId }
            }
        }
    }

    @Test
    fun fetchLatestOnAnUpstreamHttpErrorIsAFailedDependency() {
        val sourceId = 424247L
        val source =
            mockk<Source> {
                every { supportsLatest } returns true
                coEvery { getLatestUpdates(1) } throws HttpException(503)
            }
        GetSource.registerSource(sourceId to source)
        try {
            val error = fetchLatest(sourceId).errors!!.single()

            assertEquals("FAILED_DEPENDENCY", error.extensions?.get("code"))
            assertEquals("HTTP error 503", error.message)
        } finally {
            GetSource.unregisterSource(sourceId)
        }
    }

    @Test
    fun fetchLatestOnASourceExceptionIsAFailedDependency() {
        val sourceId = 424248L
        val source =
            mockk<Source> {
                every { supportsLatest } returns true
                coEvery { getLatestUpdates(1) } throws SourceException(IOException("decode failed"))
            }
        GetSource.registerSource(sourceId to source)
        try {
            val error = fetchLatest(sourceId).errors!!.single()

            assertEquals("FAILED_DEPENDENCY", error.extensions?.get("code"))
            assertEquals("Source request failed", error.message)
        } finally {
            GetSource.unregisterSource(sourceId)
        }
    }

    @Test
    fun fetchLatestOnATimeoutIsAFailedDependency() {
        val sourceId = 424249L
        val source =
            mockk<Source> {
                every { supportsLatest } returns true
                coEvery { getLatestUpdates(1) } throws InterruptedIOException("timeout")
            }
        GetSource.registerSource(sourceId to source)
        try {
            val error = fetchLatest(sourceId).errors!!.single()

            assertEquals("FAILED_DEPENDENCY", error.extensions?.get("code"))
            assertEquals("Source request failed", error.message)
        } finally {
            GetSource.unregisterSource(sourceId)
        }
    }

    @Test
    fun fetchLatestOnAPlainIoFailureKeepsTheUpstreamFormat() {
        val sourceId = 424250L
        val source =
            mockk<Source> {
                every { supportsLatest } returns true
                coEvery { getLatestUpdates(1) } throws IOException("disk gone")
            }
        GetSource.registerSource(sourceId to source)
        try {
            val error = fetchLatest(sourceId).errors!!.single()

            assertNull(error.extensions?.get("code"))
            assertTrue(error.message.contains("java.io.IOException"))
        } finally {
            GetSource.unregisterSource(sourceId)
        }
    }

    @Test
    fun fetchLatestOnALinkageFailureIsALoadFailure() {
        val sourceId = 424251L
        mockkObject(GetSource)
        try {
            coEvery { GetSource.getSource(sourceId) } throws NoClassDefFoundError("eu.kanade.tachiyomi.source.HttpSource")
            val error = fetchLatest(sourceId).errors!!.single()

            assertEquals("INTERNAL_SERVER_ERROR", error.extensions?.get("code"))
            assertEquals("Source $sourceId failed to load", error.message)
        } finally {
            unmockkObject(GetSource)
        }
    }

    @Test
    fun fetchLatestWithoutAUserIsUnauthenticated() {
        val error = fetchLatest(0L, UserType.Visitor).errors!!.single()

        assertEquals("UNAUTHENTICATED", error.extensions?.get("code"))
    }

    @Test
    fun fetchLatestOnASupportingSourceSucceeds() {
        val sourceId = 424245L
        val entry =
            SManga.create().apply {
                url = "/latest-manga"
                title = "Latest"
            }
        val source =
            mockk<Source> {
                every { supportsLatest } returns true
                coEvery { getLatestUpdates(1) } returns MangasPage(listOf(entry), false)
            }
        GetSource.registerSource(sourceId to source)
        try {
            val response = fetchLatest(sourceId)

            response.assertNoErrors()
            assertEquals("Latest", response.dataPath("fetchSourceManga", "mangas", "0", "title"))
            assertEquals(false, response.dataPath("fetchSourceManga", "hasNextPage"))
        } finally {
            GetSource.unregisterSource(sourceId)
        }
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            SourceMetaTable,
            MangaTable,
        )
    }
}
