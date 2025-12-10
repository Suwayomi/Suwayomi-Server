package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0053_SyncYomi : SQLMigration() {
    override val sql = """
        ALTER TABLE MANGA ADD COLUMN VERSION BIGINT DEFAULT 0;
        ALTER TABLE MANGA ADD COLUMN IS_SYNCING BOOLEAN DEFAULT 0;

        ALTER TABLE CHAPTER ADD COLUMN VERSION BIGINT DEFAULT 0;
        ALTER TABLE CHAPTER ADD COLUMN IS_SYNCING BOOLEAN DEFAULT 0;
                
        CREATE TRIGGER update_manga_version 
        AFTER UPDATE ON MANGA
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.UpdateMangaVersionTrigger";
        
        CREATE TRIGGER update_chapter_and_manga_version
        AFTER UPDATE ON CHAPTER
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.UpdateChapterAndMangaVersionTrigger";
        
        CREATE TRIGGER insert_manga_category_update_version
        AFTER INSERT ON CATEGORYMANGA
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.InsertMangaCategoryUpdateVersionTrigger";
        """.trimIndent()
}
