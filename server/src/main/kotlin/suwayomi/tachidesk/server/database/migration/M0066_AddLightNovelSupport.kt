package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.SQLMigration
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.serverConfig

@Suppress("ClassName", "unused")
class M0066_AddLightNovelSupport : SQLMigration() {
    override val sql: String =
        """
        ALTER TABLE extensionstore ADD COLUMN IF NOT EXISTS kind VARCHAR(16) NOT NULL DEFAULT 'JVM';
        ALTER TABLE extension ADD COLUMN IF NOT EXISTS runtime_kind VARCHAR(16) NOT NULL DEFAULT 'JVM';
        ALTER TABLE extension ADD COLUMN IF NOT EXISTS plugin_id VARCHAR(256) NULL;
        ALTER TABLE extension ADD COLUMN IF NOT EXISTS site_url VARCHAR(2048) NULL;
        ALTER TABLE extension ADD COLUMN IF NOT EXISTS code_url VARCHAR(2048) NULL;
        ALTER TABLE extension ADD COLUMN IF NOT EXISTS custom_js_url VARCHAR(2048) NULL;
        ALTER TABLE extension ADD COLUMN IF NOT EXISTS custom_css_url VARCHAR(2048) NULL;
        ALTER TABLE extension ALTER COLUMN extension_lib DROP NOT NULL;
        ALTER TABLE manga ADD COLUMN IF NOT EXISTS content_type VARCHAR(16) NOT NULL DEFAULT 'MANGA';
        CREATE INDEX IF NOT EXISTS manga_content_type_idx ON manga (content_type);
        ALTER TABLE category ADD COLUMN IF NOT EXISTS content_type VARCHAR(16) NOT NULL DEFAULT 'MANGA';
        """.trimIndent() +
            "\n" +
            when (serverConfig.databaseType.value) {
                DatabaseType.POSTGRESQL -> postgresContentTypeTriggers()
                DatabaseType.H2 -> ""
            }

    private fun postgresContentTypeTriggers(): String =
        """
        CREATE OR REPLACE FUNCTION update_manga_version()
        RETURNS trigger AS $$
        BEGIN
            IF NOT NEW.is_syncing
               AND ROW(NEW.url, NEW.description, NEW.in_library, NEW.content_type)
                   IS DISTINCT FROM
                   ROW(OLD.url, OLD.description, OLD.in_library, OLD.content_type)
            THEN
                NEW.version := OLD.version + 1;
            END IF;

            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;

        CREATE OR REPLACE FUNCTION update_manga_last_modified_at()
        RETURNS trigger AS $$
        BEGIN
            IF NEW.is_syncing THEN
                RETURN NEW;
            END IF;

            IF TG_OP = 'UPDATE'
               AND ROW(NEW.url, NEW.description, NEW.in_library, NEW.content_type, NEW.version)
                   IS NOT DISTINCT FROM
                   ROW(OLD.url, OLD.description, OLD.in_library, OLD.content_type, OLD.version)
            THEN
                RETURN NEW;
            END IF;

            NEW.last_modified_at := EXTRACT(EPOCH FROM NOW());
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;
        """.trimIndent()
}
