/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.dataLoaders

import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import org.dataloader.Try
import org.jetbrains.exposed.v1.core.Slf4jSqlDebugLogger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Cap on how many keys a single DataLoader batch may carry, so a query cannot drive an
 * unbounded `IN` list / single dispatch against the database.
 */
private const val DATA_LOADER_MAX_BATCH_SIZE = 200

private val batchOptions = DataLoaderOptions.newOptions().setMaxBatchSize(DATA_LOADER_MAX_BATCH_SIZE).build()

/**
 * [DataLoaderFactory.newDataLoader] with the shared batch-size cap applied.
 */
internal fun <K : Any, V> newBatchedDataLoader(batchLoader: BatchLoader<K, V>): DataLoader<K, V> =
    DataLoaderFactory.newDataLoader(batchLoader, batchOptions)

/**
 * Same cap, but the loader returns [Try] per key so a single batch can resolve some keys
 * while failing others (e.g. per-key authorization) instead of failing the whole batch.
 */
internal fun <K : Any, V> newBatchedDataLoaderWithTry(batchLoader: BatchLoader<K, Try<V>>): DataLoader<K, V> =
    DataLoaderFactory.newDataLoaderWithTry(batchLoader, batchOptions)

/**
 * `transaction { }` with the SQL debug logger attached, sparing every data loader the
 * repeated `addLogger(Slf4jSqlDebugLogger)` line.
 */
internal fun <T> loggedTransaction(statement: Transaction.() -> T): T =
    transaction {
        addLogger(Slf4jSqlDebugLogger)
        statement()
    }

internal suspend fun <T> loggedSuspendTransaction(statement: suspend Transaction.() -> T): T =
    suspendTransaction {
        addLogger(Slf4jSqlDebugLogger)
        statement()
    }

/**
 * java-dataloader resolves absent keys and explicit nulls through to the caller, so a
 * batch result may legitimately contain nulls; the cast only satisfies Kotlin's type
 * system which cannot express that on `BatchLoader`.
 */
@Suppress("UNCHECKED_CAST")
internal fun <T> List<T?>.asDataLoaderResults(): List<T> = this as List<T>

/** Same as [asDataLoaderResults] for [Try]-based loaders: `Try.succeeded(null)` is legal. */
@Suppress("UNCHECKED_CAST")
internal fun <T> List<Try<T?>>.asTriedDataLoaderResults(): List<Try<T>> = this as List<Try<T>>
