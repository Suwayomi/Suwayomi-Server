package android.text;


import android.graphics.drawable.Drawable;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.xml.sax.XMLReader;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Project: TachiServer
 * Author: nulldev
 * Creation Date: 16/08/16
 *
 * Android compat class for processing HTML
 *
 * The compat implementation parses HTML with Jsoup and produces a real
 * {@link Spanned} wrapping the extracted plain text. HTML entities are decoded
 * and common block-level tags ({@code <p>}, {@code <br>}, ...) are converted to
 * newlines, similar to Android's {@code Html.fromHtml}. Inline style spans are
 * not generated.
 */
public class Html {

    public static final int FROM_HTML_MODE_LEGACY = 0x00000000;

    public static Spanned fromHtml(String source) {
        return fromHtml(source, FROM_HTML_MODE_LEGACY);
    }

    public static Spanned fromHtml(String source, int flags) {
        if (source == null) {
            return new CompatSpanned("");
        }
        Document doc = Jsoup.parse(source);
        doc.select("script,style").remove();
        // Convert common block-level markup into newlines (approximation of the
        // platform renderer), then extract the whole text with entities decoded.
        for (Element br : doc.select("br")) {
            br.appendText("\n");
        }
        for (Element block : doc.select(
                "p,div,li,ul,ol,tr,table,h1,h2,h3,h4,h5,h6,blockquote,section,article,pre")) {
            block.appendText("\n");
        }
        String text = doc.body() != null ? doc.body().wholeText() : doc.wholeText();
        // Normalize whitespace like the platform implementation: collapse
        // horizontal whitespace, tidy newlines, cap blank lines, trim.
        text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        text = text.replaceAll(" *\\n+ *", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n");
        text = text.trim();
        return new CompatSpanned(text);
    }

    public static Spanned fromHtml(String source, Html.ImageGetter imageGetter, Html.TagHandler tagHandler) {
        if (imageGetter == null && tagHandler == null) {
            return fromHtml(source);
        }
        throw new UnsupportedOperationException(
                "Html.fromHtml with an ImageGetter/TagHandler is not supported in the "
                        + "Suwayomi AndroidCompat environment");
    }

    public static Spanned fromHtml(String source, int flags, Html.ImageGetter imageGetter, Html.TagHandler tagHandler) {
        if (imageGetter == null && tagHandler == null) {
            return fromHtml(source, flags);
        }
        throw new UnsupportedOperationException(
                "Html.fromHtml with an ImageGetter/TagHandler is not supported in the "
                        + "Suwayomi AndroidCompat environment");
    }

    /**
     * Serializes the text back to HTML. Limitation: since the compat
     * {@link Spanned} does not carry style spans, only the plain text is
     * preserved (escaped).
     */
    public static String toHtml(Spanned text) {
        return escapeHtml(text);
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

    /**
     * A real {@link Spanned} implementation backed by a String plus a list of
     * span records. All Spanned contract methods are functional; {@link #setSpan}
     * allows callers to attach markup.
     */
    private static class CompatSpanned implements Spanned {

        private static class SpanRecord {
            final Object what;
            final int start;
            final int end;
            final int flags;

            SpanRecord(Object what, int start, int end, int flags) {
                this.what = what;
                this.start = start;
                this.end = end;
                this.flags = flags;
            }
        }

        private final String text;
        private final List<SpanRecord> spans = new ArrayList<>();

        CompatSpanned(String string) {
            this.text = string == null ? "" : string;
        }

        public void setSpan(Object what, int start, int end, int flags) {
            spans.add(new SpanRecord(what, start, end, flags));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] getSpans(int start, int end, Class<T> type) {
            List<T> result = new ArrayList<>();
            for (SpanRecord span : spans) {
                if ((type == null || type.isInstance(span.what))
                        && span.start < end && span.end > start) {
                    result.add((T) span.what);
                }
            }
            T[] array = (T[]) Array.newInstance(
                    type == null ? Object.class : type, result.size());
            return result.toArray(array);
        }

        @Override
        public int getSpanStart(Object tag) {
            for (SpanRecord span : spans) {
                if (span.what == tag) {
                    return span.start;
                }
            }
            return -1;
        }

        @Override
        public int getSpanEnd(Object tag) {
            for (SpanRecord span : spans) {
                if (span.what == tag) {
                    return span.end;
                }
            }
            return -1;
        }

        @Override
        public int getSpanFlags(Object tag) {
            for (SpanRecord span : spans) {
                if (span.what == tag) {
                    return span.flags;
                }
            }
            return 0;
        }

        @Override
        public int nextSpanTransition(int start, int limit, Class type) {
            int next = limit;
            for (SpanRecord span : spans) {
                if (type == null || type.isInstance(span.what)) {
                    if (span.start > start && span.start < next) {
                        next = span.start;
                    }
                    if (span.end > start && span.end < next) {
                        next = span.end;
                    }
                }
            }
            return next;
        }

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public char charAt(int index) {
            return text.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return text.subSequence(start, end);
        }

        @NotNull
        @Override
        public String toString() {
            return text;
        }
    }
}
