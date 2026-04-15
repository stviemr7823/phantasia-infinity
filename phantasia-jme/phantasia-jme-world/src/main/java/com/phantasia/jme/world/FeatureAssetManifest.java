// e.g. phantasia-jme/phantasia-jme-world/src/main/java/com/phantasia/jme/world/FeatureAssetManifest.java
// (same class copied to libgdx and j2d equivalents)
package com.phantasia.jme.world; // change package per frontend

import com.phantasia.core.world.FeatureType;

import java.io.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Resolves feature (type, id) pairs to frontend-specific asset paths.
 *
 * Reads a simple Java properties file — features.manifest — from the
 * frontend's asset directory. Keys are "TYPE_ID" (e.g. "TOWN_0"),
 * values are asset paths appropriate to the frontend:
 *
 *   JME:    TOWN_0=Models/Features/TOWN_0.j3o
 *   LibGDX: TOWN_0=tiles/features/pendragon.png
 *   J2D:    TOWN_0=PENDRAGON.png
 *
 * If the manifest file is absent or a key is missing, getAssetHint()
 * returns null and the caller falls back to procedural/color rendering.
 * The game always runs regardless of manifest presence.
 *
 * MANIFEST FORMAT (standard Java .properties):
 *   # Comments start with #
 *   TOWN_0=Models/Features/TOWN_0.j3o
 *   TOWN_1=Models/Features/TOWN_1.j3o
 *   DUNGEON_1=Models/Features/DUNGEON_1.j3o
 *   NPC_101=Models/NPCs/Filmon.j3o
 */
public final class FeatureAssetManifest {

    private static final Logger LOG =
            Logger.getLogger(FeatureAssetManifest.class.getName());

    private final Properties props = new Properties();
    private boolean loaded = false;

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    /**
     * Loads the manifest from the given path.
     * Silently succeeds with an empty manifest if the file is absent.
     */
    public void load(String manifestPath) {
        File file = new File(manifestPath);
        if (!file.exists()) {
            LOG.info("[FeatureAssetManifest] No manifest at "
                    + file.getAbsolutePath() + " — all features use fallback.");
            loaded = true;
            return;
        }

        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
            LOG.info("[FeatureAssetManifest] Loaded " + props.size()
                    + " entries from " + file.getAbsolutePath());
        } catch (IOException e) {
            LOG.warning("[FeatureAssetManifest] Failed to load manifest: "
                    + e.getMessage() + " — using fallback for all features.");
        }
        loaded = true;
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Returns the asset path for (type, id), or null if not specified.
     * A null return means the frontend should use its procedural fallback.
     */
    public String getAssetHint(FeatureType type, int id) {
        if (!loaded) return null;
        return props.getProperty(manifestKey(type, id));
    }

    /** Convenience overload for the common case. */
    public String getAssetHint(com.phantasia.core.world.WorldFeature feature) {
        return getAssetHint(feature.getType(), feature.getId());
    }

    public boolean isLoaded() { return loaded; }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static String manifestKey(FeatureType type, int id) {
        return type.name() + "_" + id;
    }
}