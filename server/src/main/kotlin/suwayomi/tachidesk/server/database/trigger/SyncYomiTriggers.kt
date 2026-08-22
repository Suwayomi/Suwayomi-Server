package suwayomi.tachidesk.server.database.trigger

import org.h2.tools.TriggerAdapter
import java.sql.Connection
import java.sql.ResultSet
import kotlin.random.Random
import kotlin.time.Clock

@Deprecated("Removed, but needs to be kept due to migrations")
class UpdateMangaVersionTrigger : DeprecatedTrigger()

@Suppress("unused")
class UpdateMangaUserVersionTrigger : TriggerAdapter() {
    override fun fire(
        conn: Connection,
        oldRow: ResultSet,
        newRow: ResultSet,
    ) {
        val isSyncing = newRow.getBoolean("is_syncing")
        val hasChanged =
            oldRow.getBoolean("in_library") != newRow.getBoolean("in_library") ||
                oldRow.getLong("in_library_at") != newRow.getLong("in_library_at")

        if (!isSyncing && hasChanged) {
            val currentVersion = newRow.getLong("version")
            newRow.updateLong("version", currentVersion + 1)
        }
    }
}

@Deprecated("Removed, but needs to be kept due to migrations")
class UpdateChapterAndMangaVersionTrigger : DeprecatedTrigger()

@Suppress("unused")
class UpdateChapterUserVersionTrigger : TriggerAdapter() {
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

            val chapterId = newRow.getInt("chapter")
            val userId = newRow.getInt("user_id")
            // language=h2
            conn
                .prepareStatement(
                    "UPDATE MANGAUSER SET version = version + 1 " +
                        "WHERE user_id = ? AND NOT is_syncing " +
                        "AND manga = (SELECT manga FROM CHAPTER WHERE id = ?)",
                ).use {
                    it.setInt(1, userId)
                    it.setInt(2, chapterId)
                    it.executeUpdate()
                }
        }
    }
}

@Deprecated("Removed, but needs to be kept due to migrations")
class UpdateMangaLastModifiedAtTrigger : DeprecatedTrigger()

@Suppress("unused")
class UpdateMangaUserLastModifiedAtTrigger : TriggerAdapter() {
    override fun fire(
        conn: Connection,
        oldRow: ResultSet?,
        newRow: ResultSet,
    ) {
        newRow.updateLong("last_modified_at", Clock.System.now().epochSeconds)
    }
}

@Deprecated("Removed, but needs to be kept due to migrations")
class UpdateChapterLastModifiedAtTrigger : DeprecatedTrigger()

@Suppress("unused")
class UpdateChapterUserLastModifiedAtTrigger : TriggerAdapter() {
    override fun fire(
        conn: Connection,
        oldRow: ResultSet?,
        newRow: ResultSet,
    ) {
        newRow.updateLong("last_modified_at", Clock.System.now().epochSeconds)
    }
}

@Suppress("unused")
class UpdateMangaBumpUserVersionsTrigger : TriggerAdapter() {
    override fun fire(
        conn: Connection,
        oldRow: ResultSet,
        newRow: ResultSet,
    ) {
        val hasChanged =
            oldRow.getString("url") != newRow.getString("url") ||
                oldRow.getString("description") != newRow.getString("description")

        if (hasChanged) {
            val mangaId = newRow.getInt("id")
            // language=h2
            conn
                .prepareStatement(
                    "UPDATE MANGAUSER SET version = version + 1, last_modified_at = ? " +
                        "WHERE manga = ? AND NOT is_syncing",
                ).use {
                    it.setLong(1, Clock.System.now().epochSeconds)
                    it.setInt(2, mangaId)
                    it.executeUpdate()
                }
        }
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
        val userId = newRow.getInt("user_id")

        // language=h2
        conn
            .prepareStatement(
                "UPDATE MANGAUSER SET version = version + 1 WHERE manga = ? AND user_id = ? AND NOT is_syncing",
            ).use {
                it.setInt(1, mangaId)
                it.setInt(2, userId)
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
