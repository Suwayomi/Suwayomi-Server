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

    // language=postgresql
    fun postgresQuery(): String =
        """
        ALTER TABLE manga ADD COLUMN version BIGINT DEFAULT 0;
        ALTER TABLE manga ADD COLUMN is_syncing BOOLEAN DEFAULT FALSE;

        ALTER TABLE chapter ADD COLUMN version BIGINT DEFAULT 0;
        ALTER TABLE chapter ADD COLUMN is_syncing BOOLEAN DEFAULT FALSE;


        CREATE OR REPLACE FUNCTION update_manga_version()
        RETURNS trigger AS $$
        BEGIN
            IF NOT NEW.is_syncing
               AND ROW(NEW.url, NEW.description, NEW.in_library)
                   IS DISTINCT FROM
                   ROW(OLD.url, OLD.description, OLD.in_library)
            THEN
                NEW.version := OLD.version + 1;
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
            IF NOT NEW.is_syncing
               AND ROW(NEW.read, NEW.bookmark, NEW.last_page_read)
                   IS DISTINCT FROM
                   ROW(OLD.read, OLD.bookmark, OLD.last_page_read)
            THEN
                NEW.version := OLD.version + 1;
        
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

    // language=h2
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
