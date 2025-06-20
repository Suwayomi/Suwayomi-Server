package android.graphics;

import android.annotation.NonNull;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public final class Canvas {
    private BufferedImage canvasImage;
    private Graphics2D canvas;

    public Canvas(Bitmap bitmap) {
        canvasImage = bitmap.getImage();
        canvas = canvasImage.createGraphics();
    }

    public void drawBitmap(Bitmap sourceBitmap, Rect src, Rect dst, Paint paint) {
        BufferedImage sourceImage = sourceBitmap.getImage();
        BufferedImage sourceImageCropped = sourceImage.getSubimage(src.left, src.top, src.getWidth(), src.getHeight());
        canvas.drawImage(sourceImageCropped, null, dst.left, dst.top);
    }

    public void drawText(@NonNull char[] text, int index, int count, float x, float y,
            @NonNull Paint paint) {
        throw new RuntimeException("Stub!");
    }

    public void drawText(@NonNull String text, float x, float y, @NonNull Paint paint) {
        throw new RuntimeException("Stub!");
    }

    public void drawText(@NonNull String text, int start, int end, float x, float y,
            @NonNull Paint paint) {
        throw new RuntimeException("Stub!");
    }

    public void drawText(@NonNull CharSequence text, int start, int end, float x, float y,
            @NonNull Paint paint) {
        Font fnt = canvas.getFont();
        canvas.setFont(fnt.deriveFont(paint.getTextSize()));
        java.awt.Color color = Color.valueOf(paint.getColorLong()).toJavaColor();
        canvas.setColor(color);
        // TODO: use more from paint?
        String str = text.subSequence(start, end).toString();
        canvas.drawString(str, x, y);
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
        // TODO: check this, should translations stack?
        canvas.translate(dx, dy);
    }
}
