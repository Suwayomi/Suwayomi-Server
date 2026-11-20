package android.graphics;

import android.os.Parcelable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.os.Parcel;

public final class Rect {
    public int left;
    public int top;
    public int right;
    public int bottom;

    private static final class UnflattenHelper {
        private static final Pattern FLATTENED_PATTERN = Pattern.compile(
            "(-?\\d+) (-?\\d+) (-?\\d+) (-?\\d+)");

        static Matcher getMatcher(String str) {
            return FLATTENED_PATTERN.matcher(str);
        }
    }

    public Rect() {
    }

    public Rect(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public Rect(Rect r) {
        if (r == null) {
            this.left = 0;
            this.top = 0;
            this.right = 0;
            this.bottom = 0;
        } else {
            this.left = r.left;
            this.top = r.top;
            this.right = r.right;
            this.bottom = r.bottom;
        }
    }

    public void set(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public void set(Rect r) {
        this.left = r.left;
        this.top = r.top;
        this.right = r.right;
        this.bottom = r.bottom;
    }

    public final int getWidth() {
        return right - left;
    }

    public final int getHeight() {
        return bottom - top;
    }

    public static Rect unflattenFromString(String str) {
        if (str.isEmpty()) {
            return null;
        }

        Matcher matcher = UnflattenHelper.getMatcher(str);
        if (!matcher.matches()) {
            return null;
        }

        return new Rect(Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3)),
                        Integer.parseInt(matcher.group(4)));
    }

    public String toShortString() {
        return toShortString(new StringBuilder(32));
    }
    
    public String toShortString(StringBuilder sb) {
        sb.setLength(0);
        sb.append('['); sb.append(left); sb.append(',');
        sb.append(top); sb.append("]["); sb.append(right);
        sb.append(','); sb.append(bottom); sb.append(']');
        return sb.toString();
    }

    public String flattenToString() {
        StringBuilder sb = new StringBuilder(32);
        sb.append(left);
        sb.append(' ');
        sb.append(top);
        sb.append(' ');
        sb.append(right);
        sb.append(' ');
        sb.append(bottom);
        return sb.toString();
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(left);
        out.writeInt(top);
        out.writeInt(right);
        out.writeInt(bottom);
    }

    public static final Parcelable.Creator<Rect> CREATOR = new Parcelable.Creator<Rect>() {
        @Override
        public Rect createFromParcel(Parcel in) {
            Rect r = new Rect();
            r.readFromParcel(in);
            return r;
        }

        @Override
        public Rect[] newArray(int size) {
            return new Rect[size];
        }
    };

    public void readFromParcel(Parcel in) {
        left = in.readInt();
        top = in.readInt();
        right = in.readInt();
        bottom = in.readInt();
    }
}
