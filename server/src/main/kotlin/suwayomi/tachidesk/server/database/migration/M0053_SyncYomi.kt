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
        ALTER TABLE manga ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
        ALTER TABLE manga ADD COLUMN is_syncing BOOLEAN NOT NULL DEFAULT FALSE;
        ALTER TABLE manga ADD COLUMN last_modified_at BIGINT NOT NULL DEFAULT 0;

        ALTER TABLE chapter ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
        ALTER TABLE chapter ADD COLUMN is_syncing BOOLEAN NOT NULL DEFAULT FALSE;
        ALTER TABLE chapter ADD COLUMN last_modified_at BIGINT NOT NULL DEFAULT 0;

        ALTER TABLE category ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
        ALTER TABLE category ADD COLUMN uid BIGINT NOT NULL DEFAULT 0;
        ALTER TABLE category ADD COLUMN is_syncing BOOLEAN NOT NULL DEFAULT FALSE;
        ALTER TABLE category ADD COLUMN last_modified_at BIGINT NOT NULL DEFAULT 0;


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


        CREATE OR REPLACE FUNCTION update_manga_last_modified_at()
        RETURNS trigger AS $$
        BEGIN
            NEW.last_modified_at := EXTRACT(EPOCH FROM NOW());
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;
        
        CREATE TRIGGER update_manga_last_modified_at
        BEFORE UPDATE OR INSERT ON manga
        FOR EACH ROW
        EXECUTE FUNCTION update_manga_last_modified_at();
        
        
        CREATE OR REPLACE FUNCTION update_chapter_last_modified_at()
        RETURNS trigger AS $$
        BEGIN
            NEW.last_modified_at := EXTRACT(EPOCH FROM NOW());
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;
        
        CREATE TRIGGER update_chapter_last_modified_at
        BEFORE UPDATE OR INSERT ON chapter
        FOR EACH ROW
        EXECUTE FUNCTION update_chapter_last_modified_at();


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
        

        CREATE OR REPLACE FUNCTION insert_category_uid()
        RETURNS trigger AS $$
        BEGIN
            IF NEW.uid = 0 THEN
                NEW.uid := RANDOM(1, 9223372036854775807);
            END IF;

            IF NEW.last_modified_at = 0 THEN
                NEW.last_modified_at := EXTRACT(EPOCH FROM NOW());
            END IF;

            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;
        
        CREATE TRIGGER insert_category_uid
        BEFORE INSERT ON category
        FOR EACH ROW
        EXECUTE FUNCTION insert_category_uid();
        
        
        CREATE OR REPLACE FUNCTION update_category_version()
        RETURNS trigger AS $$
        BEGIN
            IF NOT NEW.is_syncing
               AND ROW(NEW.name, NEW.sort_order)
                   IS DISTINCT FROM
                   ROW(OLD.name, OLD.sort_order)
            THEN
                NEW.version := NEW.version + 1;
                NEW.last_modified_at := EXTRACT(EPOCH FROM NOW());
            END IF;

            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;
        
        CREATE TRIGGER update_category_version
        BEFORE UPDATE ON category
        FOR EACH ROW
        EXECUTE FUNCTION update_category_version();
        """.trimIndent()

    // language=h2
    fun h2Query() =
        """
        ALTER TABLE manga ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
        ALTER TABLE manga ADD COLUMN is_syncing BOOLEAN NOT NULL DEFAULT FALSE;
        ALTER TABLE manga ADD COLUMN last_modified_at BIGINT NOT NULL DEFAULT 0;

        ALTER TABLE chapter ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
        ALTER TABLE chapter ADD COLUMN is_syncing BOOLEAN NOT NULL DEFAULT FALSE;
        ALTER TABLE chapter ADD COLUMN last_modified_at BIGINT NOT NULL DEFAULT 0;

        ALTER TABLE category ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
        ALTER TABLE category ADD COLUMN uid BIGINT NOT NULL DEFAULT 0;
        ALTER TABLE category ADD COLUMN is_syncing BOOLEAN NOT NULL DEFAULT FALSE;
        ALTER TABLE category ADD COLUMN last_modified_at BIGINT NOT NULL DEFAULT 0;
        

        CREATE TRIGGER update_manga_version 
        AFTER UPDATE ON manga
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.UpdateMangaVersionTrigger";
        
        CREATE TRIGGER update_chapter_and_manga_version
        AFTER UPDATE ON chapter
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.UpdateChapterAndMangaVersionTrigger";
        
        CREATE TRIGGER update_manga_last_modified_at
        BEFORE UPDATE ON manga
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.UpdateMangaLastModifiedAtTrigger";
        
        CREATE TRIGGER insert_manga_last_modified_at
        BEFORE INSERT ON manga
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.UpdateMangaLastModifiedAtTrigger";
        
        CREATE TRIGGER update_chapter_last_modified_at
        BEFORE UPDATE ON chapter
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.UpdateChapterLastModifiedAtTrigger";
        
        CREATE TRIGGER insert_chapter_last_modified_at
        BEFORE INSERT ON chapter
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.UpdateChapterLastModifiedAtTrigger";
        
        CREATE TRIGGER insert_manga_category_update_version
        AFTER INSERT ON categorymanga
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.InsertMangaCategoryUpdateVersionTrigger";
        
        CREATE TRIGGER insert_category_uid
        BEFORE INSERT ON category
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.InsertCategoryUidTrigger";
        
        CREATE TRIGGER update_category_version
        BEFORE UPDATE ON category
        FOR EACH ROW
        CALL "suwayomi.tachidesk.server.database.trigger.UpdateCategoryVersionTrigger";
        """.trimIndent()
}
