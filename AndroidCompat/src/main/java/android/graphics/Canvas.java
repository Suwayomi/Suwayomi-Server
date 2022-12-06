package android.graphics;

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
}
