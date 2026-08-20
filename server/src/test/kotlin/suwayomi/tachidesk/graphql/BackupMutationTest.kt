package suwayomi.tachidesk.graphql

import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import kotlin.test.Test
import kotlin.test.assertNotNull

class BackupMutationTest : GraphQLTest() {
    @Test
    fun createBackup() {
        createLibraryManga("Manga to Backup")

        val response =
            graphql(
                """
                mutation(${'$'}input: CreateBackupInput) {
                    createBackup(input: ${'$'}input) {
                        url
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("flags" to mapOf("includeChapters" to true, "includeCategories" to true))),
            )

        response.assertNoErrors()
        assertNotNull(response.dataPath("createBackup", "url"), "a backup url should be returned")
    }

    @AfterEach
    fun tearDown() {
        clearTables(
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )
    }
}
