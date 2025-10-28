package io.jbnu.test;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.Animation.PlayMode;
import com.badlogic.gdx.utils.Array;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Lightweight GIF -> Animation<TextureRegion> decoder for LibGDX (Java 8 compatible).
 * Works well for small sprite GIFs (e.g., mario.gif, swim.gif).
 */
public class GifDecoder {

    /**
     * Load a GIF (bytes) into a LibGDX Animation.
     * @param playMode Animation play mode (e.g., LOOP)
     * @param data     GIF file bytes
     */
    public static Animation<TextureRegion> loadGIFAnimation(PlayMode playMode, byte[] data) {
        Array<TextureRegion> frames = new Array<TextureRegion>();
        float frameDuration = 0.1f; // default 10 fps fallback

        ByteArrayInputStream bais = null;
        ImageInputStream stream = null;
        ImageReader reader = null;

        try {
            bais = new ByteArrayInputStream(data);
            stream = ImageIO.createImageInputStream(bais);

            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) throw new IOException("No GIF ImageReader available");
            reader = readers.next();
            reader.setInput(stream, false);

            int frameCount = reader.getNumImages(true);
            for (int i = 0; i < frameCount; i++) {
                BufferedImage img = reader.read(i);

                // Per-frame delay in 1/100 sec → seconds
                int delayCentis = getDelayTime(reader, i);
                if (delayCentis > 0) {
                    frameDuration = delayCentis / 100f;
                }

                // Convert BufferedImage to Pixmap → TextureRegion
                Pixmap pixmap = bufferedImageToPixmap(img);
                Texture texture = new Texture(pixmap);
                frames.add(new TextureRegion(texture));
                pixmap.dispose();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) reader.dispose();
            try {
                if (stream != null) stream.close();
            } catch (IOException ignored) {}
            try {
                if (bais != null) bais.close();
            } catch (IOException ignored) {}
        }

        Animation<TextureRegion> anim = new Animation<TextureRegion>(frameDuration, frames);
        anim.setPlayMode(playMode);
        return anim;
    }

    /**
     * Read GIF GraphicControlExtension.delayTime (centiseconds).
     */
    private static int getDelayTime(ImageReader reader, int frameIndex) {
        try {
            IIOMetadata meta = reader.getImageMetadata(frameIndex);
            String format = "javax_imageio_gif_image_1.0";
            Node tree = meta.getAsTree(format);
            NodeList children = tree.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if ("GraphicControlExtension".equals(node.getNodeName())) {
                    NamedNodeMap attrs = node.getAttributes();
                    Node delayAttr = attrs.getNamedItem("delayTime");
                    if (delayAttr != null) {
                        return Integer.parseInt(delayAttr.getNodeValue()); // centiseconds
                    }
                }
            }
        } catch (Exception ignored) {}
        return 10; // fallback 100ms
    }

    /**
     * Convert AWT BufferedImage → LibGDX Pixmap (RGBA8888).
     * Note: flips Y so it matches LibGDX coordinate system used by typical sprite drawing.
     */
    private static Pixmap bufferedImageToPixmap(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);

        int[] pixels = new int[width * height];
        img.getRGB(0, 0, width, height, pixels, 0, width);

        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels[idx++];

                // 올바른 ARGB → RGBA 변환
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = (argb) & 0xFF;

                // Y축 반전 없이 그대로 LibGDX 기준으로 (Y 아래가 0)
                int rgba = (r << 24) | (g << 16) | (b << 8) | a;
                pixmap.drawPixel(x, y, rgba);
            }
        }

        return pixmap;
    }
}
