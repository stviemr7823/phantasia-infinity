// phantasia-core/src/main/java/com/phantasia/core/data/MonsterFactory.java
package com.phantasia.core.data;

import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.Monster;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads monsters from a binary bestiary and spawns independent clones on demand.
 *
 * Sequential layout (current MonsterPacker output):
 *   Records are packed contiguously — monster 1 at offset 0,
 *   monster 2 at offset 48, etc. Looked up by name.
 *
 * Sparse layout (future):
 *   If encounter tables need O(1) lookup by ID, adopt the sparse
 *   layout used by SpellFactory: monster ID N at offset (N-1) * 48.
 *   Add loadSparse(String path) here when that becomes necessary.
 */
public class MonsterFactory {

    /** Master templates keyed by trimmed ASCII name. */
    private final Map<String, byte[]> templates = new HashMap<>();

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    public void registerTemplate(String name, byte[] data) {
        templates.put(name.trim(), data);
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    /**
     * Returns a fresh Monster clone for the given name.
     * The master template is never mutated — every spawn gets its own
     * independent byte array so HP changes don't bleed between instances.
     *
     * @throws IllegalArgumentException if the name is not in the bestiary
     */
    public Monster spawn(String monsterName) {
        byte[] blueprint = templates.get(monsterName.trim());
        if (blueprint == null)
            throw new IllegalArgumentException(
                    "The beast '" + monsterName + "' is not in the bestiary!");
        return new Monster(new DataCore(Arrays.copyOf(blueprint, blueprint.length)));
    }

    public boolean hasMonster(String name)          { return templates.containsKey(name.trim()); }
    public int     size()                           { return templates.size(); }
    public Map<String, byte[]> allTemplates()       { return Collections.unmodifiableMap(templates); }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * Loads the entire binary bestiary into the template map.
     * Skips zero-filled or whitespace-only records (padding at end of file).
     */
    public void loadFromFile(String binPath) throws IOException {
        byte[] buffer = new byte[DataLayout.RECORD_SIZE];

        try (RandomAccessFile raf = new RandomAccessFile(binPath, "r")) {
            long totalRecords = raf.length() / DataLayout.RECORD_SIZE;

            for (int i = 0; i < totalRecords; i++) {
                raf.readFully(buffer);

                if (isEmptyRecord(buffer)) continue;

                String name = new String(buffer, DataLayout.NAME, DataLayout.NAME_LEN,
                        StandardCharsets.US_ASCII).trim();

                registerTemplate(name, Arrays.copyOf(buffer, DataLayout.RECORD_SIZE));
            }
        }

        System.out.printf(">>> [MONSTER FACTORY] %d creature types loaded.%n",
                templates.size());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** A record is empty if every byte is 0x00 or ASCII space (0x20). */
    private static boolean isEmptyRecord(byte[] record) {
        for (byte b : record)
            if (b != 0x00 && b != 0x20) return false;
        return true;
    }
}