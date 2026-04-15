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
 * Loads the processed Kenney Map Pack tiles and maps them to {@link TileType}.
 *
 * <h3>Asset location</h3>
 * Tiles are loaded from a discovered directory (see {@link #defaultAssetDir()}):
 * <pre>
 *   &lt;working_dir&gt;/assets/tiles/OCEAN.png
 *   &lt;working_dir&gt;/assets/tiles/ROAD.png
 *   ... etc.
 * </pre>
 * The processed PNGs are generated from the Kenney Map Pack tilesheet
 * ({@code mapPack_tilesheet_2X.png}) and stored at 128×128 px.  They are
 * scaled to {@link #DISPLAY_SIZE} (64 px) at load time.
 *
 * <h3>Fallback</h3>
 * If any tile image is missing or fails to load, that {@link TileType} falls
 * back to a solid-colour rectangle drawn directly in {@link MapPanel}.  The
 * touring engine always runs even if the asset folder is absent — no PNG
 * files are required for the game to start.
 *
 * <h3>Usage</h3>
 * <pre>
 *   KenneyTileLoader loader = new KenneyTileLoader("assets/tiles");
 *   loader.load();
 *   BufferedImage img = loader.getTile(TileType.PLAINS);
 *   // img is null if the file was missing — MapPanel handles the fallback
 * </pre>
 */
public class KenneyTileLoader {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Display tile size in pixels — must match {@link MapPanel#TILE}. */
    public static final int DISPLAY_SIZE = 64;

    // -------------------------------------------------------------------------
    // Fallback solid colours
    // -------------------------------------------------------------------------

    /**
     * Solid colours used when tile images are unavailable.
     * Values match the TourFrame / DungeonPanel palette so the game looks
     * consistent whether or not PNG assets are present.
     */
    static final Map<TileType, Color> FALLBACK_COLORS = new EnumMap<>(TileType.class);
    static {
        FALLBACK_COLORS.put(TileType.OCEAN,    new Color( 25,  50, 140));
        FALLBACK_COLORS.put(TileType.ROAD,     new Color(185, 160, 110));
        FALLBACK_COLORS.put(TileType.PLAINS,   new Color( 85, 160,  58));
        FALLBACK_COLORS.put(TileType.FOREST,   new Color( 22,  95,  22));
        FALLBACK_COLORS.put(TileType.MOUNTAIN, new Color(118, 155, 160));
        FALLBACK_COLORS.put(TileType.SWAMP,    new Color( 72,  98,  48));
        FALLBACK_COLORS.put(TileType.TOWN,     new Color(210, 185,  72));
        FALLBACK_COLORS.put(TileType.DUNGEON,  new Color( 30,  10,  45));
    }

    // -------------------------------------------------------------------------
    // Filename mapping
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final Map<TileType, BufferedImage> tiles = new EnumMap<>(TileType.class);
    private final String  assetDir;
    private       boolean loaded      = false;
    private       int     loadedCount = 0;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public KenneyTileLoader(String assetDir) {
        this.assetDir = assetDir;
    }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    /**
     * Loads all tile images from the asset directory.
     * Safe to call multiple times — only runs once.
     * Missing files are silently skipped; fallback colours handle them.
     */
    public void load() {
        if (loaded) return;
        loaded = true;

        for (TileType type : TileType.values()) {
            String filename = FILENAMES.get(type);
            if (filename == null) continue;

            File f = new File(assetDir, filename);
            if (!f.exists()) {
                System.out.println("[KenneyTileLoader] Missing: " + f.getPath()
                        + " — using fallback color.");
                continue;
            }

            try {
                BufferedImage raw = ImageIO.read(f);
                if (raw == null) continue;

                // Scale to DISPLAY_SIZE using high-quality resampling
                BufferedImage scaled = new BufferedImage(
                        DISPLAY_SIZE, DISPLAY_SIZE, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(raw, 0, 0, DISPLAY_SIZE, DISPLAY_SIZE, null);
                g.dispose();

                tiles.put(type, scaled);
                loadedCount++;
                System.out.println("[KenneyTileLoader] " + type.name() + " → " + filename);

            } catch (IOException e) {
                System.err.println("[KenneyTileLoader] Failed to load "
                        + f.getPath() + ": " + e.getMessage());
            }
        }

        System.out.println("[KenneyTileLoader] Loaded " + loadedCount
                + "/" + TileType.values().length + " tile images from: "
                + new File(assetDir).getAbsolutePath());
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the scaled tile image for the given {@link TileType},
     * or {@code null} if the image was not loaded.
     * {@link MapPanel} falls back to {@link #getFallbackColor} when this
     * returns {@code null}.
     */
    public BufferedImage getTile(TileType type) {
        return tiles.get(type);
    }

    /**
     * Returns the fallback solid colour for the given {@link TileType}.
     * Used by {@link MapPanel} when {@link #getTile} returns {@code null}.
     */
    public Color getFallbackColor(TileType type) {
        return FALLBACK_COLORS.getOrDefault(type, Color.BLACK);
    }

    public boolean isLoaded()    { return loaded;      }
    public int     loadedCount() { return loadedCount; }

    // -------------------------------------------------------------------------
    // Asset directory discovery
    // -------------------------------------------------------------------------

    /**
     * Resolves the tile asset directory by searching a prioritised list of
     * candidate paths relative to the JVM working directory.
     *
     * <p>Search order:
     * <ol>
     *   <li>{@code tiles/}           — Phantasia project root layout
     *   <li>{@code ../tiles/}        — one level up from a sub-module working dir
     *   <li>{@code assets/tiles/}    — alternative layout
     *   <li>{@code ../assets/tiles/} — alternative from a sub-module
     * </ol>
     *
     * <p>The first directory that exists <em>and</em> contains at least one
     * {@code .png} file is used.  Diagnostic output is printed so the correct
     * path is visible without a debugger.
     *
     * <p>If no directory is found, {@code "tiles"} is returned as a safe
     * default.  {@link #load()} will then print missing-file messages for
     * each tile type and the game will run entirely on fallback colours —
     * no PNG files are required.
     *
     * @return path string to pass to {@link KenneyTileLoader#KenneyTileLoader(String)}
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
                File[] pngs = dir.listFiles(
                        f -> f.isFile() && f.getName().endsWith(".png"));
                if (pngs != null && pngs.length > 0) {
                    System.out.println("[KenneyTileLoader] Found tile dir: "
                            + dir.getAbsolutePath()
                            + "  (" + pngs.length + " PNGs)");
                    return candidate;
                }
            }
        }

        System.err.println("[KenneyTileLoader] Could not find tile directory.");
        System.err.println("  JVM working dir: "
                + new File(".").getAbsolutePath());
        System.err.println("  Searched:");
        for (String c : candidates)
            System.err.println("    " + new File(c).getAbsolutePath());
        System.err.println("  -> Falling back to solid colors.");

        return "tiles";
    }
}