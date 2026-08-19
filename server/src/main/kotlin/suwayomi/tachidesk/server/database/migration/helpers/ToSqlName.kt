package suwayomi.tachidesk.server.database.migration.helpers

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

fun String.toSqlName(): String =
    TransactionManager.current().db.identifierManager.let {
        it.quoteIfNecessary(
            it.inProperCase(this),
        )
    }
