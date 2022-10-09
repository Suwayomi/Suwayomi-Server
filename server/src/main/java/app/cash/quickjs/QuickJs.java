package app.cash.quickjs;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.Closeable;

public final class QuickJs implements Closeable {
    private final ScriptEngine engine;

    public static QuickJs create() {
        return new QuickJs(new ScriptEngineManager());
    }

    public QuickJs(ScriptEngineManager manager) {
        this.engine = manager.getEngineByName("js");
    }

    public Object evaluate(String script, String fileName) {
        try {
            return engine.eval(script);
        } catch (Exception exception) {
            exception.printStackTrace();
            return "";
        }
    }

    public Object evaluate(String script) {
        try {
            return engine.eval(script);
        } catch (Exception exception) {
            exception.printStackTrace();
            return "";
        }
    }

    @Override
    public void close() {
    }
}
