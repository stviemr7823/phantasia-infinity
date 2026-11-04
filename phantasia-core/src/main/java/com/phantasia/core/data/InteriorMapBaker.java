// phantasia-core/src/main/java/com/phantasia/core/data/InteriorMapBaker.java
package com.phantasia.core.data;

import com.phantasia.core.logic.Direction;
import com.phantasia.core.model.PlacedNpc;
import com.phantasia.core.world.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Serializes {@link InteriorMapRegistry} contents to {@code interiors.dat}.
 *
 * <h3>Binary format</h3>
 * <pre>
 *   Header:
 *     4 bytes  magic "INTR"
 *     2 bytes  version = 1
 *     2 bytes  map count
 *
 *   Per map:
 *     2 bytes  map ID
 *     2 bytes  name length (N)
 *     N bytes  name (UTF-8)
 *     1 byte   InteriorMapType ordinal
 *     2 bytes  tileSetId length (M)
 *     M bytes  tileSetId (UTF-8)
 *     2 bytes  width
 *     2 bytes  height
 *     W×H bytes  tile indices (1 byte per cell, row-major)
 *
 *     Settings (5 bytes):
 *       1 byte   fogOfWar (0/1)
 *       1 byte   encountersEnabled (0/1)
 *       2 bytes  encounterInterval
 *       1 byte   ambientLight ordinal
 *       1 byte   torchRadius
 *
 *     Features:
 *       2 bytes  feature count
 *       Per feature:
 *         2 bytes  x
 *         2 bytes  y
 *         1 byte   PlacedFeatureType ordinal
 *         2 bytes  serviceRef (shopId for SHOP_COUNTER, mapId for STAIRS_LINK, else 0)
 *
 *     NPCs:
 *       2 bytes  NPC count
 *       Per NPC:
 *         2 bytes  npcId
 *         2 bytes  x
 *         2 bytes  y
 *         1 byte   Direction ordinal
 * </pre>
 *
 * <p>Quest flag gating on features/NPCs is deliberately omitted from v1 —
 * it will be added in v2 once the QuestFlag ordinal table is stable.</p>
 */
public final class InteriorMapBaker {

    private static final byte[] MAGIC   = {'I', 'N', 'T', 'R'};
    private static final short  VERSION = 1;

    private InteriorMapBaker() {}

    /**
     * Bakes all maps in the registry to {@code outPath}.
     */
    public static void bake(InteriorMapRegistry registry, String outPath)
            throws IOException {

        File file = DataPaths.ensureParentDirs(outPath);
        Collection<InteriorMap> maps = registry.getAll();

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {

            out.write(MAGIC);
            out.writeShort(VERSION);
            out.writeShort(maps.size());

            for (InteriorMap map : maps) {
                writeMap(out, map);
            }
        }

        System.out.println("[InteriorMapBaker] Baked " + maps.size()
                + " interior maps → " + outPath);
    }

    private static void writeMap(DataOutputStream out, InteriorMap map)
            throws IOException {

        out.writeShort(map.getId());
        writeString(out, map.getName());
        out.writeByte(map.getMapType().ordinal());
        writeString(out, map.getTileSetId());
        out.writeShort(map.getWidth());
        out.writeShort(map.getHeight());

        // Tile grid (1 byte per tile, clamped to 0-255)
        for (int y = 0; y < map.getHeight(); y++)
            for (int x = 0; x < map.getWidth(); x++)
                out.writeByte(Math.max(0, Math.min(255, map.getTile(x, y))));

        // Settings
        InteriorSettings s = map.getSettings();
        out.writeByte(s.isFogOfWar()          ? 1 : 0);
        out.writeByte(s.isEncountersEnabled()  ? 1 : 0);
        out.writeShort(s.getEncounterInterval());
        out.writeByte(s.getAmbientLight().ordinal());
        out.writeByte(s.getTorchRadius());

        // Features
        var features = map.getFeatures();
        out.writeShort(features.size());
        for (PlacedFeature f : features) {
            out.writeShort(f.x());
            out.writeShort(f.y());
            out.writeByte(f.featureType().ordinal());
            // serviceRef: shop ID for SHOP_COUNTER, else 0
            int sref = (f.serviceData() instanceof Integer i) ? i : 0;
            out.writeShort(sref);
        }

        // NPCs
        var npcs = map.getPlacedNpcs();
        out.writeShort(npcs.size());
        for (PlacedNpc n : npcs) {
            out.writeShort(n.npcId());
            out.writeShort(n.x());
            out.writeShort(n.y());
            out.writeByte(n.facing().ordinal());
        }
    }

    private static void writeString(DataOutputStream out, String s)
            throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }
}
