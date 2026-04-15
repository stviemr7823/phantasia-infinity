// phantasia-j2d/src/main/java/com/phantasia/j2d/engine/ResourceCache.java
package com.phantasia.j2d.engine;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized resource loader and cache for the j2d frontend.
 *
 * <p>Loads and caches:</p>
 * <ul>
 *   <li>{@link BufferedImage} assets by classpath path</li>
 *   <li>{@link Font} objects from embedded TTF/OTF resources</li>
 *   <li>Shared parchment {@link TexturePaint} and gradient resources</li>
 * </ul>
 *
 * <p>Thread-safe via ConcurrentHashMap. Resources are loaded lazily on
 * first request and cached for the lifetime of the process.</p>
 *
 * <h3>Font Inventory</h3>
 * <table>
 *   <tr><td>Cinzel</td><td>Headers, panel titles, character names</td></tr>
 *   <tr><td>Crimson Text</td><td>Dialogue, narration, item descriptions</td></tr>
 *   <tr><td>Share Tech Mono</td><td>HP/MP numbers, stat values, combat numbers</td></tr>
 * </table>
 *
 * Fonts are loaded from {@code /fonts/} on the classpath. If a font file
 * is missing, the cache falls back to a system serif/monospace font.
 */
public class ResourceCache {

    // -------------------------------------------------------------------------
    // Caches
    // -------------------------------------------------------------------------

    private final Map<String, BufferedImage> images = new ConcurrentHashMap<>();
    private final Map<String, Font>         fonts  = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Singleton (one cache per game instance)
    // -------------------------------------------------------------------------

    private static final ResourceCache INSTANCE = new ResourceCache();

    public static ResourceCache get() { return INSTANCE; }

    private ResourceCache() {
        loadCoreFonts();
    }

    // -------------------------------------------------------------------------
    // Image loading
    // -------------------------------------------------------------------------

    /**
     * Returns a cached image, loading it from the classpath on first access.
     *
     * @param path  classpath resource path (e.g. "/sprites/warrior_idle.png")
     * @return the image, or a 1×1 magenta placeholder on failure
     */
    public BufferedImage getImage(String path) {
        return images.computeIfAbsent(path, this::loadImage);
    }

    /**
     * Pre-loads an image into the cache.
     */
    public void preload(String path) {
        getImage(path);
    }

    private BufferedImage loadImage(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                System.err.println("[ResourceCache] Image not found: " + path);
                return placeholderImage();
            }
            return ImageIO.read(is);
        } catch (IOException e) {
            System.err.println("[ResourceCache] Failed to load image: " + path + " — " + e.getMessage());
            return placeholderImage();
        }
    }

    private BufferedImage placeholderImage() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFFFF00FF); // magenta = missing asset
        return img;
    }

    // -------------------------------------------------------------------------
    // Font loading
    // -------------------------------------------------------------------------

    /** Header font — Cinzel, all-caps Roman serif. */
    public static final String FONT_HEADER = "Cinzel";

    /** Body font — Crimson Text, old-style serif. */
    public static final String FONT_BODY = "CrimsonText";

    /** Data font — Share Tech Mono, for numbers and stats. */
    public static final String FONT_DATA = "ShareTechMono";

    /**
     * Returns a cached font at the requested size and style.
     *
     * @param name  one of FONT_HEADER, FONT_BODY, FONT_DATA
     * @param style Font.PLAIN, Font.BOLD, etc.
     * @param size  point size
     * @return the derived font
     */
    public Font getFont(String name, int style, float size) {
        Font base = fonts.get(name);
        if (base == null) {
            // Fallback
            base = new Font(Font.SERIF, Font.PLAIN, 12);
        }
        return base.deriveFont(style, size);
    }

    private void loadCoreFonts() {
        loadFont(FONT_HEADER, "/fonts/Cinzel-Regular.ttf");
        loadFont(FONT_BODY,   "/fonts/CrimsonText-Regular.ttf");
        loadFont(FONT_DATA,   "/fonts/ShareTechMono-Regular.ttf");
    }

    private void loadFont(String key, String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                System.err.println("[ResourceCache] Font not found: " + path
                        + " — using system fallback.");
                fonts.put(key, fallbackFont(key));
                return;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, is);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            fonts.put(key, font);
            System.out.println("[ResourceCache] Loaded font: " + key);
        } catch (Exception e) {
            System.err.println("[ResourceCache] Failed to load font: " + path
                    + " — " + e.getMessage());
            fonts.put(key, fallbackFont(key));
        }
    }

    private Font fallbackFont(String key) {
        return switch (key) {
            case FONT_DATA -> new Font(Font.MONOSPACED, Font.PLAIN, 12);
            default        -> new Font(Font.SERIF, Font.PLAIN, 12);
        };
    }

    // -------------------------------------------------------------------------
    // Parchment resources
    // -------------------------------------------------------------------------

    private TexturePaint parchmentPaint;

    /**
     * Returns a TexturePaint that tiles a parchment noise texture.
     * Falls back to a solid dark brown if the texture is not available.
     */
    public TexturePaint getParchmentPaint() {
        if (parchmentPaint == null) {
            parchmentPaint = buildParchmentPaint();
        }
        return parchmentPaint;
    }

    private TexturePaint buildParchmentPaint() {
        // Try to load a baked noise texture
        BufferedImage tex = loadImage("/textures/parchment_noise.png");
        if (tex.getWidth() == 1) {
            // No texture — generate a simple noise pattern procedurally
            tex = generateNoiseTexture(64, 64);
        }
        return new TexturePaint(tex,
                new Rectangle(0, 0, tex.getWidth(), tex.getHeight()));
    }

    /**
     * Generates a simple procedural noise texture for parchment surfaces.
     * Used as a fallback when the authored texture is not yet available.
     */
    private BufferedImage generateNoiseTexture(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.util.Random rng = new java.util.Random(42); // deterministic

        int baseR = 0x2A, baseG = 0x21, baseB = 0x18; // parchment surface #2A2118
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int noise = rng.nextInt(16) - 8; // -8 to +7
                int r = clamp(baseR + noise);
                int g = clamp(baseG + noise);
                int b = clamp(baseB + noise);
                img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}