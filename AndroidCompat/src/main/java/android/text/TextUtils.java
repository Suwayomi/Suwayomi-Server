/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.text;

import android.annotation.Nullable;
import android.os.Parcel;
import android.text.style.ReplacementSpan;
import android.util.Printer;
import com.android.internal.util.ArrayUtils;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.regex.Pattern;
public class TextUtils {
    private static final String TAG = "TextUtils";
    /* package */ static final char[] ELLIPSIS_NORMAL = { '\u2026' }; // this is "..."
    /** {@hide} */
    public static final String ELLIPSIS_STRING = new String(ELLIPSIS_NORMAL);
    /* package */ static final char[] ELLIPSIS_TWO_DOTS = { '\u2025' }; // this is ".."
    private static final String ELLIPSIS_TWO_DOTS_STRING = new String(ELLIPSIS_TWO_DOTS);
    private TextUtils() { /* cannot be instantiated */ }
    public static void getChars(CharSequence s, int start, int end,
                                char[] dest, int destoff) {
        Class<? extends CharSequence> c = s.getClass();
        if (c == String.class)
            ((String) s).getChars(start, end, dest, destoff);
        else if (c == StringBuffer.class)
            ((StringBuffer) s).getChars(start, end, dest, destoff);
        else if (c == StringBuilder.class)
            ((StringBuilder) s).getChars(start, end, dest, destoff);
        else if (s instanceof GetChars)
            ((GetChars) s).getChars(start, end, dest, destoff);
        else {
            for (int i = start; i < end; i++)
                dest[destoff++] = s.charAt(i);
        }
    }
    public static int indexOf(CharSequence s, char ch) {
        return indexOf(s, ch, 0);
    }
    public static int indexOf(CharSequence s, char ch, int start) {
        Class<? extends CharSequence> c = s.getClass();
        if (c == String.class)
            return ((String) s).indexOf(ch, start);
        return indexOf(s, ch, start, s.length());
    }
    public static int indexOf(CharSequence s, char ch, int start, int end) {
        Class<? extends CharSequence> c = s.getClass();
        if (s instanceof GetChars || c == StringBuffer.class ||
            c == StringBuilder.class || c == String.class) {
            final int INDEX_INCREMENT = 500;
            char[] temp = obtain(INDEX_INCREMENT);
            while (start < end) {
                int segend = start + INDEX_INCREMENT;
                if (segend > end)
                    segend = end;
                getChars(s, start, segend, temp, 0);
                int count = segend - start;
                for (int i = 0; i < count; i++) {
                    if (temp[i] == ch) {
                        recycle(temp);
                        return i + start;
                    }
                }
                start = segend;
            }
            recycle(temp);
            return -1;
        }
        for (int i = start; i < end; i++)
            if (s.charAt(i) == ch)
                return i;
        return -1;
    }
    public static int lastIndexOf(CharSequence s, char ch) {
        return lastIndexOf(s, ch, s.length() - 1);
    }
    public static int lastIndexOf(CharSequence s, char ch, int last) {
        Class<? extends CharSequence> c = s.getClass();
        if (c == String.class)
            return ((String) s).lastIndexOf(ch, last);
        return lastIndexOf(s, ch, 0, last);
    }
    public static int lastIndexOf(CharSequence s, char ch,
                                  int start, int last) {
        if (last < 0)
            return -1;
        if (last >= s.length())
            last = s.length() - 1;
        int end = last + 1;
        Class<? extends CharSequence> c = s.getClass();
        if (s instanceof GetChars || c == StringBuffer.class ||
            c == StringBuilder.class || c == String.class) {
            final int INDEX_INCREMENT = 500;
            char[] temp = obtain(INDEX_INCREMENT);
            while (start < end) {
                int segstart = end - INDEX_INCREMENT;
                if (segstart < start)
                    segstart = start;
                getChars(s, segstart, end, temp, 0);
                int count = end - segstart;
                for (int i = count - 1; i >= 0; i--) {
                    if (temp[i] == ch) {
                        recycle(temp);
                        return i + segstart;
                    }
                }
                end = segstart;
            }
            recycle(temp);
            return -1;
        }
        for (int i = end - 1; i >= start; i--)
            if (s.charAt(i) == ch)
                return i;
        return -1;
    }
    public static int indexOf(CharSequence s, CharSequence needle) {
        return indexOf(s, needle, 0, s.length());
    }
    public static int indexOf(CharSequence s, CharSequence needle, int start) {
        return indexOf(s, needle, start, s.length());
    }
    public static int indexOf(CharSequence s, CharSequence needle,
                              int start, int end) {
        int nlen = needle.length();
        if (nlen == 0)
            return start;
        char c = needle.charAt(0);
        for (;;) {
            start = indexOf(s, c, start);
            if (start > end - nlen) {
                break;
            }
            if (start < 0) {
                return -1;
            }
            if (regionMatches(s, start, needle, 0, nlen)) {
                return start;
            }
            start++;
        }
        return -1;
    }
    public static boolean regionMatches(CharSequence one, int toffset,
                                        CharSequence two, int ooffset,
                                        int len) {
        int tempLen = 2 * len;
        if (tempLen < len) {
            // Integer overflow; len is unreasonably large
            throw new IndexOutOfBoundsException();
        }
        char[] temp = obtain(tempLen);
        getChars(one, toffset, toffset + len, temp, 0);
        getChars(two, ooffset, ooffset + len, temp, len);
        boolean match = true;
        for (int i = 0; i < len; i++) {
            if (temp[i] != temp[i + len]) {
                match = false;
                break;
            }
        }
        recycle(temp);
        return match;
    }
    /**
     * Create a new String object containing the given range of characters
     * from the source string.  This is different than simply calling
     * {@link CharSequence#subSequence(int, int) CharSequence.subSequence}
     * in that it does not preserve any style runs in the source sequence,
     * allowing a more efficient implementation.
     */
    public static String substring(CharSequence source, int start, int end) {
        if (source instanceof String)
            return ((String) source).substring(start, end);
        if (source instanceof StringBuilder)
            return ((StringBuilder) source).substring(start, end);
        if (source instanceof StringBuffer)
            return ((StringBuffer) source).substring(start, end);
        char[] temp = obtain(end - start);
        getChars(source, start, end, temp, 0);
        String ret = new String(temp, 0, end - start);
        recycle(temp);
        return ret;
    }
    /**
     * Returns a string containing the tokens joined by delimiters.
     * @param tokens an array objects to be joined. Strings will be formed from
     *     the objects by calling object.toString().
     */
    public static String join(CharSequence delimiter, Object[] tokens) {
        StringBuilder sb = new StringBuilder();
        boolean firstTime = true;
        for (Object token: tokens) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(delimiter);
            }
            sb.append(token);
        }
        return sb.toString();
    }
    /**
     * Returns a string containing the tokens joined by delimiters.
     * @param tokens an array objects to be joined. Strings will be formed from
     *     the objects by calling object.toString().
     */
    public static String join(CharSequence delimiter, Iterable tokens) {
        StringBuilder sb = new StringBuilder();
        Iterator<?> it = tokens.iterator();
        if (it.hasNext()) {
            sb.append(it.next());
            while (it.hasNext()) {
                sb.append(delimiter);
                sb.append(it.next());
            }
        }
        return sb.toString();
    }
    /**
     * String.split() returns [''] when the string to be split is empty. This returns []. This does
     * not remove any empty strings from the result. For example split("a,", ","  ) returns {"a", ""}.
     *
     * @param text the string to split
     * @param expression the regular expression to match
     * @return an array of strings. The array will be empty if text is empty
     *
     * @throws NullPointerException if expression or text is null
     */
    public static String[] split(String text, String expression) {
        if (text.length() == 0) {
            return EMPTY_STRING_ARRAY;
        } else {
            return text.split(expression, -1);
        }
    }
    /**
     * Splits a string on a pattern. String.split() returns [''] when the string to be
     * split is empty. This returns []. This does not remove any empty strings from the result.
     * @param text the string to split
     * @param pattern the regular expression to match
     * @return an array of strings. The array will be empty if text is empty
     *
     * @throws NullPointerException if expression or text is null
     */
    public static String[] split(String text, Pattern pattern) {
        if (text.length() == 0) {
            return EMPTY_STRING_ARRAY;
        } else {
            return pattern.split(text, -1);
        }
    }
    /**
     * An interface for splitting strings according to rules that are opaque to the user of this
     * interface. This also has less overhead than split, which uses regular expressions and
     * allocates an array to hold the results.
     *
     * <p>The most efficient way to use this class is:
     *
     * <pre>
     * // Once
     * TextUtils.StringSplitter splitter = new TextUtils.SimpleStringSplitter(delimiter);
     *
     * // Once per string to split
     * splitter.setString(string);
     * for (String s : splitter) {
     *     ...
     * }
     * </pre>
     */
    public interface StringSplitter extends Iterable<String> {
        public void setString(String string);
    }
    /**
     * A simple string splitter.
     *
     * <p>If the final character in the string to split is the delimiter then no empty string will
     * be returned for the empty string after that delimeter. That is, splitting <tt>"a,b,"</tt> on
     * comma will return <tt>"a", "b"</tt>, not <tt>"a", "b", ""</tt>.
     */
    public static class SimpleStringSplitter implements StringSplitter, Iterator<String> {
        private String mString;
        private char mDelimiter;
        private int mPosition;
        private int mLength;
        /**
         * Initializes the splitter. setString may be called later.
         * @param delimiter the delimeter on which to split
         */
        public SimpleStringSplitter(char delimiter) {
            mDelimiter = delimiter;
        }
        /**
         * Sets the string to split
         * @param string the string to split
         */
        public void setString(String string) {
            mString = string;
            mPosition = 0;
            mLength = mString.length();
        }
        public Iterator<String> iterator() {
            return this;
        }
        public boolean hasNext() {
            return mPosition < mLength;
        }
        public String next() {
            int end = mString.indexOf(mDelimiter, mPosition);
            if (end == -1) {
                end = mLength;
            }
            String nextString = mString.substring(mPosition, end);
            mPosition = end + 1; // Skip the delimiter.
            return nextString;
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
    public static CharSequence stringOrSpannedString(CharSequence source) {
        if (source == null)
            return null;
        if (source instanceof SpannedString)
            return source;
        if (source instanceof Spanned)
            return new SpannedString(source);
        return source.toString();
    }
    /**
     * Returns true if the string is null or 0-length.
     * @param str the string to be examined
     * @return true if str is null or zero length
     */
    public static boolean isEmpty(@Nullable CharSequence str) {
        if (str == null || str.length() == 0)
            return true;
        else
            return false;
    }
    /** {@hide} */
    public static String nullIfEmpty(@Nullable String str) {
        return isEmpty(str) ? null : str;
    }
    /**
     * Returns the length that the specified CharSequence would have if
     * spaces and ASCII control characters were trimmed from the start and end,
     * as by {@link String#trim}.
     */
    public static int getTrimmedLength(CharSequence s) {
        int len = s.length();
        int start = 0;
        while (start < len && s.charAt(start) <= ' ') {
            start++;
        }
        int end = len;
        while (end > start && s.charAt(end - 1) <= ' ') {
            end--;
        }
        return end - start;
    }
    /**
     * Returns true if a and b are equal, including if they are both null.
     * <p><i>Note: In platform versions 1.1 and earlier, this method only worked well if
     * both the arguments were instances of String.</i></p>
     * @param a first CharSequence to check
     * @param b second CharSequence to check
     * @return true if a and b are equal
     */
    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        int length;
        if (a != null && b != null && (length = a.length()) == b.length()) {
            if (a instanceof String && b instanceof String) {
                return a.equals(b);
            } else {
                for (int i = 0; i < length; i++) {
                    if (a.charAt(i) != b.charAt(i)) return false;
                }
                return true;
            }
        }
        return false;
    }
    /**
     * This function only reverses individual {@code char}s and not their associated
     * spans. It doesn't support surrogate pairs (that correspond to non-BMP code points), combining
     * sequences or conjuncts either.
     * @deprecated Do not use.
     */
    @Deprecated
    public static CharSequence getReverse(CharSequence source, int start, int end) {
        return new Reverser(source, start, end);
    }
    private static class Reverser
    implements CharSequence, GetChars
    {
        public Reverser(CharSequence source, int start, int end) {
            mSource = source;
            mStart = start;
            mEnd = end;
        }
        public int length() {
            return mEnd - mStart;
        }
        public CharSequence subSequence(int start, int end) {
            char[] buf = new char[end - start];
            getChars(start, end, buf, 0);
            return new String(buf);
        }
        @Override
        public String toString() {
            return subSequence(0, length()).toString();
        }
        public char charAt(int off) {
            return AndroidCharacter.getMirror(mSource.charAt(mEnd - 1 - off));
        }
        public void getChars(int start, int end, char[] dest, int destoff) {
            TextUtils.getChars(mSource, start + mStart, end + mStart,
                               dest, destoff);
            AndroidCharacter.mirror(dest, 0, end - start);
            int len = end - start;
            int n = (end - start) / 2;
            for (int i = 0; i < n; i++) {
                char tmp = dest[destoff + i];
                dest[destoff + i] = dest[destoff + len - i - 1];
                dest[destoff + len - i - 1] = tmp;
            }
        }
        private CharSequence mSource;
        private int mStart;
        private int mEnd;
    }
    private static void writeWhere(Parcel p, Spanned sp, Object o) {
        p.writeInt(sp.getSpanStart(o));
        p.writeInt(sp.getSpanEnd(o));
        p.writeInt(sp.getSpanFlags(o));
    }
    /**
     * Debugging tool to print the spans in a CharSequence.  The output will
     * be printed one span per line.  If the CharSequence is not a Spanned,
     * then the entire string will be printed on a single line.
     */
    public static void dumpSpans(CharSequence cs, Printer printer, String prefix) {
        if (cs instanceof Spanned) {
            Spanned sp = (Spanned) cs;
            Object[] os = sp.getSpans(0, cs.length(), Object.class);
            for (int i = 0; i < os.length; i++) {
                Object o = os[i];
                printer.println(prefix + cs.subSequence(sp.getSpanStart(o),
                        sp.getSpanEnd(o)) + ": "
                        + Integer.toHexString(System.identityHashCode(o))
                        + " " + o.getClass().getCanonicalName()
                         + " (" + sp.getSpanStart(o) + "-" + sp.getSpanEnd(o)
                         + ") fl=#" + sp.getSpanFlags(o));
            }
        } else {
            printer.println(prefix + cs + ": (no spans)");
        }
    }
    /**
     * Return a new CharSequence in which each of the source strings is
     * replaced by the corresponding element of the destinations.
     */
    public static CharSequence replace(CharSequence template,
                                       String[] sources,
                                       CharSequence[] destinations) {
        SpannableStringBuilder tb = new SpannableStringBuilder(template);
        for (int i = 0; i < sources.length; i++) {
            int where = indexOf(tb, sources[i]);
            if (where >= 0)
                tb.setSpan(sources[i], where, where + sources[i].length(),
                           Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        for (int i = 0; i < sources.length; i++) {
            int start = tb.getSpanStart(sources[i]);
            int end = tb.getSpanEnd(sources[i]);
            if (start >= 0) {
                tb.replace(start, end, destinations[i]);
            }
        }
        return tb;
    }
    /**
     * Replace instances of "^1", "^2", etc. in the
     * <code>template</code> CharSequence with the corresponding
     * <code>values</code>.  "^^" is used to produce a single caret in
     * the output.  Only up to 9 replacement values are supported,
     * "^10" will be produce the first replacement value followed by a
     * '0'.
     *
     * @param template the input text containing "^1"-style
     * placeholder values.  This object is not modified; a copy is
     * returned.
     *
     * @param values CharSequences substituted into the template.  The
     * first is substituted for "^1", the second for "^2", and so on.
     *
     * @return the new CharSequence produced by doing the replacement
     *
     * @throws IllegalArgumentException if the template requests a
     * value that was not provided, or if more than 9 values are
     * provided.
     */
    public static CharSequence expandTemplate(CharSequence template,
                                              CharSequence... values) {
        if (values.length > 9) {
            throw new IllegalArgumentException("max of 9 values are supported");
        }
        SpannableStringBuilder ssb = new SpannableStringBuilder(template);
        try {
            int i = 0;
            while (i < ssb.length()) {
                if (ssb.charAt(i) == '^') {
                    char next = ssb.charAt(i+1);
                    if (next == '^') {
                        ssb.delete(i+1, i+2);
                        ++i;
                        continue;
                    } else if (Character.isDigit(next)) {
                        int which = Character.getNumericValue(next) - 1;
                        if (which < 0) {
                            throw new IllegalArgumentException(
                                "template requests value ^" + (which+1));
                        }
                        if (which >= values.length) {
                            throw new IllegalArgumentException(
                                "template requests value ^" + (which+1) +
                                "; only " + values.length + " provided");
                        }
                        ssb.replace(i, i+2, values[which]);
                        i += values[which].length();
                        continue;
                    }
                }
                ++i;
            }
        } catch (IndexOutOfBoundsException ignore) {
            // happens when ^ is the last character in the string.
        }
        return ssb;
    }
    public static int getOffsetBefore(CharSequence text, int offset) {
        if (offset == 0)
            return 0;
        if (offset == 1)
            return 0;
        char c = text.charAt(offset - 1);
        if (c >= '\uDC00' && c <= '\uDFFF') {
            char c1 = text.charAt(offset - 2);
            if (c1 >= '\uD800' && c1 <= '\uDBFF')
                offset -= 2;
            else
                offset -= 1;
        } else {
            offset -= 1;
        }
        if (text instanceof Spanned) {
            ReplacementSpan[] spans = ((Spanned) text).getSpans(offset, offset,
                                                       ReplacementSpan.class);
            for (int i = 0; i < spans.length; i++) {
                int start = ((Spanned) text).getSpanStart(spans[i]);
                int end = ((Spanned) text).getSpanEnd(spans[i]);
                if (start < offset && end > offset)
                    offset = start;
            }
        }
        return offset;
    }
    public static int getOffsetAfter(CharSequence text, int offset) {
        int len = text.length();
        if (offset == len)
            return len;
        if (offset == len - 1)
            return len;
        char c = text.charAt(offset);
        if (c >= '\uD800' && c <= '\uDBFF') {
            char c1 = text.charAt(offset + 1);
            if (c1 >= '\uDC00' && c1 <= '\uDFFF')
                offset += 2;
            else
                offset += 1;
        } else {
            offset += 1;
        }
        if (text instanceof Spanned) {
            ReplacementSpan[] spans = ((Spanned) text).getSpans(offset, offset,
                                                       ReplacementSpan.class);
            for (int i = 0; i < spans.length; i++) {
                int start = ((Spanned) text).getSpanStart(spans[i]);
                int end = ((Spanned) text).getSpanEnd(spans[i]);
                if (start < offset && end > offset)
                    offset = end;
            }
        }
        return offset;
    }
    private static void readSpan(Parcel p, Spannable sp, Object o) {
        sp.setSpan(o, p.readInt(), p.readInt(), p.readInt());
    }
    /**
     * Copies the spans from the region <code>start...end</code> in
     * <code>source</code> to the region
     * <code>destoff...destoff+end-start</code> in <code>dest</code>.
     * Spans in <code>source</code> that begin before <code>start</code>
     * or end after <code>end</code> but overlap this range are trimmed
     * as if they began at <code>start</code> or ended at <code>end</code>.
     *
     * @throws IndexOutOfBoundsException if any of the copied spans
     * are out of range in <code>dest</code>.
     */
    public static void copySpansFrom(Spanned source, int start, int end,
                                     Class kind,
                                     Spannable dest, int destoff) {
        if (kind == null) {
            kind = Object.class;
        }
        Object[] spans = source.getSpans(start, end, kind);
        for (int i = 0; i < spans.length; i++) {
            int st = source.getSpanStart(spans[i]);
            int en = source.getSpanEnd(spans[i]);
            int fl = source.getSpanFlags(spans[i]);
            if (st < start)
                st = start;
            if (en > end)
                en = end;
            dest.setSpan(spans[i], st - start + destoff, en - start + destoff,
                         fl);
        }
    }
    public enum TruncateAt {
        START,
        MIDDLE,
        END,
        MARQUEE,
        /**
         * @hide
         */
        END_SMALL
    }
    private static final char FIRST_RIGHT_TO_LEFT = '\u0590';
    /* package */
    static boolean doesNotNeedBidi(CharSequence s, int start, int end) {
        for (int i = start; i < end; i++) {
            if (s.charAt(i) >= FIRST_RIGHT_TO_LEFT) {
                return false;
            }
        }
        return true;
    }
    /* package */
    static boolean doesNotNeedBidi(char[] text, int start, int len) {
        for (int i = start, e = i + len; i < e; i++) {
            if (text[i] >= FIRST_RIGHT_TO_LEFT) {
                return false;
            }
        }
        return true;
    }
    /* package */ static char[] obtain(int len) {
        char[] buf;
        synchronized (sLock) {
            buf = sTemp;
            sTemp = null;
        }
        if (buf == null || buf.length < len)
            buf = ArrayUtils.newUnpaddedCharArray(len);
        return buf;
    }
    /* package */ static void recycle(char[] temp) {
        if (temp.length > 1000)
            return;
        synchronized (sLock) {
            sTemp = temp;
        }
    }
    /**
     * Html-encode the string.
     * @param s the string to be encoded
     * @return the encoded string
     */
    public static String htmlEncode(String s) {
        StringBuilder sb = new StringBuilder();
        char c;
        for (int i = 0; i < s.length(); i++) {
            c = s.charAt(i);
            switch (c) {
            case '<':
                sb.append("&lt;"); //$NON-NLS-1$
                break;
            case '>':
                sb.append("&gt;"); //$NON-NLS-1$
                break;
            case '&':
                sb.append("&amp;"); //$NON-NLS-1$
                break;
            case '\'':
                //http://www.w3.org/TR/xhtml1
                // The named character reference &apos; (the apostrophe, U+0027) was introduced in
                // XML 1.0 but does not appear in HTML. Authors should therefore use &#39; instead
                // of &apos; to work as expected in HTML 4 user agents.
                sb.append("&#39;"); //$NON-NLS-1$
                break;
            case '"':
                sb.append("&quot;"); //$NON-NLS-1$
                break;
            default:
                sb.append(c);
            }
        }
        return sb.toString();
    }
    /**
     * Returns a CharSequence concatenating the specified CharSequences,
     * retaining their spans if any.
     */
    public static CharSequence concat(CharSequence... text) {
        if (text.length == 0) {
            return "";
        }
        if (text.length == 1) {
            return text[0];
        }
        boolean spanned = false;
        for (int i = 0; i < text.length; i++) {
            if (text[i] instanceof Spanned) {
                spanned = true;
                break;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length; i++) {
            sb.append(text[i]);
        }
        if (!spanned) {
            return sb.toString();
        }
        SpannableString ss = new SpannableString(sb);
        int off = 0;
        for (int i = 0; i < text.length; i++) {
            int len = text[i].length();
            if (text[i] instanceof Spanned) {
                copySpansFrom((Spanned) text[i], 0, len, Object.class, ss, off);
            }
            off += len;
        }
        return new SpannedString(ss);
    }
    /**
     * Returns whether the given CharSequence contains any printable characters.
     */
    public static boolean isGraphic(CharSequence str) {
        final int len = str.length();
        for (int cp, i=0; i<len; i+=Character.charCount(cp)) {
            cp = Character.codePointAt(str, i);
            int gc = Character.getType(cp);
            if (gc != Character.CONTROL
                    && gc != Character.FORMAT
                    && gc != Character.SURROGATE
                    && gc != Character.UNASSIGNED
                    && gc != Character.LINE_SEPARATOR
                    && gc != Character.PARAGRAPH_SEPARATOR
                    && gc != Character.SPACE_SEPARATOR) {
                return true;
            }
        }
        return false;
    }
    /**
     * Returns whether this character is a printable character.
     *
     * This does not support non-BMP characters and should not be used.
     *
     * @deprecated Use {@link #isGraphic(CharSequence)} instead.
     */
    @Deprecated
    public static boolean isGraphic(char c) {
        int gc = Character.getType(c);
        return     gc != Character.CONTROL
                && gc != Character.FORMAT
                && gc != Character.SURROGATE
                && gc != Character.UNASSIGNED
                && gc != Character.LINE_SEPARATOR
                && gc != Character.PARAGRAPH_SEPARATOR
                && gc != Character.SPACE_SEPARATOR;
    }
    /**
     * Returns whether the given CharSequence contains only digits.
     */
    public static boolean isDigitsOnly(CharSequence str) {
        final int len = str.length();
        for (int cp, i = 0; i < len; i += Character.charCount(cp)) {
            cp = Character.codePointAt(str, i);
            if (!Character.isDigit(cp)) {
                return false;
            }
        }
        return true;
    }
    /**
     * @hide
     */
    public static boolean isPrintableAscii(final char c) {
        final int asciiFirst = 0x20;
        final int asciiLast = 0x7E;  // included
        return (asciiFirst <= c && c <= asciiLast) || c == '\r' || c == '\n';
    }
    /**
     * @hide
     */
    public static boolean isPrintableAsciiOnly(final CharSequence str) {
        final int len = str.length();
        for (int i = 0; i < len; i++) {
            if (!isPrintableAscii(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }
    /**
     * Capitalization mode for {@link #getCapsMode}: capitalize all
     * characters.  This value is explicitly defined to be the same as
     * {@link InputType#TYPE_TEXT_FLAG_CAP_CHARACTERS}.
     */
    public static final int CAP_MODE_CHARACTERS
            = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
    /**
     * Capitalization mode for {@link #getCapsMode}: capitalize the first
     * character of all words.  This value is explicitly defined to be the same as
     * {@link InputType#TYPE_TEXT_FLAG_CAP_WORDS}.
     */
    public static final int CAP_MODE_WORDS
            = InputType.TYPE_TEXT_FLAG_CAP_WORDS;
    /**
     * Capitalization mode for {@link #getCapsMode}: capitalize the first
     * character of each sentence.  This value is explicitly defined to be the same as
     * {@link InputType#TYPE_TEXT_FLAG_CAP_SENTENCES}.
     */
    public static final int CAP_MODE_SENTENCES
            = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    /**
     * Determine what caps mode should be in effect at the current offset in
     * the text.  Only the mode bits set in <var>reqModes</var> will be
     * checked.  Note that the caps mode flags here are explicitly defined
     * to match those in {@link InputType}.
     *
     * @param cs The text that should be checked for caps modes.
     * @param off Location in the text at which to check.
     * @param reqModes The modes to be checked: may be any combination of
     * {@link #CAP_MODE_CHARACTERS}, {@link #CAP_MODE_WORDS}, and
     * {@link #CAP_MODE_SENTENCES}.
     *
     * @return Returns the actual capitalization modes that can be in effect
     * at the current position, which is any combination of
     * {@link #CAP_MODE_CHARACTERS}, {@link #CAP_MODE_WORDS}, and
     * {@link #CAP_MODE_SENTENCES}.
     */
    public static int getCapsMode(CharSequence cs, int off, int reqModes) {
        if (off < 0) {
            return 0;
        }
        int i;
        char c;
        int mode = 0;
        if ((reqModes&CAP_MODE_CHARACTERS) != 0) {
            mode |= CAP_MODE_CHARACTERS;
        }
        if ((reqModes&(CAP_MODE_WORDS|CAP_MODE_SENTENCES)) == 0) {
            return mode;
        }
        // Back over allowed opening punctuation.
        for (i = off; i > 0; i--) {
            c = cs.charAt(i - 1);
            if (c != '"' && c != '\'' &&
                Character.getType(c) != Character.START_PUNCTUATION) {
                break;
            }
        }
        // Start of paragraph, with optional whitespace.
        int j = i;
        while (j > 0 && ((c = cs.charAt(j - 1)) == ' ' || c == '\t')) {
            j--;
        }
        if (j == 0 || cs.charAt(j - 1) == '\n') {
            return mode | CAP_MODE_WORDS;
        }
        // Or start of word if we are that style.
        if ((reqModes&CAP_MODE_SENTENCES) == 0) {
            if (i != j) mode |= CAP_MODE_WORDS;
            return mode;
        }
        // There must be a space if not the start of paragraph.
        if (i == j) {
            return mode;
        }
        // Back over allowed closing punctuation.
        for (; j > 0; j--) {
            c = cs.charAt(j - 1);
            if (c != '"' && c != '\'' &&
                Character.getType(c) != Character.END_PUNCTUATION) {
                break;
            }
        }
        if (j > 0) {
            c = cs.charAt(j - 1);
            if (c == '.' || c == '?' || c == '!') {
                // Do not capitalize if the word ends with a period but
                // also contains a period, in which case it is an abbreviation.
                if (c == '.') {
                    for (int k = j - 2; k >= 0; k--) {
                        c = cs.charAt(k);
                        if (c == '.') {
                            return mode;
                        }
                        if (!Character.isLetter(c)) {
                            break;
                        }
                    }
                }
                return mode | CAP_MODE_SENTENCES;
            }
        }
        return mode;
    }
    /**
     * Does a comma-delimited list 'delimitedString' contain a certain item?
     * (without allocating memory)
     *
     * @hide
     */
    public static boolean delimitedStringContains(
            String delimitedString, char delimiter, String item) {
        if (isEmpty(delimitedString) || isEmpty(item)) {
            return false;
        }
        int pos = -1;
        int length = delimitedString.length();
        while ((pos = delimitedString.indexOf(item, pos + 1)) != -1) {
            if (pos > 0 && delimitedString.charAt(pos - 1) != delimiter) {
                continue;
            }
            int expectedDelimiterPos = pos + item.length();
            if (expectedDelimiterPos == length) {
                // Match at end of string.
                return true;
            }
            if (delimitedString.charAt(expectedDelimiterPos) == delimiter) {
                return true;
            }
        }
        return false;
    }
    /**
     * Removes empty spans from the <code>spans</code> array.
     *
     * When parsing a Spanned using {@link Spanned#nextSpanTransition(int, int, Class)}, empty spans
     * will (correctly) create span transitions, and calling getSpans on a slice of text bounded by
     * one of these transitions will (correctly) include the empty overlapping span.
     *
     * However, these empty spans should not be taken into account when layouting or rendering the
     * string and this method provides a way to filter getSpans' results accordingly.
     *
     * @param spans A list of spans retrieved using {@link Spanned#getSpans(int, int, Class)} from
     * the <code>spanned</code>
     * @param spanned The Spanned from which spans were extracted
     * @return A subset of spans where empty spans ({@link Spanned#getSpanStart(Object)}  ==
     * {@link Spanned#getSpanEnd(Object)} have been removed. The initial order is preserved
     * @hide
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] removeEmptySpans(T[] spans, Spanned spanned, Class<T> klass) {
        T[] copy = null;
        int count = 0;
        for (int i = 0; i < spans.length; i++) {
            final T span = spans[i];
            final int start = spanned.getSpanStart(span);
            final int end = spanned.getSpanEnd(span);
            if (start == end) {
                if (copy == null) {
                    copy = (T[]) Array.newInstance(klass, spans.length - 1);
                    System.arraycopy(spans, 0, copy, 0, i);
                    count = i;
                }
            } else {
                if (copy != null) {
                    copy[count] = span;
                    count++;
                }
            }
        }
        if (copy != null) {
            T[] result = (T[]) Array.newInstance(klass, count);
            System.arraycopy(copy, 0, result, 0, count);
            return result;
        } else {
            return spans;
        }
    }
    /**
     * Pack 2 int values into a long, useful as a return value for a range
     * @see #unpackRangeStartFromLong(long)
     * @see #unpackRangeEndFromLong(long)
     * @hide
     */
    public static long packRangeInLong(int start, int end) {
        return (((long) start) << 32) | end;
    }
    /**
     * Get the start value from a range packed in a long by {@link #packRangeInLong(int, int)}
     * @see #unpackRangeEndFromLong(long)
     * @see #packRangeInLong(int, int)
     * @hide
     */
    public static int unpackRangeStartFromLong(long range) {
        return (int) (range >>> 32);
    }
    /**
     * Get the end value from a range packed in a long by {@link #packRangeInLong(int, int)}
     * @see #unpackRangeStartFromLong(long)
     * @see #packRangeInLong(int, int)
     * @hide
     */
    public static int unpackRangeEndFromLong(long range) {
        return (int) (range & 0x00000000FFFFFFFFL);
    }
    private static Object sLock = new Object();
    private static char[] sTemp = null;
    private static String[] EMPTY_STRING_ARRAY = new String[]{};
    private static final char ZWNBS_CHAR = '\uFEFF';
}