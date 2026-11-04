package com.phantasia.j2d.tour;

import com.phantasia.core.world.TileType;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Loads Kenney tiles and pre-scales them to the 128px Adventure Scale.
 * Includes fallback color logic for missing assets and automated directory discovery.
 */
public class KenneyTileLoader {

    public static final int DISPLAY_SIZE = 128; // The Native Target Size

    // Fallback colors used if PNG assets are missing
    private static final Map<TileType, Color> FALLBACK_COLORS = new EnumMap<>(TileType.class);
    static {
        FALLBACK_COLORS.put(TileType.OCEAN,    new Color(25,  50, 140));
        FALLBACK_COLORS.put(TileType.ROAD,     new Color(185, 160, 110));
        FALLBACK_COLORS.put(TileType.PLAINS,   new Color(85,  160, 58));
        FALLBACK_COLORS.put(TileType.FOREST,   new Color(22,  95,  22));
        FALLBACK_COLORS.put(TileType.MOUNTAIN, new Color(118, 155, 160));
        FALLBACK_COLORS.put(TileType.SWAMP,    new Color(72,  98,  48));
        FALLBACK_COLORS.put(TileType.TOWN,     new Color(210, 185, 72));
        FALLBACK_COLORS.put(TileType.DUNGEON,  new Color(30,  10,  45));
    }

    private static final Map<TileType, String> FILENAMES = new EnumMap<>(TileType.class);
    static {
        FILENAMES.put(TileType.OCEAN,    "OCEAN.png");
        FILENAMES.put(TileType.ROAD,     "ROAD.png");
        FILENAMES.put(TileType.PLAINS,   "PLAINS.png");
        FILENAMES.put(TileType.FOREST,   "FOREST.png");
        FILENAMES.put(TileType.MOUNTAIN, "MOUNTAIN.png");
        FILENAMES.put(TileType.SWAMP,    "SWAMP.png");
        FILENAMES.put(TileType.TOWN,     "TOWN.png");
        FILENAMES.put(TileType.DUNGEON,  "DUNGEON.png");
    }

    private final Map<TileType, BufferedImage> tiles = new EnumMap<>(TileType.class);
    private final String assetDir;
    private boolean loaded = false;

    public KenneyTileLoader(String assetDir) {
        this.assetDir = assetDir;
    }

    /**
     * Finds the tile directory by searching common project relative paths.
     */
    public static String defaultAssetDir() {
        String[] candidates = {
                "tiles",
                "../tiles",
                "assets/tiles",
                "../assets/tiles",
        };

        for (String candidate : candidates) {
            File dir = new File(candidate);
            if (dir.isDirectory()) {
                File[] pngs = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".png"));
                if (pngs != null && pngs.length > 0) {
                    System.out.println("[KenneyTileLoader] Discovery: Found " + dir.getAbsolutePath());
                    return candidate;
                }
            }
        }
        return "tiles";
    }

    public void load() {
        if (loaded) return;
        loaded = true;

        for (TileType type : TileType.values()) {
            String filename = FILENAMES.get(type);
            if (filename == null) continue;

            File f = new File(assetDir, filename);
            if (!f.exists()) continue;

            BufferedImage scaled = loadAndScale(f);
            if (scaled != null) {
                tiles.put(type, scaled);
            }
        }
    }

    private BufferedImage loadAndScale(File file) {
        try {
            BufferedImage raw = ImageIO.read(file);
            if (raw == null) return null;

            // Pre-scale to 128px ARGB once at startup
            BufferedImage scaled = new BufferedImage(DISPLAY_SIZE, DISPLAY_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();

            // Bilinear provides a painterly softness
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(raw, 0, 0, DISPLAY_SIZE, DISPLAY_SIZE, null);
            g.dispose();

            return scaled;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Returns the pre-scaled 128px tile image.
     */
    public BufferedImage getTile(TileType type) {
        return tiles.get(type);
    }

    /**
     * Returns the fallback color if the image is missing.
     * Required by MapPanel.java.
     */
    public Color getFallbackColor(TileType type) {
        return FALLBACK_COLORS.getOrDefault(type, Color.BLACK);
    }

    public int loadedCount() {
        return tiles.size();
    }
}
