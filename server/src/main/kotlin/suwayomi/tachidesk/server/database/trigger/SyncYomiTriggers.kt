package suwayomi.tachidesk.server.database.trigger

import org.h2.tools.TriggerAdapter
import java.sql.Connection
import java.sql.ResultSet

@Suppress("unused")
class UpdateMangaVersionTrigger : TriggerAdapter() {
    override fun fire(
        conn: Connection,
        oldRow: ResultSet,
        newRow: ResultSet,
    ) {
        val isSyncing = newRow.getBoolean("is_syncing")
        val hasChanged = oldRow.getString("url") != newRow.getString("url") ||
            oldRow.getString("description") != newRow.getString("description") ||
            oldRow.getBoolean("in_library") != newRow.getBoolean("in_library")

        if (!isSyncing && hasChanged) {
            val id = newRow.getInt("id")

            conn.prepareStatement(
                "UPDATE MANGA SET version = version + 1 WHERE id = ?",
            ).use {
                it.setInt(1, id)
                it.executeUpdate()
            }
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
        val hasChanged = oldRow.getBoolean("read") != newRow.getBoolean("read") ||
            oldRow.getBoolean("bookmark") != newRow.getBoolean("bookmark") ||
            oldRow.getInt("last_page_read") != newRow.getInt("last_page_read")

        if (!isSyncing && hasChanged) {
            val chapterId = newRow.getInt("id")
            val mangaId = newRow.getInt("manga")

            conn.prepareStatement(
                "UPDATE CHAPTER SET version = version + 1 WHERE id = ?",
            ).use {
                it.setInt(1, chapterId)
                it.executeUpdate()
            }

            conn.prepareStatement(
                "UPDATE MANGA SET version = version + 1 WHERE id = ? AND (SELECT is_syncing FROM MANGA WHERE id = ?) = FALSE",
            ).use {
                it.setInt(1, mangaId)
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

        conn.prepareStatement(
            "UPDATE MANGA SET version = version + 1 WHERE id = ? AND (SELECT is_syncing FROM MANGA WHERE id = ?) = FALSE",
        ).use {
            it.setInt(1, mangaId)
            it.setInt(2, mangaId)
            it.executeUpdate()
        }
    }
}

