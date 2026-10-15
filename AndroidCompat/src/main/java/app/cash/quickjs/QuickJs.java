package app.cash.quickjs;

import org.graalvm.polyglot.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;

/**
 * JavaScript engine backed by the GraalVM polyglot implementation.
 *
 * <p>Threading: every operation wraps its context touch in {@code enter()}/
 * {@code leave()}, and all public entry points are {@code synchronized}, so
 * concurrent callers are serialized (matching real quickjs-java semantics)
 * instead of tripping GraalVM's multi-threaded-access check. {@link #close()}
 * may still be invoked from any thread to interrupt an in-flight evaluation.
 *
 * <p>Resource bounds: scripts are third-party extension code, so the engine is
 * created with a GraalVM {@link ResourceLimits#getStatementLimit() statement
 * limit} — a hard, engine-level interrupt for runaway loops ({@code while(1){}}).
 * Wall-clock enforcement lives in the caller: closing the instance from another
 * thread ({@link #close()} -> {@code Context.close(true)}) cancels an executing
 * script, which is how {@code JavaScriptEngine} implements its evaluation timeout.
 */
public final class QuickJs implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(QuickJs.class);

    /**
     * Default GraalVM statement budget for a single evaluation. Extension
     * scripts are small (deobfuscation/ad-removal snippets); ~1e8 statements is
     * far above legitimate usage while still bounding runaway scripts. The real
     * quickjs-java cannot be interrupted at all, so any bound is an improvement.
     * Overridable via the {@code suwayomi.quickjs.statementLimit} system
     * property for engines created through {@link #create()} (e.g. by
     * extensions); {@code JavaScriptEngine} passes an explicit value.
     */
    public static final long DEFAULT_STATEMENT_LIMIT =
            Long.getLong("suwayomi.quickjs.statementLimit", 100_000_000L);

    private volatile Context context;

    public static QuickJs create() {
        return new QuickJs();
    }

    public QuickJs() {
        this(DEFAULT_STATEMENT_LIMIT);
    }

    public QuickJs(long statementLimit) {
        ResourceLimits limits = ResourceLimits
                .newBuilder()
                .statementLimit(statementLimit, null)
                .onLimit(event ->
                        logger.warn("QuickJs statement limit ({}) exceeded: {}", statementLimit, event))
                .build();
        Context context = Context
                .newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                // Real quickjs-java has no Java.type equivalent; scripts must only
                // reach the objects injected via set(), never host classes by name.
                .allowHostClassLookup(className -> false)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .allowHostClassLoading(false)
                .resourceLimits(limits)
                .build();
        this.context = context;
    }

    private Context requireContext() {
        Context ctx = this.context;
        if (ctx == null) {
            throw new QuickJsException("QuickJs engine is closed");
        }
        return ctx;
    }

    public synchronized Object evaluate(String script, String ignoredFileName) {
        return this.evaluate(script);
    }

    public synchronized Object evaluate(String script) {
        Context ctx = requireContext();
        ctx.enter();
        try {
            Value value = ctx.eval("js", script);
            return translateType(value);
        } catch (QuickJsException e) {
            throw e;
        } catch (Exception exception) {
            throw new QuickJsException(exception.getMessage(), exception);
        } finally {
            leaveQuietly(ctx);
        }
    }

    private Object translateType(Value obj) {
        if (obj.isBoolean()) {
            return obj.asBoolean();
        } else if (obj.hasArrayElements()) {
            if (obj.getArraySize() == 0) {
                return new int[0];
            } else {
                Value element = obj.getArrayElement(0);
                if (element.isBoolean()) {
                    return obj.as(boolean[].class);
                } else if (element.isNumber()) {
                    if (element.fitsInInt()) {
                        return obj.as(int[].class);
                    } else if (element.fitsInLong()) {
                        // long[] keeps full integer fidelity; the previous
                        // BigInteger[]->longValue() mapping produced Object[].
                        return obj.as(long[].class);
                    } else {
                        // Larger-than-long integers (incl. BigInteger) degrade to
                        // double, as in JavaScript itself.
                        return obj.as(double[].class);
                    }
                } else if (element.isHostObject()) {
                    return obj.as(Object[].class);
                } else if (element.isString()) {
                    return obj.as(String[].class);
                }
            }
        } else if (obj.isNumber()) {
            if (obj.fitsInInt()) {
                return obj.asInt();
            } else if (obj.fitsInLong()) {
                return obj.asLong();
            } else {
                // BigInteger values beyond long range (and plain doubles) fall
                // back to double rather than truncating through longValue().
                return obj.asDouble();
            }
        } else if (obj.isHostObject()) {
            return obj.asHostObject();
        } else if (obj.isString()) {
            return obj.asString();
        }
        // Non-translatable JS values (functions, plain objects, ...) leak as
        // raw polyglot Value objects. Callers should not rely on this.
        return obj;
    }

    /**
     * "Compiles" the source. GraalVM does not expose a bytecode exchange
     * format, so this returns the UTF-8 encoded source; {@link #execute}
     * evaluates it again. The charset is explicit: the platform default
     * charset would silently corrupt non-ASCII scripts on the round trip.
     */
    public synchronized byte[] compile(String sourceCode, String ignoredFileName) {
        return sourceCode.getBytes(StandardCharsets.UTF_8);
    }

    public synchronized Object execute(byte[] bytecode) {
        return this.evaluate(new String(bytecode, StandardCharsets.UTF_8));
    }

    public synchronized <T> void set(String name, Class<T> ignoredType, T object) {
        Context ctx = requireContext();
        ctx.enter();
        try {
            ctx.getBindings("js").putMember(name, object);
        } catch (QuickJsException e) {
            throw e;
        } catch (Exception exception) {
            // Same contract as evaluate(): host-side failures (e.g. a cancelled
            // context racing close()) surface as QuickJsException, not bare
            // PolyglotException.
            throw new QuickJsException(exception.getMessage(), exception);
        } finally {
            leaveQuietly(ctx);
        }
    }

    /**
     * Attaches to a global JavaScript object called {@code name} that
     * implements {@code type} (mirrors the real quickjs-java API).
     */
    public synchronized <T> T get(String name, Class<T> type) {
        Context ctx = requireContext();
        ctx.enter();
        try {
            Value member = ctx.getBindings("js").getMember(name);
            if (member == null || member.isNull()) {
                return null;
            }
            return member.as(type);
        } catch (QuickJsException e) {
            throw e;
        } catch (Exception exception) {
            throw new QuickJsException(exception.getMessage(), exception);
        } finally {
            leaveQuietly(ctx);
        }
    }

    /**
     * Pairs {@link Context#enter()} while tolerating a context that was closed
     * (cancelled) underneath this operation by a cross-thread {@link #close()}.
     */
    private static void leaveQuietly(Context ctx) {
        try {
            ctx.leave();
        } catch (IllegalStateException | PolyglotException ignored) {
            // context already cancelled by close(true)
        }
    }

    /**
     * Closes the engine. Safe to call from any thread and idempotent.
     *
     * <p>The context teardown intentionally runs outside this instance's
     * monitor: {@code Context.close(true)} is itself thread-safe and cancels an
     * in-flight evaluation, so a caller on another thread can use it to
     * interrupt a script that is still executing (the wall-clock timeout path).
     * Synchronizing the teardown would make such an interrupt block until the
     * script finishes on its own.
     */
    @Override
    public void close() {
        // Deliberately NOT synchronized: the whole point of close() is that it
        // can interrupt an eval still holding this instance's monitor — taking
        // the lock here would deadlock against the very thread being cancelled.
        // A volatile read/write plus Context.close(true) (itself thread-safe and
        // idempotent) is sufficient.
        Context ctx = this.context;
        if (ctx == null) {
            return;
        }
        this.context = null;
        // close(true) cancels any executing script — including one entered on
        // another thread — and performs the teardown from whatever thread calls
        // it, so the context can never be left open.
        ctx.close(true);
    }
}
