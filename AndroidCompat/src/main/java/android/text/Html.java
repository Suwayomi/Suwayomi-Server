package android.text;


import android.graphics.drawable.Drawable;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.xml.sax.XMLReader;

/**
 * Project: TachiServer
 * Author: nulldev
 * Creation Date: 16/08/16
 *
 * Android compat class for processing HTML
 */

public class Html {

    public static Spanned fromHtml(String source) {
        return new FakeSpanned(Jsoup.clean(source, Whitelist.none()));
    }

    public static Spanned fromHtml(String source, Html.ImageGetter imageGetter, Html.TagHandler tagHandler) {
        throw new RuntimeException("Stub!");
    }

    public static String toHtml(Spanned text) {
        return text.toString();
    }

    /** From: http://stackoverflow.com/a/25228492/5054192 **/
    public static String escapeHtml(CharSequence s) {
        StringBuilder out = new StringBuilder(Math.max(16, s.length()));
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 127 || c == '"' || c == '<' || c == '>' || c == '&') {
                out.append("&#");
                out.append((int) c);
                out.append(';');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public interface TagHandler {
        void handleTag(boolean var1, String var2, Editable var3, XMLReader var4);
    }

    public interface ImageGetter {
        Drawable getDrawable(String var1);
    }

    private static class FakeSpanned implements Spanned {

        String string;

        public FakeSpanned(String string) {
            this.string = string;
        }

        @Override
        public <T> T[] getSpans(int i, int i1, Class<T> aClass) {
            return null;
        }

        @Override
        public int getSpanStart(Object o) {
            return 0;
        }

        @Override
        public int getSpanEnd(Object o) {
            return 0;
        }

        @Override
        public int getSpanFlags(Object o) {
            return 0;
        }

        @Override
        public int nextSpanTransition(int i, int i1, Class aClass) {
            return 0;
        }

        @Override
        public int length() {
            return 0;
        }

        @Override
        public char charAt(int index) {
            return 0;
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return null;
        }

        @NotNull
        @Override
        public String toString() {
            return string;
        }
    }
}
