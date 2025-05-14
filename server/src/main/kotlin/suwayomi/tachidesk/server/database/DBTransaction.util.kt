package suwayomi.tachidesk.server.database

import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Performs the given transaction block inside the parent transaction or creates a new transaction if necessary.
 *
 * Any rollback or exception in the inner transaction will be propagated to the parent transaction.
 */
fun <T> dbTransaction(block: Transaction.() -> T): T {
    val currentTransaction = TransactionManager.currentOrNull()

    return if (currentTransaction == null) {
        transaction { block() }
    } else {
        block(currentTransaction)
    }
}

/**
 * Creates a nested transaction.
 *
 * Any rollback or exception will only roll back the inner (nested) transaction, leaving the parent transaction unaffected.
 *
 * Only works in case "useNestedTransactions" is enabled.
 */
fun <T> nestedDbTransaction(block: Transaction.() -> T): T =
    transaction {
        check(db.useNestedTransactions) { "Nested transactions are not enabled." }
        block()
    }
