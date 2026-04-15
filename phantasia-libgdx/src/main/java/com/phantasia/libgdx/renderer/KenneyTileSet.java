package com.phantasia.libgdx.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.phantasia.core.world.TileType;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/**
 * Loads the Kenney Map Pack tile PNGs and the party sprite, mapping them
 * to {@link TileType} and exposing the party texture separately.
 *
 * <h3>Asset discovery — multi-path probe</h3>
 * Previous versions used only {@code Gdx.files.internal("tiles/...")} which
 * resolves relative to the LibGDX assets root and silently produced fallback
 * colors whenever the PNG files lived somewhere else on disk.
 *
 * This version mirrors {@code j2d.KenneyTileLoader.defaultAssetDir()}: it
 * probes a prioritised list of candidate directories using both
 * {@code Gdx.files.internal} (for the packaged assets root) and
 * {@code Gdx.files.absolute} (for filesystem-relative paths discovered via
 * {@link File}).  The first directory that contains at least one tile PNG is
 * used for all subsequent loads.
 *
 * <h3>Candidate search order</h3>
 * <ol>
 *   <li>{@code tiles/}           — internal asset root sub-directory (standard LibGDX layout)
 *   <li>{@code assets/tiles/}    — internal asset root with explicit prefix
 *   <li>{@code tiles/}           — absolute: JVM working directory / tiles
 *   <li>{@code ../tiles/}        — absolute: one level up (running from a sub-module dir)
 *   <li>{@code assets/tiles/}    — absolute: JVM working dir / assets / tiles
 *   <li>{@code ../assets/tiles/} — absolute: one up / assets / tiles
 * </ol>
 *
 * <h3>Fallback</h3>
 * If no directory is found, every tile falls back to a solid color and the
 * party token falls back to the gold ShapeRenderer rectangle.  The game
 * always runs regardless of whether PNGs are present.
 *
 * <h3>Party sprite transparency</h3>
 * LibGDX respects PNG alpha natively via {@code SpriteBatch}, so no
 * white-pixel transparency pass is needed.
 */
public class KenneyTileSet {

    // -------------------------------------------------------------------------
    // Asset filenames
    // -------------------------------------------------------------------------

    private static final String PLAYER_FILENAME = "PLAYER.png";

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
    // Fallback solid colors — identical values to j2d KenneyTileLoader
    // -------------------------------------------------------------------------

    static final Map<TileType, Color> FALLBACK_COLORS = new EnumMap<>(TileType.class);
    static {
        FALLBACK_COLORS.put(TileType.OCEAN,    new Color(0.098f, 0.196f, 0.549f, 1f));
        FALLBACK_COLORS.put(TileType.ROAD,     new Color(0.725f, 0.627f, 0.431f, 1f));
        FALLBACK_COLORS.put(TileType.PLAINS,   new Color(0.333f, 0.627f, 0.227f, 1f));
        FALLBACK_COLORS.put(TileType.FOREST,   new Color(0.086f, 0.373f, 0.086f, 1f));
        FALLBACK_COLORS.put(TileType.MOUNTAIN, new Color(0.463f, 0.608f, 0.627f, 1f));
        FALLBACK_COLORS.put(TileType.SWAMP,    new Color(0.282f, 0.384f, 0.188f, 1f));
        FALLBACK_COLORS.put(TileType.TOWN,     new Color(0.824f, 0.725f, 0.282f, 1f));
        FALLBACK_COLORS.put(TileType.DUNGEON,  new Color(0.118f, 0.039f, 0.176f, 1f));
    }

    // -------------------------------------------------------------------------
    // Candidate directories — probed in order, first winner is used
    // -------------------------------------------------------------------------

    /**
     * Directories probed using {@code Gdx.files.internal()} (asset-root relative).
     * These work when the Gradle {@code run} task has {@code workingDir} set to
     * {@code phantasia-libgdx/} and the PNG files live in the {@code assets/}
     * source set.
     */
    private static final String[] INTERNAL_CANDIDATES = {
            "tiles/",
            "assets/tiles/",
    };

