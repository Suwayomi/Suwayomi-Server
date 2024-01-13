package suwayomi.tachidesk.manga.impl.util.lang

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Observable
import rx.Subscriber
import rx.Subscription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// source: https://github.com/jobobby04/TachiyomiSY/blob/9320221a4e8b118ef68deb60d8c4c32bcbb9e06f/app/src/main/java/eu/kanade/tachiyomi/util/lang/RxCoroutineBridge.kt
// Util functions for bridging RxJava and coroutines. Taken from TachiyomiEH/SY.

suspend fun <T> Observable<T>.awaitSingle(): T = single().awaitOne()

@OptIn(InternalCoroutinesApi::class)
private suspend fun <T> Observable<T>.awaitOne(): T =
    suspendCancellableCoroutine { cont ->
        cont.unsubscribeOnCancellation(
            subscribe(
                object : Subscriber<T>() {
                    override fun onStart() {
                        request(1)
                    }

                    override fun onNext(t: T) {
                        cont.resume(t)
                    }

                    override fun onCompleted() {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException(
                                    "Should have invoked onNext",
                                ),
                            )
                        }
                    }

                    override fun onError(e: Throwable) {
                    /*
                     * Rx1 observable throws NoSuchElementException if cancellation happened before
                     * element emission. To mitigate this we try to atomically resume continuation with exception:
                     * if resume failed, then we know that continuation successfully cancelled itself
                     */
                        val token = cont.tryResumeWithException(e)
                        if (token != null) {
                            cont.completeResume(token)
                        }
                    }
                },
            ),
        )
    }

private fun <T> CancellableContinuation<T>.unsubscribeOnCancellation(sub: Subscription) = invokeOnCancellation { sub.unsubscribe() }
