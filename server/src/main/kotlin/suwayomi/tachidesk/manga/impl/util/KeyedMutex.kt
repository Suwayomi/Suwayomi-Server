package suwayomi.tachidesk.manga.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-key mutual exclusion whose entries live exactly as long as they are used.
 *
 * The naive `map.getOrPut(key) { Mutex() }` + paired `map.remove(key, mutex)`
 * idiom breaks exclusion: a coroutine that already bound to the map's mutex
 * instance keeps it after the entry is removed, so a newcomer installs a second
 * mutex and both enter the critical section of the same key. Here every
 * [withLock] call first atomically increments the holder's reference count and
 * only then waits on the mutex — while any coroutine holds an outstanding
 * reference the entry cannot be removed, because removal only happens when a
 * release-side `compute` observes the count reaching zero. `ConcurrentHashMap`
 * runs each `compute` remapping under the bin lock, so acquire, release and
 * removal are serialized per key.
 *
 * Invariants this class relies on:
 * - `compute` remapping functions never suspend and only mutate [Holder.refs]
 *   — they run inside a CHM bin lock and must return promptly;
 * - the release-side decrement runs unconditionally in `finally`, so exception
 *   and cancellation paths still release the reference;
 * - waiters are counted before they start waiting on the mutex, so a mutex
 *   with parked waiters always has `refs > 0` and stays mapped.
 *
 * Not re-entrant: a caller must not invoke [withLock] with the same key from
 * within its own block — the nested call parks on the already-held mutex and
 * deadlocks.
 */
class KeyedMutex<K> {
    private class Holder {
        val mutex = Mutex()

        // only read/written inside ConcurrentHashMap.compute for the key that
        // maps to this holder, i.e. under that key's bin lock
        var refs = 0
    }

    private val holders = ConcurrentHashMap<K, Holder>()

    suspend fun <T> withLock(
        key: K,
        block: suspend () -> T,
    ): T {
        val holder =
            holders.compute(key) { _, cur ->
                (cur ?: Holder()).apply { refs++ }
            }!!
        try {
            return holder.mutex.withLock { block() }
        } finally {
            holders.compute(key) { _, cur ->
                if (cur === holder && --cur.refs == 0) null else cur
            }
        }
    }

    /** test hook: number of keys currently mapped — zero once all users left */
    internal val size: Int get() = holders.size
}
