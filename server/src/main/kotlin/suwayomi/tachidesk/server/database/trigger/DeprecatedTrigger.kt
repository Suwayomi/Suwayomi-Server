package suwayomi.tachidesk.server.database.trigger

import org.h2.tools.TriggerAdapter
import java.sql.Connection
import java.sql.ResultSet

open class DeprecatedTrigger : TriggerAdapter() {
    override fun fire(
        conn: Connection?,
        oldRow: ResultSet?,
        newRow: ResultSet?,
    ) {}
}
