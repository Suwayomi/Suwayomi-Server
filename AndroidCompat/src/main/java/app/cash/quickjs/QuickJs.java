package app.cash.quickjs;

import org.graalvm.polyglot.*;

import java.io.Closeable;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * JavaScript engine backed by the GraalVM polyglot implementation.
 *
 * <p>Threading: the Graal {@link Context} is entered once at construction and
 * stays pinned to the creating thread for the lifetime of the engine. All use
 * of an instance must therefore happen on the thread that created it (which is
 * how {@code JavaScriptEngine} drives it); {@link #close()} leaves and closes
 * the context and must likewise be called from that thread.
 */
public final class QuickJs implements Closeable {
    private Context context;

    public static QuickJs create() {
        return new QuickJs();
    }

    public QuickJs() {
        this.context = Context
                .newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .allowHostClassLoading(false)
                .build();
        context.enter();
    }

    public Object evaluate(String script, String ignoredFileName) {
        return this.evaluate(script);
    }

    public Object evaluate(String script) {
        try {
            Value value = context.eval("js", script);
            return translateType(value);
        } catch (Exception exception) {
            throw new QuickJsException(exception.getMessage(), exception);
        }
    }

    /**
     * Best-effort mapping of a polyglot {@link Value} to Java types.
     *
     * <p>Types with no Java equivalent (functions, promises, nested arrays of
     * mixed element type, plain JS objects) fall through and leak the raw
     * {@link Value} to the caller; touching it off the owning thread fails.
     */
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
                    } else if (element.fitsInBigInteger()) {
                        return Arrays.stream(obj.as(BigInteger[].class)).map(BigInteger::longValue).toArray();
                    } else {
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
            } else if (obj.fitsInBigInteger()) {
                return obj.asBigInteger().longValue();
            } else {
                return obj.asDouble();
            }
        } else if (obj.isHostObject()) {
            return obj.asHostObject();
        } else if (obj.isString()) {
            return obj.asString();
        }
        return obj;
    }

    /**
     * Fake-compile for API compatibility: there is no bytecode format, the
     * "compiled" bytes are simply the UTF-8 source. {@link #execute(byte[])}
     * evaluates them again.
     */
    public byte[] compile(String sourceCode, String ignoredFileName) {
        return sourceCode.getBytes(StandardCharsets.UTF_8);
    }

    public Object execute(byte[] bytecode) {
        return this.evaluate(new String(bytecode, StandardCharsets.UTF_8));
    }

    public <T> void set(String name, Class<T> ignoredType, T object) {
        context.getBindings("js").putMember(name, object);
    }

    @Override
    public void close() {
        if (this.context != null) {
            this.context.leave();
            this.context.close();
            this.context = null;
        }
    }
}
