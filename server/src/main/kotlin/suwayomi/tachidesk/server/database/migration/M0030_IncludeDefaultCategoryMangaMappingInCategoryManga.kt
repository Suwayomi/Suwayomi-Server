package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0030_IncludeDefaultCategoryMangaMappingInCategoryManga : SQLMigration() {
    override val sql: String = """
       INSERT INTO CATEGORYMANGA (CATEGORY, MANGA) SELECT 0, ID FROM MANGA WHERE IN_LIBRARY AND ID NOT IN (SELECT MANGA FROM CATEGORYMANGA) 
    """.trimIndent()
}
