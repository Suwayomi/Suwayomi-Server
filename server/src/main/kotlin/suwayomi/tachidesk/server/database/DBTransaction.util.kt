package suwayomi.tachidesk.server.database

import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Performs the given transaction block inside the parent transaction or creates a new transaction if necessary.
 *
 * Any rollback or exception in the inner transaction will be propagated to the parent transaction.
 */
fun <T> dbTransaction(block: JdbcTransaction.() -> T): T {
    val currentTransaction = TransactionManager.currentOrNull()

    return if (currentTransaction == null) {
        transaction { block() }
    } else {
        block(currentTransaction)
    }
}

/**
 * Performs the given suspending transaction block inside the parent transaction or creates
 * a new transaction if necessary.
 *
 * Parent-transaction reuse is thread-affine: [TransactionManager.currentOrNull] reads a
 * thread-local stack, so the parent transaction is only visible while the coroutine resumes
 * on the same thread. If [block] suspends and resumes on a different dispatcher thread
 * (e.g. `withContext(Dispatchers.IO)`), a nested `dbSuspendTransaction`/`suspendTransaction`
 * call there sees no parent and opens an **independent transaction on another pooled
 * connection**: it cannot read the parent's uncommitted writes, and row locks can deadlock
 * against the parent.
 *
 * Contracts for callers:
 * - Do not switch coroutine dispatchers inside a block that is meant to share the parent
 *   transaction; a resume on another thread silently starts a new transaction.
 * - Do not perform file I/O (or any long suspending work) inside a transaction: the pooled
 *   connection and its locks are held for the entire suspend window.
 */
suspend fun <T> dbSuspendTransaction(block: suspend JdbcTransaction.() -> T): T {
    val currentTransaction = TransactionManager.currentOrNull()

    return if (currentTransaction == null) {
        suspendTransaction { block() }
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
