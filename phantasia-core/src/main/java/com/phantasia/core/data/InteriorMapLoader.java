// phantasia-core/src/main/java/com/phantasia/core/data/InteriorMapLoader.java
package com.phantasia.core.data;

import com.phantasia.core.logic.Direction;
import com.phantasia.core.model.PlacedNpc;
import com.phantasia.core.world.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Deserializes {@code interiors.dat} into an {@link InteriorMapRegistry}.
 *
 * <p>If the file is absent, returns an empty registry — callers should fall
 * back to {@link InteriorMapRegistry#buildDefault()} in that case.</p>
 *
 * @see InteriorMapBaker for the binary format specification
 */
public final class InteriorMapLoader {

    private static final Logger LOG =
            Logger.getLogger(InteriorMapLoader.class.getName());

    private static final byte[] MAGIC   = {'I', 'N', 'T', 'R'};
    private static final short  VERSION = 1;

    private InteriorMapLoader() {}

    /**
     * Loads all interior maps from {@code path}.
     *
     * @return a populated registry, or an empty one if the file is absent
     * @throws IOException if the file exists but is corrupt or unreadable
     */
    public static InteriorMapRegistry load(String path) throws IOException {
        InteriorMapRegistry registry = new InteriorMapRegistry();
        File file = new File(path);

        if (!file.exists()) {
            LOG.info("[InteriorMapLoader] No interiors.dat at "
                    + file.getAbsolutePath() + " — empty registry.");
            return registry;
        }

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {

            // Validate magic
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IOException("Not an interiors.dat file — bad magic bytes.");
            }

            short version = in.readShort();
            if (version != VERSION) {
                throw new IOException(
                        "Unsupported interiors.dat version: " + version);
            }

            int count = in.readShort() & 0xFFFF;

            for (int i = 0; i < count; i++) {
                registry.register(readMap(in));
            }

            LOG.info("[InteriorMapLoader] Loaded " + count
                    + " interior maps from " + file.getAbsolutePath());
        }

        return registry;
    }

    // -------------------------------------------------------------------------
    // Per-map deserialization
    // -------------------------------------------------------------------------

    private static InteriorMap readMap(DataInputStream in) throws IOException {
        int    id        = in.readShort() & 0xFFFF;
        String name      = readString(in);
        int    typeOrd   = in.readByte() & 0xFF;
        String tileSetId = readString(in);
        int    width     = in.readShort() & 0xFFFF;
        int    height    = in.readShort() & 0xFFFF;

        InteriorMapType type = (typeOrd < InteriorMapType.values().length)
                ? InteriorMapType.values()[typeOrd]
                : InteriorMapType.TOWN;

        InteriorMap map = new InteriorMap(id, name, type, width, height, tileSetId);

        // Tile grid
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                map.setTile(x, y, in.readByte() & 0xFF);

        // Settings
        boolean fogOfWar          = in.readByte() != 0;
        boolean encountersEnabled = in.readByte() != 0;
        int     encounterInterval = in.readShort() & 0xFFFF;
        int     lightOrd          = in.readByte() & 0xFF;
        int     torchRadius       = in.readByte() & 0xFF;

        InteriorSettings s = map.getSettings();
        s.setFogOfWar(fogOfWar);
        s.setEncountersEnabled(encountersEnabled);
        s.setEncounterInterval(encounterInterval);
        InteriorSettings.LightingMode[] modes = InteriorSettings.LightingMode.values();
        if (lightOrd < modes.length) s.setAmbientLight(modes[lightOrd]);
        s.setTorchRadius(torchRadius);

        // Features
        int featureCount = in.readShort() & 0xFFFF;
        PlacedFeatureType[] featureTypes = PlacedFeatureType.values();
        for (int i = 0; i < featureCount; i++) {
            int x        = in.readShort() & 0xFFFF;
            int y        = in.readShort() & 0xFFFF;
            int typeOrd2 = in.readByte()  & 0xFF;
            int sref     = in.readShort() & 0xFFFF; // service reference (e.g. shopId)

            PlacedFeatureType ft = (typeOrd2 < featureTypes.length)
                    ? featureTypes[typeOrd2] : PlacedFeatureType.EXIT;

            // Reconstruct service data for SHOP_COUNTER
            Object serviceData = (ft == PlacedFeatureType.SHOP_COUNTER && sref > 0)
                    ? sref : null;

            map.addFeature(new PlacedFeature(x, y, ft, serviceData));
        }

        // NPCs
        int npcCount = in.readShort() & 0xFFFF;
        Direction[] dirs = Direction.values();
        for (int i = 0; i < npcCount; i++) {
            int npcId   = in.readShort() & 0xFFFF;
            int x       = in.readShort() & 0xFFFF;
            int y       = in.readShort() & 0xFFFF;
            int dirOrd  = in.readByte()  & 0xFF;

            Direction facing = (dirOrd < dirs.length)
                    ? dirs[dirOrd] : Direction.SOUTH;

            map.addNpc(new PlacedNpc(npcId, x, y, facing));
        }

        return map;
    }

    // -------------------------------------------------------------------------
    // I/O helpers
    // -------------------------------------------------------------------------

    private static String readString(DataInputStream in) throws IOException {
        int    len   = in.readShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
