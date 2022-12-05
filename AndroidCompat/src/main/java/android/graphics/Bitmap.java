package android.graphics;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public final class Bitmap {
    private int width;
    private int height;
    private BufferedImage image;

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

        private static Config sConfigs[] = {
            null, ALPHA_8, null, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE, RGBA_1010102
        };

        Config(int ni) {
            this.nativeInt = ni;
        }

        static Config nativeToConfig(int ni) {
            return sConfigs[ni];
        }
    }

    public static Bitmap createBitmap(int width, int height, Config config) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        return new Bitmap(image);
    }

    public boolean compress(CompressFormat format, int quality, OutputStream stream) {
        if (stream == null) {
            throw new NullPointerException();
        }

        if (quality < 0 || quality > 100) {
            throw new IllegalArgumentException("quality must be 0..100");
        }
        float qualityFloat = ((float) quality) / 100;

        String formatString = "";
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
        ImageWriter writer = (ImageWriter) writers.next();

        ImageOutputStream ios;
        try {
            ios = ImageIO.createImageOutputStream(stream);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        writer.setOutput(ios);

        ImageWriteParam param = writer.getDefaultWriteParam();
        if (formatString == "jpg") {
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
}
