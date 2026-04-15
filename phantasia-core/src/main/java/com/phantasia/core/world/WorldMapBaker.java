// phantasia-core/src/main/java/com/phantasia/core/world/WorldMapBaker.java
package com.phantasia.core.world;
import com.phantasia.core.data.DataPaths;
import com.phantasia.core.world.FeatureRecord;
import com.phantasia.core.world.FeatureRegistry;

import java.io.*;


/**
 * Bakes a hand-authored tile grid into the binary .map format
 * that WorldMap.loadFromFile() reads.
 */
public class WorldMapBaker {

    public static void bake(String outPath, int width, int height,
                            int startX, int startY,
                            TileType[][]     terrain,
                            WorldFeature[][] features) throws IOException {

        try (DataOutputStream out = new DataOutputStream(
                new FileOutputStream(outPath))) {

            // Header
            out.writeShort(width);
            out.writeShort(height);
            out.writeShort(startX);
            out.writeShort(startY);

            // Tile records
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    out.writeByte(terrain[x][y].ordinal());

                    WorldFeature f = (features != null &&
                            features[x][y] != null)
                            ? features[x][y]
                            : WorldFeature.NONE;

                    int featureByte = (f.getType().ordinal() << 6) |
                            (f.getId() & 0x3F);
                    out.writeByte(featureByte);
                }
            }

        }
        FeatureRegistry registry = buildFeatureRegistry();
        String featuresPath = DataPaths.DAT_DIR + "/features.dat";
        try {
            registry.save(featuresPath);
            System.out.println("[WorldMapBaker] features.dat written: "
                    + registry.size() + " records.");
        } catch (IOException e) {
            System.err.println("[WorldMapBaker] Failed to write features.dat: "
                    + e.getMessage());
        }

        System.out.println("Baked " + width + "x" + height +
                " map -> " + outPath);
    }

    // Convenience overload — no features
    public static void bake(String outPath, int width, int height,
                            int startX, int startY,
                            TileType[][] terrain) throws IOException {
        bake(outPath, width, height, startX, startY, terrain, null);
    }

    /** Builds the FeatureRegistry with full metadata — written to features.dat. */
    static FeatureRegistry buildFeatureRegistry() {
        FeatureRegistry registry = new FeatureRegistry();

        // Towns
        registry.add(FeatureRecord.town(0,  6, 10,
                "Pendragon",
                "The ancient capital, seat of the High Council."));
        registry.add(FeatureRecord.town(1, 12,  4,
                "Scandor",
                "A northern trading post, cold and pragmatic."));
        registry.add(FeatureRecord.town(2,  9, 25,
                "Loftwood",
                "A peaceful forest settlement, known for its healers."));
        registry.add(FeatureRecord.town(3, 12, 16,
                "Ironhaven",
                "A fortified city built on iron mines."));

        // Dungeons
        registry.add(FeatureRecord.dungeon(1,  9, 27,
                "Frostpeak Cavern",
                "A frozen labyrinth carved by ancient hands."));
        registry.add(FeatureRecord.dungeon(2,  5,  6,
                "Sunken Vault",
                "A submerged treasury, half-drowned and treacherous."));

        // NPCs
        registry.add(FeatureRecord.npc(101, 7, 10, "Filmon"));

        return registry;
    }
}