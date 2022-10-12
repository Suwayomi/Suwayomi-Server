package app.cash.quickjs;

import org.mozilla.javascript.ConsString;
import org.mozilla.javascript.NativeArray;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.Closeable;

public final class QuickJs implements Closeable {
    private ScriptEngine engine;

    public static QuickJs create() {
        return new QuickJs(new ScriptEngineManager());
    }

    public QuickJs(ScriptEngineManager manager) {
        this.engine = manager.getEngineByName("rhino");
    }

    public Object evaluate(String script, String fileName) {
        return this.evaluate(script);
    }

    public Object evaluate(String script) {
        try {
            Object value = engine.eval(script);
            return translateType(value);
        } catch (Exception exception) {
            throw new QuickJsException(exception.getMessage(), exception);
        }
    }

    private Object translateType(Object obj) {
        if (obj instanceof NativeArray) {
            NativeArray array = (NativeArray) obj;
            long length = array.getLength();
            Object[] objects = new Object[(int) length];
            for (int i = 0; i < (int) length; i++) {
                objects[i] = translateType(array.get(i));
            }
            return objects;
        }
        if (obj instanceof ConsString) {
            ConsString consString = (ConsString) obj;
            return consString.toString();
        }
        if (obj instanceof Long) {
            Long value = (Long) obj;
            return value.intValue();
        }
        return obj;
    }

    public byte[] compile(String sourceCode, String fileName) {
        return sourceCode.getBytes();
    }


    public Object execute(byte[] bytecode) {
        return this.evaluate(new String(bytecode));
    }


    @Override
    public void close() {
        this.engine = null;
    }
}
