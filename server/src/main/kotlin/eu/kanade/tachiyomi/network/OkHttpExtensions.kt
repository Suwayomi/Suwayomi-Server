package eu.kanade.tachiyomi.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.Producer
import rx.Subscription
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException

fun Call.asObservable(): Observable<Response> {
    return Observable.unsafeCreate { subscriber ->
        // Since Call is a one-shot type, clone it for each new subscriber.
        val call = clone()

        // Wrap the call in a helper which handles both unsubscription and backpressure.
        val requestArbiter =
            object : AtomicBoolean(), Producer, Subscription {
                override fun request(n: Long) {
                    if (n == 0L || !compareAndSet(false, true)) return

                    try {
                        val response = call.execute()
                        if (!subscriber.isUnsubscribed) {
                            subscriber.onNext(response)
                            subscriber.onCompleted()
                        }
                    } catch (error: Exception) {
                        if (!subscriber.isUnsubscribed) {
                            subscriber.onError(error)
                        }
                    }
                }

                override fun unsubscribe() {
                    // call.cancel()
                }

                override fun isUnsubscribed(): Boolean {
                    return call.isCanceled()
                }
            }

        subscriber.add(requestArbiter)
        subscriber.setProducer(requestArbiter)
    }
}

fun Call.asObservableSuccess(): Observable<Response> {
    return asObservable()
        .doOnNext { response ->
            if (!response.isSuccessful) {
                response.close()
                throw HttpException(response.code)
            }
        }
}

// Based on https://github.com/gildor/kotlin-coroutines-okhttp
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun Call.await(callStack: Array<StackTraceElement>): Response {
    return suspendCancellableCoroutine { continuation ->
        val callback =
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    continuation.resume(response) {
                        response.body.close()
                    }
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    // Don't bother with resuming the continuation if it is already cancelled.
                    if (continuation.isCancelled) return
                    val exception = IOException(e.message, e).apply { stackTrace = callStack }
                    continuation.resumeWithException(exception)
                }
            }

        enqueue(callback)

        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Throwable) {
                // Ignore cancel exception
            }
        }
    }
}

suspend fun Call.await(): Response {
    val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
    return await(callStack)
}

/**
 * @since extensions-lib 1.5
 */
suspend fun Call.awaitSuccess(): Response {
    val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
    val response = await(callStack)
    if (!response.isSuccessful) {
        response.close()
        throw HttpException(response.code).apply { stackTrace = callStack }
    }
    return response
}

fun OkHttpClient.newCachelessCallWithProgress(
    request: Request,
    listener: ProgressListener,
): Call {
    val progressClient =
        newBuilder()
            .cache(null)
            .addNetworkInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                    .body(ProgressResponseBody(originalResponse.body, listener))
                    .build()
            }
            .build()

    return progressClient.newCall(request)
}

context(Json)
inline fun <reified T> Response.parseAs(): T {
    return decodeFromJsonResponse(serializer(), this)
}

context(Json)
@OptIn(ExperimentalSerializationApi::class)
fun <T> decodeFromJsonResponse(
    deserializer: DeserializationStrategy<T>,
    response: Response,
): T {
    return response.body.source().use {
        decodeFromBufferedSource(deserializer, it)
    }
}

class HttpException(val code: Int) : IllegalStateException("HTTP error $code")