    /**
     * Directories probed using {@code Gdx.files.absolute()} after resolving
     * against the JVM working directory via {@link File}.  These mirror the
     * probing logic of {@code j2d.KenneyTileLoader.defaultAssetDir()} and
     * succeed when running from an IDE or the project root.
     */
    private static final String[] RELATIVE_CANDIDATES = {
            "tiles",
            "../tiles",
            "assets/tiles",
            "../assets/tiles",
            "phantasia-libgdx/assets/tiles",
    };

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final Map<TileType, Texture> textures     = new EnumMap<>(TileType.class);
    private final Map<TileType, String>  queuedPaths  = new EnumMap<>(TileType.class);
    private       Texture                playerTexture = null;
    private       String                 playerQueuedPath = null;
    private       int                    loadedCount   = 0;

    /** The resolved tile directory, null if not found yet. */
    private String resolvedTileDir = null;

    // -------------------------------------------------------------------------
    // Phase 1 — queue
    // -------------------------------------------------------------------------

    /**
     * Finds the tile directory and queues all tile PNGs with the given
     * {@link AssetManager}.  Files absent from the found directory are skipped
     * silently — fallback colors handle them.
     */
    public void queueAll(AssetManager assets) {
        resolvedTileDir = findTileDirectory();

        if (resolvedTileDir == null) {
            System.err.println("[KenneyTileSet] No tile directory found — using fallback colors.");
            System.err.println("  JVM working dir: " + new File(".").getAbsolutePath());
            System.err.println("  Searched internal: " + String.join(", ", INTERNAL_CANDIDATES));
            System.err.println("  Searched absolute: " + String.join(", ", RELATIVE_CANDIDATES));
            System.err.println("  Place PNG tiles in one of those locations to enable tile graphics.");
            return;
        }

        System.out.println("[KenneyTileSet] Using tile dir: " + resolvedTileDir);

        for (Map.Entry<TileType, String> entry : FILENAMES.entrySet()) {
            FileHandle fh = resolveFile(resolvedTileDir, entry.getValue());
            if (fh != null && fh.exists()) {
                assets.load(fh.path(), Texture.class);
                queuedPaths.put(entry.getKey(), fh.path());
                System.out.println("[KenneyTileSet] Queued: " + fh.path());
            } else {
                System.out.println("[KenneyTileSet] Missing (fallback color): "
                        + entry.getValue());
            }
        }

        // Party sprite — try same directory as tiles, then parent
        FileHandle playerFh = resolveFile(resolvedTileDir, "../" + PLAYER_FILENAME);
        if (playerFh == null || !playerFh.exists()) {
            playerFh = resolveFile(resolvedTileDir, PLAYER_FILENAME);
        }
        if (playerFh != null && playerFh.exists()) {
            assets.load(playerFh.path(), Texture.class);
            playerQueuedPath = playerFh.path();
            System.out.println("[KenneyTileSet] Queued player sprite: " + playerFh.path());
        } else {
            System.out.println("[KenneyTileSet] Missing player sprite (fallback token): "
                    + PLAYER_FILENAME);
        }
    }

    // -------------------------------------------------------------------------
    // Phase 2 — fetch
    // -------------------------------------------------------------------------

