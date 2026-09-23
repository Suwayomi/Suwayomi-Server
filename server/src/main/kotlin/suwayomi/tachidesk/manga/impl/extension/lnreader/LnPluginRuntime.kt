package suwayomi.tachidesk.manga.impl.extension.lnreader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.SandboxPolicy
import org.graalvm.polyglot.Value
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.readText

/**
 * Minimal secure runtime boundary. ABI-specific operations are intentionally
 * added to the trusted bootstrap/bridge in the following implementation slice.
 */
class LnPluginRuntime(
    private val pluginId: String,
    private val indexJs: Path,
    private val hostBridge: LnHostBridge? = null,
    private val hostBridgeFactory: ((LnPluginRuntime) -> LnHostBridge)? = null,
    private val dispatchTimeoutNanos: Long = DISPATCH_TIMEOUT_NANOS,
    val webStorageOrigin: String? = null,
) : AutoCloseable {
    enum class State {
        NEW,
        READY,
        CLOSING,
        CLOSED,
        FAILED,
    }

    private val state = AtomicReference(State.NEW)
    val isClosed: Boolean get() = state.get() == State.CLOSED
    private val context = AtomicReference<Context?>(null)
    private val activeCall = AtomicReference<Call?>(null)
    private val executionLock = ReentrantLock()
    private val operationDeadline = ThreadLocal<Long>()
    private val bridge by lazy { hostBridge ?: hostBridgeFactory?.invoke(this) ?: LnPluginHost(pluginId, this).bridge() }

    @Volatile private var parsePageSupported: Boolean = false

    @Volatile private var webStorageSupported: Boolean = false

    fun currentState(): State = state.get()

    val hasParsePage: Boolean
        get() =
            executionLock.withLock {
                ensureContext()
                parsePageSupported
            }

    val webStorageUtilized: Boolean
        get() =
            executionLock.withLock {
                ensureContext()
                webStorageSupported
            }

    /** Guest calls serialize per installed plugin; other runtimes remain independent. */
    fun call(
        operation: String,
        requestJson: String,
    ): String =
        executionLock.withLock {
            require(operation.length <= MAX_OPERATION_LENGTH) { "LNReader operation is too long" }
            require(requestJson.length <= MAX_JSON_LENGTH) { "LNReader request is too large" }
            val deadline = System.nanoTime() + dispatchTimeoutNanos
            operationDeadline.set(deadline)
            try {
                val runtime = ensureContext()
                runCatching { runtime.resetLimits() }
                val result = runtime.getBindings("js").getMember(DISPATCH).execute(operation, requestJson)
                awaitDispatch(runtime, result, deadline).also {
                    require(it.length <= MAX_JSON_LENGTH) { "LNReader response is too large" }
                }
            } catch (error: Throwable) {
                val ctx = context.getAndSet(null)
                runCatching { ctx?.close(true) }
                throw error
            } finally {
                operationDeadline.remove()
            }
        }

    /** Supports published retry delays without exceeding the operation deadline. */
    fun sleep(milliseconds: Long) {
        require(milliseconds in 0..MAX_TIMER_MILLIS) { "LNReader timer duration is not permitted" }
        val remaining =
            requireNotNull(operationDeadline.get()) { "LNReader timer is unavailable outside a guest operation" } - System.nanoTime()
        require(remaining > 0 && TimeUnit.NANOSECONDS.toMillis(remaining) >= milliseconds) { "LNReader operation timed out" }
        try {
            Thread.sleep(milliseconds)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("LNReader timer interrupted", error)
        }
    }

    /** Called by the future bounded network bridge immediately before executing a Call. */
    fun registerActiveCall(call: Call): Boolean {
        while (true) {
            if (state.get() !in setOf(State.NEW, State.READY)) {
                call.cancel()
                return false
            }
            val previous = activeCall.get()
            check(previous == null) { "LNReader runtime has more than one active host call" }
            if (activeCall.compareAndSet(null, call)) {
                if (state.get() in setOf(State.NEW, State.READY)) return true
                if (activeCall.compareAndSet(call, null)) call.cancel()
                return false
            }
        }
    }

    fun unregisterActiveCall(call: Call) {
        activeCall.compareAndSet(call, null)
    }

    override fun close() {
        val previous = state.getAndUpdate { current -> if (current == State.CLOSED) State.CLOSED else State.CLOSING }
        if (previous == State.CLOSED || previous == State.CLOSING) return
        activeCall.getAndSet(null)?.cancel()
        try {
            context.getAndSet(null)?.close(true)
        } finally {
            state.set(State.CLOSED)
        }
    }

    private fun ensureContext(): Context {
        while (true) {
            val current = state.get()
            check(current != State.CLOSING && current != State.CLOSED) { "LNReader runtime is closing or closed" }
            context.get()?.let { return it }
            if (current == State.NEW || state.compareAndSet(current, State.NEW)) break
        }
        if (!LnRuntimePlatform.isSupported()) {
            state.compareAndSet(State.NEW, State.FAILED)
            throw LnRuntimeUnavailableException("LNReader UNTRUSTED isolate is unsupported on ${LnRuntimePlatform.description()}")
        }

        val created =
            try {
                Context
                    .newBuilder("js")
                    .sandbox(SandboxPolicy.UNTRUSTED)
                    .allowHostAccess(HostAccess.UNTRUSTED)
                    .allowHostClassLookup { false }
                    .allowCreateThread(false)
                    .allowNativeAccess(false)
                    .out(BoundedOutputStream())
                    .err(BoundedOutputStream())
                    .option("engine.MaxIsolateMemory", "128MB")
                    .option("sandbox.MaxCPUTime", "60s")
                    .option("sandbox.MaxHeapMemory", "64MB")
                    .option("sandbox.MaxASTDepth", "10000")
                    .option("sandbox.MaxThreads", "1")
                    .option("sandbox.MaxOutputStreamSize", "64KB")
                    .option("sandbox.MaxErrorStreamSize", "64KB")
                    .build()
            } catch (error: Throwable) {
                state.compareAndSet(State.NEW, State.FAILED)
                throw LnRuntimeUnavailableException("LNReader UNTRUSTED isolate is unavailable", error)
            }

        if (!state.compareAndSet(State.NEW, State.READY)) {
            created.close(true)
            throw IllegalStateException("LNReader runtime closed while initializing")
        }
        context.set(created)
        if (state.get() != State.READY) {
            context.compareAndSet(created, null)
            created.close(true)
            throw IllegalStateException("LNReader runtime closed while initializing")
        }
        try {
            created.eval("js", LnCompatibility.primitives)
            created.eval("js", LnCompatibility.vendor)
            created.eval("js", LnCompatibility.bootstrap)
            created.getBindings("js").putMember(HOST, bridge)
            created.getBindings("js").putMember(PLUGIN_CODE, indexJs.readText(Charsets.UTF_8))
            created.eval(
                "js",
                "__suwayomiLnInitialize($PLUGIN_CODE, $HOST); delete globalThis.$PLUGIN_CODE; delete globalThis.$HOST; delete globalThis.__suwayomiLnInitialize;",
            )
            val validation =
                created
                    .getBindings("js")
                    .getMember(VALIDATE)
                    .execute()
                    .asString()
            val metadata = Json.parseToJsonElement(validation).jsonObject
            require(metadata["id"]?.jsonPrimitive?.content == pluginId) {
                "LNReader plugin export id does not match manifest id '$pluginId'"
            }
            parsePageSupported = metadata["hasParsePage"]?.jsonPrimitive?.booleanOrNull ?: false
            webStorageSupported =
                metadata["webStorageUtilized"]?.jsonPrimitive?.booleanOrNull == true &&
                webStorageOrigin != null &&
                LnReaderRepository.webStorageOrigin(metadata["site"]?.jsonPrimitive?.contentOrNull.orEmpty()) == webStorageOrigin

            val settingsJson =
                awaitDispatch(
                    created,
                    created.getBindings("js").getMember(DISPATCH).execute("pluginSettings", "{}"),
                    System.nanoTime() + DISPATCH_TIMEOUT_NANOS,
                )
            if (settingsJson != "null" && settingsJson.isNotBlank()) {
                val map =
                    Json.parseToJsonElement(settingsJson).jsonObject.mapValues { (_, elem) ->
                        elem.jsonObject["type"]?.jsonPrimitive?.content ?: "Text"
                    }
                bridge.invoke("storage.registerDeclaredSettings", Json.encodeToString(map))
            }
            runCatching { created.resetLimits() }
        } catch (error: Throwable) {
            state.compareAndSet(State.READY, State.FAILED)
            context.getAndSet(null)?.close(true)
            throw error
        }
        return created
    }

    private fun awaitDispatch(
        runtime: Context,
        result: Value,
        deadline: Long,
    ): String {
        if (!result.canInvokeMember("then")) return result.asString()
        val bindings = runtime.getBindings("js")
        bindings.putMember(PENDING, result)
        runtime.eval(
            "js",
            "globalThis.$PENDING_STATE = { done: false, value: null, error: null, isUndefined: false }; " +
                "globalThis.$PENDING.then(" +
                "v => { if (v === undefined) globalThis.$PENDING_STATE.isUndefined = true; " +
                "globalThis.$PENDING_STATE.value = v; globalThis.$PENDING_STATE.done = true; }, " +
                "e => { globalThis.$PENDING_STATE.error = String(e && e.message || e); globalThis.$PENDING_STATE.done = true; });",
        )
        val pendingState = bindings.getMember(PENDING_STATE)
        while (true) {
            val isDone = pendingState.getMember("done")?.asBoolean() == true
            if (isDone) {
                runtime.eval("js", "delete globalThis.$PENDING; delete globalThis.$PENDING_STATE;")
                if (pendingState.getMember("isUndefined")?.asBoolean() == true) {
                    throw IllegalStateException("LNReader plugin operation produced no result")
                }
                val errorVal = pendingState.getMember("error")
                if (errorVal != null && !errorVal.isNull) {
                    throw IllegalStateException("LNReader plugin operation failed: ${errorVal.asString().take(MAX_ERROR_LENGTH)}")
                }
                val valObj = pendingState.getMember("value")
                val resultValue =
                    when {
                        valObj == null || valObj.isNull -> "null"
                        valObj.isString -> valObj.asString()
                        else -> valObj.toString()
                    }
                return checkNotNull(resultValue) { "LNReader plugin operation produced no result" }
            }
            if (System.nanoTime() >= deadline) {
                close()
                throw IllegalStateException("LNReader plugin operation timed out")
            }
            try {
                Thread.sleep(DISPATCH_POLL_INTERVAL_MILLIS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                close()
                throw IllegalStateException("LNReader plugin operation interrupted", e)
            }
        }
    }

    companion object {
        private const val HOST = "__suwayomiLnHost"
        private const val PLUGIN_CODE = "__suwayomiLnPluginCode"
        private const val DISPATCH = "__suwayomiLnDispatch"
        private const val VALIDATE = "__suwayomiLnValidate"
        private const val PENDING = "__suwayomiLnPending"
        private const val PENDING_STATE = "__suwayomiLnPendingState"
        private const val MAX_OPERATION_LENGTH = 64
        private const val MAX_JSON_LENGTH = 16_777_216
        private const val MAX_ERROR_LENGTH = 512
        private const val DISPATCH_TIMEOUT_NANOS = 90_000_000_000L
        private const val DISPATCH_POLL_INTERVAL_MILLIS = 25L
        private const val MAX_TIMER_MILLIS = 90_000L

        fun smokeTest(
            indexJs: Path,
            expectedPluginId: String,
            expectedVersion: String,
        ): Boolean =
            LnPluginRuntime(expectedPluginId, indexJs).use { runtime ->
                val validation = runtime.call("__validate", "{}")
                require(validation.contains("\"id\":\"$expectedPluginId\"")) {
                    "LNReader plugin export id does not match manifest id '$expectedPluginId'"
                }
                require(validation.contains("\"version\":\"$expectedVersion\"")) {
                    "LNReader plugin export version does not match manifest version '$expectedVersion'"
                }
                runtime.call("pluginSettings", "{}").let { it != "null" && it.isNotBlank() && it != "{}" }
            }
    }
}

class LnRuntimeUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

object LnRuntimePlatform {
    fun isSupported(
        osName: String = System.getProperty("os.name"),
        architecture: String = System.getProperty("os.arch"),
    ): Boolean {
        val os = osName.lowercase()
        val arch = architecture.lowercase()
        return when {
            os.contains("win") -> arch in X64
            os.contains("linux") -> arch in X64 || arch in ARM64
            os.contains("mac") || os.contains("darwin") -> arch in ARM64
            else -> false
        }
    }

    fun description(): String = "${System.getProperty("os.name")} ${System.getProperty("os.arch")}"

    private val X64 = setOf("amd64", "x64", "x86_64", "x86-64")
    private val ARM64 = setOf("aarch64", "arm64")
}

private class BoundedOutputStream : OutputStream() {
    private var written = 0

    override fun write(byte: Int) = write(byteArrayOf(byte.toByte()))

    override fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        written += length
        if (written > 64 * 1024) throw IOException("LNReader guest output limit exceeded")
    }
}
