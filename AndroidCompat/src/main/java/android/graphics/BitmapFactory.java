package android.graphics;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class BitmapFactory {
    public static Bitmap decodeStream(InputStream inputStream) {
        Bitmap bitmap = null;

        try {
            ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream);
            Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageInputStream);

            if (!imageReaders.hasNext()) {
                throw new IllegalArgumentException("no reader for image");
            }

            ImageReader imageReader = imageReaders.next();
            imageReader.setInput(imageInputStream);

            BufferedImage image = imageReader.read(0, imageReader.getDefaultReadParam());
            bitmap = new Bitmap(image);

            imageReader.dispose();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return bitmap;
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length) {
        Bitmap bitmap = null;

        ByteArrayInputStream byteArrayStream = new ByteArrayInputStream(data);
        try {
            BufferedImage image = ImageIO.read(byteArrayStream);
            bitmap = new Bitmap(image);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return bitmap;
    }
}
