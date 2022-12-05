package android.graphics;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public class BitmapFactory {
    public static Bitmap decodeStream(InputStream is) {
        Bitmap bm = null;

        try {
            BufferedImage bf = ImageIO.read(is);
            bm = new Bitmap(bf);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return bm;
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length) {
        Bitmap bm = null;

        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try {
            BufferedImage bf = ImageIO.read(bais);
            bm = new Bitmap(bf);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return bm;
    }
}
