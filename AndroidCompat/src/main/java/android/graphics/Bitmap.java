package android.graphics;

import android.annotation.ColorInt;
import android.annotation.NonNull;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

public final class Bitmap {
    private final int width;
    private final int height;
    private final BufferedImage image;

    public Bitmap(BufferedImage image) {
        this.image = image;
        this.width = image.getWidth();
        this.height = image.getHeight();
    }

    public BufferedImage getImage() {
        return image;
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public enum CompressFormat {
        JPEG          (0),
        PNG           (1),
        WEBP          (2),
        WEBP_LOSSY    (3),
        WEBP_LOSSLESS (4);

        CompressFormat(int nativeInt) {
            this.nativeInt = nativeInt;
        }

        final int nativeInt;
    }

    public enum Config {
        ALPHA_8(1),
        RGB_565(3),
        ARGB_4444(4),
        ARGB_8888(5),
        RGBA_F16(6),
        HARDWARE(7),
        RGBA_1010102(8);

        final int nativeInt;

        private static final Config[] sConfigs = {
                null, ALPHA_8, null, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE, RGBA_1010102
        };

        Config(int ni) {
            this.nativeInt = ni;
        }

        static Config nativeToConfig(int ni) {
            return sConfigs[ni];
        }
    }

    /**
     * Common code for checking that x and y are >= 0
     *
     * @param x x coordinate to ensure is >= 0
     * @param y y coordinate to ensure is >= 0
     */
    private static void checkXYSign(int x, int y) {
        if (x < 0) {
            throw new IllegalArgumentException("x must be >= 0");
        }
        if (y < 0) {
            throw new IllegalArgumentException("y must be >= 0");
        }
    }

    /**
     * Common code for checking that width and height are > 0
     *
     * @param width  width to ensure is > 0
     * @param height height to ensure is > 0
     */
    private static void checkWidthHeight(int width, int height) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be > 0");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be > 0");
        }
    }

    public static Bitmap createBitmap(int width, int height, Config config) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        return new Bitmap(image);
    }

    public static Bitmap createBitmap(@NonNull Bitmap source, int x, int y, int width, int height) {
        checkXYSign(x, y);
        checkWidthHeight(width, height);
        if (x + width > source.getWidth()) {
            throw new IllegalArgumentException("x + width must be <= bitmap.width()");
        }
        if (y + height > source.getHeight()) {
            throw new IllegalArgumentException("y + height must be <= bitmap.height()");
        }

        // Android will make a copy when creating a sub image,
        // so we do the same here
        BufferedImage subImage = source.image.getSubimage(x, y, width, height);
        BufferedImage newImage = new BufferedImage(subImage.getWidth(), subImage.getHeight(), subImage.getType());
        newImage.setData(subImage.getData());

        return new Bitmap(newImage);
    }

    public boolean compress(CompressFormat format, int quality, OutputStream stream) {
        if (stream == null) {
            throw new NullPointerException();
        }

        if (quality < 0 || quality > 100) {
            throw new IllegalArgumentException("quality must be 0..100");
        }
        float qualityFloat = ((float) quality) / 100;

        String formatString;
        if (format == Bitmap.CompressFormat.PNG) {
            formatString = "png";
        } else if (format == Bitmap.CompressFormat.JPEG) {
            formatString = "jpg";
        } else {
            throw new IllegalArgumentException("unsupported compression format!");
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatString);
        if (!writers.hasNext()) {
            throw new IllegalStateException("no image writers found for this format!");
        }
        ImageWriter writer = writers.next();

        ImageOutputStream ios;
        try {
            ios = ImageIO.createImageOutputStream(stream);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        writer.setOutput(ios);

        ImageWriteParam param = writer.getDefaultWriteParam();
        if ("jpg".equals(formatString)) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(qualityFloat);
        }

        try {
            writer.write(null, new IIOImage(image, null, null), param);
            ios.close();
            writer.dispose();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return true;
    }

    /**
     * Shared code to check for illegal arguments passed to getPixels()
     * or setPixels()
     *
     * @param x      left edge of the area of pixels to access
     * @param y      top edge of the area of pixels to access
     * @param width  width of the area of pixels to access
     * @param height height of the area of pixels to access
     * @param offset offset into pixels[] array
     * @param stride number of elements in pixels[] between each logical row
     * @param pixels array to hold the area of pixels being accessed
     */
    private void checkPixelsAccess(int x, int y, int width, int height,
                                   int offset, int stride, int[] pixels) {
        checkXYSign(x, y);
        if (width < 0) {
            throw new IllegalArgumentException("width must be >= 0");
        }
        if (height < 0) {
            throw new IllegalArgumentException("height must be >= 0");
        }
        if (x + width > getWidth()) {
            throw new IllegalArgumentException(
                    "x + width must be <= bitmap.width()");
        }
        if (y + height > getHeight()) {
            throw new IllegalArgumentException(
                    "y + height must be <= bitmap.height()");
        }
        if (Math.abs(stride) < width) {
            throw new IllegalArgumentException("abs(stride) must be >= width");
        }
        int lastScanline = offset + (height - 1) * stride;
        int length = pixels.length;
        if (offset < 0 || (offset + width > length)
                || lastScanline < 0
                || (lastScanline + width > length)) {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    public void getPixels(@ColorInt int[] pixels, int offset, int stride,
                          int x, int y, int width, int height) {
        checkPixelsAccess(x, y, width, height, offset, stride, pixels);

        Raster raster = image.getData();
        int[] rasterPixels = raster.getPixels(x, y, width, height, (int[]) null);

        for (int ht = 0; ht < height; ht++) {
            int rowOffset = offset + stride * ht;
            System.arraycopy(rasterPixels, ht * width, pixels, rowOffset, width);
        }
    }
}