    /**
     * Retrieves loaded textures from the {@link AssetManager}.
     * Call once after {@code AssetManager.update()} returns {@code true}.
     */
    public void fetchAll(AssetManager assets) {
        for (Map.Entry<TileType, String> entry : queuedPaths.entrySet()) {
            Texture tex = assets.get(entry.getValue(), Texture.class);
            tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            textures.put(entry.getKey(), tex);
            loadedCount++;
        }

        if (playerQueuedPath != null) {
            playerTexture = assets.get(playerQueuedPath, Texture.class);
            playerTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
            System.out.println("[KenneyTileSet] Fetched player sprite: "
                    + playerTexture.getWidth() + "x" + playerTexture.getHeight() + "px");
        }

        System.out.println("[KenneyTileSet] Fetched " + loadedCount
                + "/" + TileType.values().length + " tile textures."
                + (loadedCount == 0 ? " (no PNGs found — using solid colors)" : ""));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the tile texture for the given type, or {@code null} if absent. */
    public Texture getTexture(TileType type) {
        return textures.get(type);
    }

    /**
     * Returns the {@code PLAYER.png} texture, or {@code null} if absent.
     * {@link com.phantasia.libgdx.SmoothPartyActor} falls back to the gold
     * ShapeRenderer rectangle when this is null.
     */
    public Texture getPlayerTexture() {
        return playerTexture;
    }

    public Color   getFallbackColor(TileType type) {
        return FALLBACK_COLORS.getOrDefault(type, Color.BLACK);
    }

    public int     loadedCount() { return loadedCount;     }
    public boolean hasTextures() { return loadedCount > 0; }

    /** Returns the resolved tile directory path, or null if not found. Useful for diagnostics. */
    public String getResolvedTileDir() { return resolvedTileDir; }

    // Textures are owned by AssetManager — do not dispose here.

    // -------------------------------------------------------------------------
    // Asset discovery
    // -------------------------------------------------------------------------

    /**
     * Probes candidate directories in priority order and returns the path of
     * the first one that contains at least one tile PNG.
     *
     * Internal candidates are checked first (correct classpath/asset-root layout).
     * Absolute/filesystem candidates are checked second (IDE / project-root launch).
     *
     * @return the winning directory path string, or {@code null} if nothing found
     */
    private static String findTileDirectory() {
        // --- Internal candidates (Gdx.files.internal, asset-root relative) ---
        for (String candidate : INTERNAL_CANDIDATES) {
            for (String filename : FILENAMES.values()) {
                FileHandle fh = Gdx.files.internal(candidate + filename);
                if (fh.exists()) {
                    System.out.println("[KenneyTileSet] Found internal tile dir: " + candidate);
                    return candidate;   // signal: use internal resolution for this dir
                }
            }
        }

        // --- Absolute / filesystem candidates ---
        for (String rel : RELATIVE_CANDIDATES) {
            File dir = new File(rel).getAbsoluteFile();
            if (!dir.isDirectory()) continue;
            for (String filename : FILENAMES.values()) {
                if (new File(dir, filename).exists()) {
                    System.out.println("[KenneyTileSet] Found absolute tile dir: "
                            + dir.getAbsolutePath());
                    return dir.getAbsolutePath();   // absolute path
                }
            }
        }

        return null;
    }

    /**
     * Resolves a file handle for {@code filename} within {@code dir}.
     *
     * If {@code dir} is an absolute path (starts with / or contains :\) the
     * file is opened via {@code Gdx.files.absolute()}; otherwise it is opened
     * via {@code Gdx.files.internal()} so the asset manager can track it.
     */
    private static FileHandle resolveFile(String dir, String filename) {
        if (dir == null) return null;

        boolean isAbsolute = dir.startsWith("/")
                || (dir.length() > 1 && dir.charAt(1) == ':'); // Windows C:\...

        String fullPath = dir.endsWith("/") || dir.endsWith(File.separator)
                ? dir + filename
                : dir + "/" + filename;

        // Normalise separators and collapse "../" segments
        try {
            fullPath = new File(fullPath).getCanonicalPath();
        } catch (java.io.IOException ignored) {
            fullPath = new File(fullPath).getAbsolutePath();
        }

        if (isAbsolute || new File(dir).isAbsolute()) {
            return Gdx.files.absolute(fullPath);
        } else {
            // Re-express as relative for internal resolution
            String rel = dir.endsWith("/") ? dir + filename : dir + "/" + filename;
            return Gdx.files.internal(rel);
        }
    }
}