// phantasia-core/src/main/java/com/phantasia/core/data/SpellFactory.java
package com.phantasia.core.data;

import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.Spell;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads spells from the sparse binary spells.dat baked by SpellBaker.
 *
 * SPARSE LAYOUT: Spell ID N lives at byte offset (N - 1) * 48.
 * Zero-filled gap slots (between non-contiguous IDs) are skipped.
 * getSpell(id) is O(1) — no scanning.
 *
 * Default path: DataPaths.SPELLS_DAT (phantasia-core/data/spells.dat)
 * Override:     -Dphantasia.data.dir=<path>
 */
public class SpellFactory {

    private final Map<Integer, Spell> spellBook = new HashMap<>();

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /** Load from the default path defined in DataPaths. */
    public void loadSpells() throws IOException {
        loadSpells(DataPaths.SPELLS_DAT);
    }

    /** Load from an explicit path — used by tools and tests. */
    public void loadSpells(String datPath) throws IOException {
        System.out.println(">>> [SPELL FACTORY] Loading: "
                + DataPaths.absolute(datPath));

        byte[] buffer = new byte[DataLayout.RECORD_SIZE];

        try (RandomAccessFile raf = new RandomAccessFile(datPath, "r")) {
            long totalRecords = raf.length() / DataLayout.RECORD_SIZE;

            for (int id = 1; id <= totalRecords; id++) {
                raf.seek((long)(id - 1) * DataLayout.RECORD_SIZE);
                raf.readFully(buffer);

                if (isEmptyRecord(buffer)) continue;

                spellBook.put(id, new Spell(new DataCore(buffer.clone())));
            }
        }

        System.out.printf(">>> [SPELL FACTORY] %d spells loaded.%n",
                spellBook.size());
    }

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    /** Returns the Spell for the given ID, or null if not in the spellbook. */
    public Spell getSpell(int id) {
        return spellBook.get(id);
    }

    public boolean hasSpell(int id)            { return spellBook.containsKey(id); }
    public int     size()                      { return spellBook.size(); }
    public Map<Integer, Spell> allSpells()     { return Collections.unmodifiableMap(spellBook); }

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