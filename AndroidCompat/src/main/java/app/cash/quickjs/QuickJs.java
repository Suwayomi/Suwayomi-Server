package app.cash.quickjs;

import org.graalvm.polyglot.*;

import java.io.Closeable;
import java.math.BigInteger;
import java.util.Arrays;

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

    public byte[] compile(String sourceCode, String ignoredFileName) {
        return sourceCode.getBytes();
    }

    public Object execute(byte[] bytecode) {
        return this.evaluate(new String(bytecode));
    }

    public <T> void set(String name, Class<T> ignoredType, T object) {
        context.getBindings("js").putMember(name, object);
    }


    @Override
    public void close() {
        this.context.leave();
        this.context.close();
        this.context = null;
    }
}
