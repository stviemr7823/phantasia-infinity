// phantasia-jme/phantasia-jme-world/src/main/java/com/phantasia/jme/world/HeightmapData.java
package com.phantasia.jme.world;

import com.phantasia.core.world.TileType;
import com.phantasia.core.world.WorldMap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Loads or generates the heightmap data that drives TerrainMeshBuilder.
 *
 * The heightmap is a 2D float array indexed [col][row], where each value
 * is a normalised height in [0.0, 1.0].  Dimensions are
 * (mapWidth + 1) x (mapHeight + 1) — one sample per tile corner, so a
 * 32x32 map produces a 33x33 grid of elevation samples.
 *
 * LOADING:
 *   Attempts to read a greyscale PNG at the path supplied to load().
 *   Pixel intensity (0–255 from any channel) maps linearly to [0.0, 1.0].
 *   The PNG must be exactly (mapWidth+1) x (mapHeight+1) pixels.
 *   If the file is absent, unreadable, or the wrong size, the fallback
 *   generator runs automatically — the game is always playable.
 *
 * FALLBACK:
 *   Derives normalised heights from the TileType of the nearest tile,
 *   using the table from the design document.  Corner samples average
 *   the heights of their up-to-four adjacent tiles.
 *
 * NO JME DEPENDENCY:
 *   Uses only java.awt and java.io.  Fully unit-testable in isolation.
 */
public final class HeightmapData {

    private static final Logger LOG = Logger.getLogger(HeightmapData.class.getName());

    // -------------------------------------------------------------------------
    // Tile-type fallback heights (normalised, from design doc §4.1)
    // -------------------------------------------------------------------------

    private static final float H_OCEAN    = 0.05f;
    private static final float H_SWAMP    = 0.12f;
    private static final float H_PLAINS   = 0.22f;
    private static final float H_ROAD     = 0.22f;
    private static final float H_TOWN     = 0.22f;
    private static final float H_DUNGEON  = 0.18f;
    private static final float H_FOREST   = 0.32f;
    private static final float H_MOUNTAIN = 0.72f;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Normalised heights [col][row], dimensions (width+1) x (height+1). */
    private final float[][] data;
    private final int       cols;   // mapWidth  + 1
    private final int       rows;   // mapHeight + 1

    // -------------------------------------------------------------------------
    // Construction — use the static factory methods below
    // -------------------------------------------------------------------------

    private HeightmapData(float[][] data) {
        this.data = data;
        this.cols = data.length;
        this.rows = data[0].length;
    }

    // -------------------------------------------------------------------------
    // Static factory: load PNG or fall back to tile-type defaults
    // -------------------------------------------------------------------------

    /**
     * Attempts to load a greyscale heightmap PNG from {@code pngPath}.
     * Falls back to tile-type defaults if the file is missing or invalid.
     *
     * @param pngPath  absolute or relative path to the PNG file
     * @param worldMap the WorldMap whose dimensions and tile types are used
     *                 for validation and the fallback generator
     * @return a HeightmapData ready for consumption by TerrainMeshBuilder
     */
    public static HeightmapData load(String pngPath, WorldMap worldMap) {
        int expectedCols = worldMap.getWidth()  + 1;
        int expectedRows = worldMap.getHeight() + 1;

        File pngFile = new File(pngPath);
        if (pngFile.exists()) {
            try {
                BufferedImage img = ImageIO.read(pngFile);
                if (img == null) {
                    LOG.warning("[HeightmapData] ImageIO could not decode: " + pngPath
                            + " — using tile-type fallback.");
                } else if (img.getWidth() != expectedCols || img.getHeight() != expectedRows) {
                    LOG.warning("[HeightmapData] PNG size " + img.getWidth() + "x" + img.getHeight()
                            + " does not match expected " + expectedCols + "x" + expectedRows
                            + " — using tile-type fallback.");
                } else {
                    LOG.info("[HeightmapData] Loaded heightmap PNG: " + pngPath);
                    return fromImage(img, expectedCols, expectedRows);
                }
            } catch (IOException e) {
                LOG.warning("[HeightmapData] Failed to read PNG (" + e.getMessage()
                        + ") — using tile-type fallback.");
            }
        } else {
            LOG.info("[HeightmapData] No heightmap PNG found at " + pngPath
                    + " — using tile-type fallback.");
        }

        return fromTileTypes(worldMap);
    }

    /**
     * Generates a HeightmapData directly from tile-type defaults, bypassing
     * any PNG.  Useful for headless tests or when no PNG has been authored yet.
     */
    public static HeightmapData fromTileTypes(WorldMap worldMap) {
        int cols = worldMap.getWidth()  + 1;
        int rows = worldMap.getHeight() + 1;

        float[][] data = new float[cols][rows];

        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                data[col][row] = cornerHeight(worldMap, col, row);
            }
        }

        LOG.info("[HeightmapData] Generated from tile-type fallback ("
                + cols + "x" + rows + ").");
        return new HeightmapData(data);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static HeightmapData fromImage(BufferedImage img, int cols, int rows) {
        float[][] data = new float[cols][rows];
        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                // Extract red channel (0–255); all channels equal in greyscale
                int rgb   = img.getRGB(col, row);
                int red   = (rgb >> 16) & 0xFF;
                data[col][row] = red / 255.0f;
            }
        }
        return new HeightmapData(data);
    }

    /**
     * Computes the height at a corner sample by averaging the normalised
     * heights of the up-to-four tiles that share that corner.
     *
     * Corner (col, row) is shared by tiles at:
     *   (col-1, row-1),  (col, row-1),
     *   (col-1, row  ),  (col, row  )
     * — clamped to the valid tile range [0, mapWidth) x [0, mapHeight).
     */
    private static float cornerHeight(WorldMap worldMap, int col, int row) {
        int w = worldMap.getWidth();
        int h = worldMap.getHeight();

        float sum   = 0f;
        int   count = 0;

        // The four tiles that share this corner
        int[][] neighbors = {
                { col - 1, row - 1 },
                { col,     row - 1 },
                { col - 1, row     },
                { col,     row     }
        };

        for (int[] n : neighbors) {
            int tx = n[0], ty = n[1];
            if (tx >= 0 && tx < w && ty >= 0 && ty < h) {
                sum += tileHeight(worldMap.getTile(tx, ty).getType());
                count++;
            }
        }

        return (count > 0) ? (sum / count) : H_PLAINS;
    }

    private static float tileHeight(TileType type) {
        return switch (type) {
            case OCEAN    -> H_OCEAN;
            case SWAMP    -> H_SWAMP;
            case PLAINS   -> H_PLAINS;
            case ROAD     -> H_ROAD;
            case TOWN     -> H_TOWN;
            case DUNGEON  -> H_DUNGEON;
            case FOREST   -> H_FOREST;
            case MOUNTAIN -> H_MOUNTAIN;
        };
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the normalised height [0.0, 1.0] at grid corner (col, row).
     * Indices must be in range: col in [0, cols), row in [0, rows).
     */
    public float get(int col, int row) {
        return data[col][row];
    }

    /** Number of columns — equals mapWidth + 1. */
    public int getCols() { return cols; }

    /** Number of rows — equals mapHeight + 1. */
    public int getRows() { return rows; }

    /**
     * Returns a direct reference to the underlying float[][] for use by
     * TerrainMeshBuilder and HeightSampler.  Treat as read-only.
     */
    public float[][] getData() { return data; }
}