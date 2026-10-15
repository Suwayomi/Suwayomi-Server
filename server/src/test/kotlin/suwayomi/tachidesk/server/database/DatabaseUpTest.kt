package suwayomi.tachidesk.server.database

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.ExitCode
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DatabaseUpTest : ApplicationTest() {
    private val userId = 1

    private var mangaRowId: Int = 0
    private var chapterRowId: Int = 0
    private var categoryRowId: Int = 0

    @BeforeEach
    fun setUp() {
        transaction {
            mangaRowId =
                MangaTable
                    .insertAndGetId {
                        it[MangaTable.url] = "/m"
                        it[MangaTable.title] = "Manga"
                        it[MangaTable.description] = "d"
                        it[MangaTable.sourceReference] = 1
                    }.value

            chapterRowId =
                ChapterTable
                    .insertAndGetId {
                        it[ChapterTable.url] = "/c"
                        it[ChapterTable.name] = "c1"
                        it[ChapterTable.sourceOrder] = 1
                        it[ChapterTable.manga] = mangaRowId
                    }.value

            categoryRowId =
                CategoryTable
                    .insertAndGetId {
                        it[CategoryTable.name] = "Reading"
                        it[CategoryTable.user] = userId
                    }.value
        }
    }

    @AfterEach
    fun tearDown() {
        transaction {
            MangaUserTable.deleteWhere {
                (MangaUserTable.manga eq mangaRowId) and (MangaUserTable.user eq userId)
            }
            ChapterUserTable.deleteWhere {
                (ChapterUserTable.chapter eq chapterRowId) and (ChapterUserTable.user eq userId)
            }
            ChapterTable.deleteWhere { ChapterTable.id eq chapterRowId }
            MangaTable.deleteWhere { MangaTable.id eq mangaRowId }
            CategoryTable.deleteWhere { CategoryTable.id eq categoryRowId }
        }
    }

    @Test
    fun `setup failure requests shutdown instead of running on without a database`() {
        // U1-D: a database that cannot be set up is fatal. The shutdown handler seam
        // captures the request instead of exiting the test JVM.
        // The shared in-memory H2 survives this failure path only because
        // ApplicationTest.databaseSetup connects with DB_CLOSE_DELAY=-1 (keeps the DB
        // alive across connection closes) and DBManager.connectedDb is null in tests,
        // so setupDatabase() never closeAndUnregister()s the test Database. If that
        // changes, this test must re-run databaseSetup() to re-register the shared db.
        val captured = mutableListOf<ExitCode>()
        val originalHandler = databaseShutdownHandler
        databaseShutdownHandler = { captured += it }

        val previousType = serverConfig.databaseType.value
        val previousUrl = serverConfig.databaseUrl.value
        val previousPool = serverConfig.useHikariConnectionPool.value
        try {
            serverConfig.databaseType.value = DatabaseType.POSTGRESQL
            // refused connection — fails fast in HikariDataSource's constructor
            serverConfig.databaseUrl.value = "postgresql://127.0.0.1:1/suwayomi"
            serverConfig.useHikariConnectionPool.value = true

            databaseUp()
        } finally {
            databaseShutdownHandler = originalHandler
            serverConfig.databaseType.value = previousType
            serverConfig.databaseUrl.value = previousUrl
            serverConfig.useHikariConnectionPool.value = previousPool
        }

        assertEquals(listOf(ExitCode.DbMigrationFailure), captured)
    }

    @Test
    fun `databaseUp resets stale is_syncing flags without bumping versions`() {
        // U1-F: is_syncing = true at startup can only be crash residue; databaseUp clears it
        // and the reset itself must not move version/last_modified_at watermarks.
        val db = transaction { TransactionManager.current().db }

        var mangaUserId = 0
        var chapterUserId = 0
        transaction(db) {
            mangaUserId =
                MangaUserTable
                    .insertAndGetId {
                        it[MangaUserTable.manga] = mangaRowId
                        it[MangaUserTable.user] = userId
                        it[MangaUserTable.isSyncing] = true
                    }.value
            chapterUserId =
                ChapterUserTable
                    .insertAndGetId {
                        it[ChapterUserTable.chapter] = chapterRowId
                        it[ChapterUserTable.user] = userId
                        it[ChapterUserTable.isSyncing] = true
                    }.value
            CategoryTable.update({ CategoryTable.id eq categoryRowId }) {
                it[CategoryTable.isSyncing] = true
            }
        }

        val stateBefore =
            transaction(db) {
                Triple(
                    MangaUserTable
                        .selectAll()
                        .where { MangaUserTable.id eq mangaUserId }
                        .single()
                        .let { it[MangaUserTable.version] to it[MangaUserTable.lastModifiedAt] },
                    ChapterUserTable
                        .selectAll()
                        .where { ChapterUserTable.id eq chapterUserId }
                        .single()
                        .let { it[ChapterUserTable.version] to it[ChapterUserTable.lastModifiedAt] },
                    CategoryTable
                        .selectAll()
                        .where { CategoryTable.id eq categoryRowId }
                        .single()[CategoryTable.version],
                )
            }

        // re-run startup against the shared database: migrations are already applied, so
        // this exercises the is_syncing reset path
        databaseUp(db)

        transaction(db) {
            val mangaUser =
                MangaUserTable
                    .selectAll()
                    .where { MangaUserTable.id eq mangaUserId }
                    .single()
            assertFalse(mangaUser[MangaUserTable.isSyncing])
            assertEquals(
                stateBefore.first,
                mangaUser[MangaUserTable.version] to mangaUser[MangaUserTable.lastModifiedAt],
            )

            val chapterUser =
                ChapterUserTable
                    .selectAll()
                    .where { ChapterUserTable.id eq chapterUserId }
                    .single()
            assertFalse(chapterUser[ChapterUserTable.isSyncing])
            assertEquals(
                stateBefore.second,
                chapterUser[ChapterUserTable.version] to chapterUser[ChapterUserTable.lastModifiedAt],
            )

            val category =
                CategoryTable
                    .selectAll()
                    .where { CategoryTable.id eq categoryRowId }
                    .single()
            assertFalse(category[CategoryTable.isSyncing])
            assertEquals(stateBefore.third, category[CategoryTable.version])
        }
    }
}
