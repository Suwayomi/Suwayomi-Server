//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package android.util;

import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class Log {
    public static final int ASSERT = 7;
    public static final int DEBUG = 3;
    public static final int ERROR = 6;
    public static final int INFO = 4;
    public static final int VERBOSE = 2;
    public static final int WARN = 5;

    private static Logger logger = LoggerFactory.getLogger(Log.class);

    public static int v(String tag, String msg) {
        return log(VERBOSE, tag, msg);
    }

    public static int v(String tag, String msg, Throwable tr) {
        return log(VERBOSE, tag, msg, tr);
    }

    public static int d(String tag, String msg) {
        return log(DEBUG, tag, msg);
    }

    public static int d(String tag, String msg, Throwable tr) {
        return log(DEBUG, tag, msg, tr);
    }

    public static int i(String tag, String msg) {
        return log(INFO, tag, msg);
    }

    public static int i(String tag, String msg, Throwable tr) {
        return log(INFO, tag, msg, tr);
    }

    public static int w(String tag, String msg) {
        return log(WARN, tag, msg);
    }

    public static int w(String tag, String msg, Throwable tr) {
        return log(WARN, tag, msg, tr);
    }

    public static boolean isLoggable(String var0, int var1) {
        return true;
    }

    public static int w(String tag, Throwable tr) {
        return log(WARN, tag, tr);
    }

    public static int e(String tag, String msg) {
        return log(ERROR, tag, msg);
    }

    public static int e(String tag, String msg, Throwable tr) {
        return log(ERROR, tag, msg, tr);
    }

    //Level?
    public static int wtf(String tag, String msg) {
        return log(ERROR, tag, msg);
    }
    public static int wtf(String tag, Throwable tr) {
        return log(ERROR, tag, tr);
    }
    public static int wtf(String tag, String msg, Throwable tr) {
        return log(ERROR, tag, msg, tr);
    }

    public static String getStackTraceString(Throwable tr) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        tr.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    public static int println(int priority, String tag, String msg) {
        return log(priority, tag, msg);
    }

    private static int log(int level, String tag, String msg) {
        logger.atLevel(intToLevel(level)).log(formatLog(tag, msg));
        return tag.length() + msg.length(); //Not accurate, but never used anyways
    }

    private static int log(int level, String tag, Throwable t) {
        return log(level, tag, "An exception occured!", t);
    }

    private static int log(int level, String tag, String msg, Throwable t) {
        logger.atLevel(intToLevel(level)).setCause(t).log(formatLog(tag, msg));
        return tag.length() + msg.length(); //Not accurate, but never used anyways
    }

    private static String formatLog(String tag, String msg) {
        StringBuilder first = new StringBuilder("[");
        first.append(tag);
        first.append("]: ");
        first.append(msg);
        return first.toString();
    }

    private static Level intToLevel(int level) {
        switch(level) {
            case ASSERT:
                return Level.ERROR;
            case DEBUG:
                return Level.DEBUG;
            case ERROR:
                return Level.ERROR;
            case INFO:
                return Level.INFO;
            case VERBOSE:
                return Level.TRACE;
            case WARN:
                return Level.WARN;
            default:
                return Level.INFO;
        }

  }
}
