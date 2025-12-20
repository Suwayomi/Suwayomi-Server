package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.SQLMigration
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.serverConfig

@Suppress("ClassName", "unused")
class M0053_SyncYomi : SQLMigration() {
    override val sql =
        when (serverConfig.databaseType.value) {
            DatabaseType.POSTGRESQL -> postgresQuery()
            DatabaseType.H2 -> h2Query()
        }

    fun postgresQuery(): String =
        """
        ALTER TABLE manga ADD COLUMN version BIGINT DEFAULT 0;
        ALTER TABLE manga ADD COLUMN is_syncing BOOLEAN DEFAULT FALSE;

        ALTER TABLE chapter ADD COLUMN version BIGINT DEFAULT 0;
        ALTER TABLE chapter ADD COLUMN is_syncing BOOLEAN DEFAULT FALSE;


        CREATE OR REPLACE FUNCTION update_manga_version()
        RETURNS trigger AS $$
        BEGIN
            IF NEW.is_syncing = FALSE AND (
                NEW.url IS DISTINCT FROM OLD.url OR
                NEW.description IS DISTINCT FROM OLD.description OR
                NEW.in_library IS DISTINCT FROM OLD.in_library
            ) THEN
                UPDATE manga SET version = version + 1 WHERE id = NEW.id;
            END IF;

            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;

        CREATE TRIGGER update_manga_version
        AFTER UPDATE ON manga
        FOR EACH ROW
        EXECUTE FUNCTION update_manga_version();


        CREATE OR REPLACE FUNCTION update_chapter_and_manga_version()
        RETURNS trigger AS $$
        BEGIN
            IF NEW.is_syncing = FALSE AND (
                NEW.read IS DISTINCT FROM OLD.read OR
                NEW.bookmark IS DISTINCT FROM OLD.bookmark OR
                NEW.last_page_read IS DISTINCT FROM OLD.last_page_read
            ) THEN
                UPDATE chapter SET version = version + 1 WHERE id = NEW.id;
                UPDATE manga SET version = version + 1 WHERE id = NEW.manga AND is_syncing = FALSE;
            END IF;

            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;

        CREATE TRIGGER update_chapter_and_manga_version
        AFTER UPDATE ON chapter
        FOR EACH ROW
        EXECUTE FUNCTION update_chapter_and_manga_version();


        CREATE OR REPLACE FUNCTION insert_manga_category_update_version()
        RETURNS trigger AS $$
        BEGIN
            UPDATE manga SET version = version + 1 WHERE id = NEW.manga AND is_syncing = FALSE;

            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;

        CREATE TRIGGER insert_manga_category_update_version
        AFTER INSERT ON categorymanga
        FOR EACH ROW
        EXECUTE FUNCTION insert_manga_category_update_version();
        """.trimIndent()

    fun h2Query() =
        """
        ALTER TABLE manga ADD COLUMN version BIGINT DEFAULT 0;
        ALTER TABLE manga ADD COLUMN is_syncing BOOLEAN DEFAULT FALSE;

        ALTER TABLE chapter ADD COLUMN version BIGINT DEFAULT 0;
        ALTER TABLE chapter ADD COLUMN is_syncing BOOLEAN DEFAULT FALSE;
        
        CREATE TRIGGER update_manga_version 
        AFTER UPDATE ON manga
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.UpdateMangaVersionTrigger";
        
        CREATE TRIGGER update_chapter_and_manga_version
        AFTER UPDATE ON chapter
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.UpdateChapterAndMangaVersionTrigger";
        
        CREATE TRIGGER insert_manga_category_update_version
        AFTER INSERT ON categorymanga
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.InsertMangaCategoryUpdateVersionTrigger";
        """.trimIndent()
}
