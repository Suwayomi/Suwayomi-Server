package android.graphics;

import android.annotation.ColorInt;
import android.annotation.ColorLong;
import android.annotation.NonNull;
import android.graphics.Path;
import android.graphics.RectF;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextAttribute;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;

public final class Canvas {
    private BufferedImage canvasImage;
    private Graphics2D canvas;
    private List<AffineTransform> transformStack = new ArrayList<AffineTransform>();

    private static final String TAG = "Canvas";

    public Canvas(Bitmap bitmap) {
        canvasImage = bitmap.getImage();
        canvas = canvasImage.createGraphics();
    }

    public void drawBitmap(Bitmap sourceBitmap, Rect src, Rect dst, Paint paint) {
        BufferedImage sourceImage = sourceBitmap.getImage();
        BufferedImage sourceImageCropped = sourceImage.getSubimage(src.left, src.top, src.getWidth(), src.getHeight());
        canvas.drawImage(sourceImageCropped, dst.left, dst.top, dst.getWidth(), dst.getHeight(), null);
    }

    public void drawBitmap(Bitmap sourceBitmap, float left, float top, Paint paint) {
        BufferedImage sourceImage = sourceBitmap.getImage();
        canvas.drawImage(sourceImage, null, (int) left, (int) top);
    }

    public void drawText(@NonNull char[] text, int index, int count, float x, float y,
            @NonNull Paint paint) {
        drawText(new String(text, index, count), x, y, paint);
    }

    public void drawText(@NonNull String str, float x, float y, @NonNull Paint paint) {
        applyPaint(paint);
        AttributedString text = paint.getTypeface().createWithFallback(str);
        canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        // TODO: fix with fallback fonts
        GlyphVector glyphVector = paint.getTypeface().getFont().createGlyphVector(canvas.getFontRenderContext(), text.getIterator());
        Shape textShape = glyphVector.getOutline();
        switch (paint.getStyle()) {
            case Paint.Style.FILL:
                canvas.drawString(text.getIterator(), x, y);
                break;
            case Paint.Style.STROKE:
                save();
                translate(x, y);
                canvas.draw(textShape);
                restore();
                break;
            case Paint.Style.FILL_AND_STROKE:
                save();
                translate(x, y);
                canvas.draw(textShape);
                canvas.fill(textShape);
                restore();
                break;
        }
    }

    public void drawText(@NonNull String text, int start, int end, float x, float y,
            @NonNull Paint paint) {
        drawText(text.substring(start, end), x, y, paint);
    }

    public void drawText(@NonNull CharSequence text, int start, int end, float x, float y,
            @NonNull Paint paint) {
        String str = text.subSequence(start, end).toString();
        drawText(str, x, y, paint);
    }

    public void drawRoundRect(@NonNull RectF rect, float rx, float ry, @NonNull Paint paint) {
        throw new RuntimeException("Stub!");
    }

    public void drawRoundRect(float left, float top, float right, float bottom, float rx, float ry,
            @NonNull Paint paint) {
        throw new RuntimeException("Stub!");
    }

    public void drawPath(@NonNull Path path, @NonNull Paint paint) {
        throw new RuntimeException("Stub!");
    }

    public void translate(float dx, float dy) {
        if (dx == 0.0f && dy == 0.0f) return;
        // TODO: check this, should translations stack?
        canvas.translate(dx, dy);
    }

    public void scale(float sx, float sy) {
        if (sx == 1.0f && sy == 1.0f) return;
        canvas.scale(sx, sy);
    }

    public final void scale(float sx, float sy, float px, float py) {
        if (sx == 1.0f && sy == 1.0f) return;
        translate(px, py);
        scale(sx, sy);
        translate(-px, -py);
    }

    public void rotate(float degrees) {
        if (degrees == 0.0f) return;
        canvas.rotate(degrees);
    }

    public final void rotate(float degrees, float px, float py) {
        if (degrees == 0.0f) return;
        canvas.rotate(degrees, px, py);
    }

    public int getSaveCount() {
        return transformStack.size();
    }

    public int save() {
        transformStack.add(canvas.getTransform());
        return getSaveCount();
    }

    public void restoreToCount(int saveCount) {
        if (saveCount < 1) {
            throw new IllegalArgumentException(
                    "Underflow in restoreToCount - more restores than saves");
        }
        if (saveCount > getSaveCount()) {
            throw new IllegalArgumentException("Overflow in restoreToCount");

        }
        AffineTransform ts = transformStack.get(saveCount - 1);
        canvas.setTransform(ts);
        while (transformStack.size() >= saveCount) {
            transformStack.remove(transformStack.size() - 1);
        }
    }

    public void restore() {
        restoreToCount(getSaveCount());
    }

    public boolean getClipBounds(@NonNull Rect bounds) {
        Rectangle r = canvas.getClipBounds();
        if (r == null) {
            bounds.left = 0;
            bounds.top = 0;
            bounds.right = canvasImage.getWidth();
            bounds.bottom = canvasImage.getHeight();
            return true;
        }
        bounds.left = r.x;
        bounds.top = r.y;
        bounds.right = r.x + r.width;
        bounds.bottom = r.y + r.height;
        return r.width != 0 && r.height != 0;
    }

    public void drawColor(@ColorInt int colorInt) {
        java.awt.Color color = Color.valueOf(colorInt).toJavaColor();
        canvas.setColor(color);
        canvas.fillRect(0, 0, canvasImage.getWidth(), canvasImage.getHeight());
    }

    public void drawColor(@ColorLong long colorLong) {
        java.awt.Color color = Color.valueOf(colorLong).toJavaColor();
        canvas.setColor(color);
        canvas.fillRect(0, 0, canvasImage.getWidth(), canvasImage.getHeight());
    }

    private void applyPaint(Paint paint) {
        canvas.setFont(paint.getTypeface().getFont());
        java.awt.Color color = Color.valueOf(paint.getColorLong()).toJavaColor();
        canvas.setColor(color);
        canvas.setStroke(new BasicStroke(paint.getStrokeWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (paint.isAntiAlias()) {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        } else {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }
        if (paint.isDither()) {
            canvas.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        } else {
            canvas.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
        }
        // TODO: use more from paint?
    }
}
