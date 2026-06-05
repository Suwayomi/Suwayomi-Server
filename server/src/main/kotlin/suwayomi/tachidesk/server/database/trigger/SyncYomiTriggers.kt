package suwayomi.tachidesk.server.database.trigger

import org.h2.tools.TriggerAdapter
import java.sql.Connection
import java.sql.ResultSet
import kotlin.random.Random
import kotlin.time.Clock

@Suppress("unused")
class UpdateMangaVersionTrigger : TriggerAdapter() {
    override fun fire(
        conn: Connection,
        oldRow: ResultSet,
        newRow: ResultSet,
    ) {
        val isSyncing = newRow.getBoolean("is_syncing")
        val hasChanged =
            oldRow.getString("url") != newRow.getString("url") ||
                oldRow.getString("description") != newRow.getString("description") ||
                oldRow.getBoolean("in_library") != newRow.getBoolean("in_library")

        if (!isSyncing && hasChanged) {
            val currentVersion = newRow.getLong("version")
            newRow.updateLong("version", currentVersion + 1)
        }
    }
}

@Suppress("unused")
class UpdateChapterAndMangaVersionTrigger : TriggerAdapter() {
    override fun fire(
        conn: Connection,
        oldRow: ResultSet,
        newRow: ResultSet,
    ) {
        val isSyncing = newRow.getBoolean("is_syncing")
        val hasChanged =
            oldRow.getBoolean("read") != newRow.getBoolean("read") ||
                oldRow.getBoolean("bookmark") != newRow.getBoolean("bookmark") ||
                oldRow.getInt("last_page_read") != newRow.getInt("last_page_read")

        if (!isSyncing && hasChanged) {
            val currentVersion = newRow.getLong("version")
            newRow.updateLong("version", currentVersion + 1)

            val mangaId = newRow.getInt("manga")
            conn
                .prepareStatement(
                    "UPDATE MANGA SET version = version + 1 WHERE id = ? AND NOT is_syncing",
                ).use {
                    it.setInt(1, mangaId)
                    it.executeUpdate()
                }
        }
    }
}

@Suppress("unused")
class UpdateMangaLastModifiedAtTrigger : TriggerAdapter() {
    override fun fire(
        conn: Connection,
        oldRow: ResultSet?,
        newRow: ResultSet,
    ) {
        newRow.updateLong("last_modified_at", Clock.System.now().epochSeconds)
    }
}

@Suppress("unused")
class UpdateChapterLastModifiedAtTrigger : TriggerAdapter() {
    override fun fire(
        conn: Connection,
        oldRow: ResultSet?,
        newRow: ResultSet,
    ) {
        newRow.updateLong("last_modified_at", Clock.System.now().epochSeconds)
    }
}

@Suppress("unused")
class InsertMangaCategoryUpdateVersionTrigger : TriggerAdapter() {
    override fun fire(
        conn: Connection,
        oldRow: ResultSet?,
        newRow: ResultSet,
    ) {
        val mangaId = newRow.getInt("manga")

        conn
            .prepareStatement(
                "UPDATE MANGA SET version = version + 1 WHERE id = ? AND NOT is_syncing",
            ).use {
                it.setInt(1, mangaId)
                it.executeUpdate()
            }
    }
}

@Suppress("unused")
class InsertCategoryUidTrigger : TriggerAdapter() {
    override fun fire(
        conn: Connection,
        oldRow: ResultSet?,
        newRow: ResultSet,
    ) {
        if (newRow.getLong("uid") == 0L) {
            newRow.updateLong("uid", Random.nextLong(1, Long.MAX_VALUE))
        }

        if (newRow.getLong("last_modified_at") == 0L) {
            newRow.updateLong(
                "last_modified_at",
                Clock.System.now().epochSeconds,
            )
        }
    }
}

@Suppress("unused")
class UpdateCategoryVersionTrigger : TriggerAdapter() {
    override fun fire(
        conn: Connection,
        oldRow: ResultSet,
        newRow: ResultSet,
    ) {
        val isSyncing = newRow.getBoolean("is_syncing")
        val hasChanged =
            oldRow.getString("name") != newRow.getString("name") ||
                oldRow.getInt("sort_order") != newRow.getInt("sort_order")

        if (!isSyncing && hasChanged) {
            val currentVersion = newRow.getLong("version")
            newRow.updateLong("version", currentVersion + 1)

            newRow.updateLong(
                "last_modified_at",
                Clock.System.now().epochSeconds,
            )
        }
    }
}
