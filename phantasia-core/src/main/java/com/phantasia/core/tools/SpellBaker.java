// phantasia-core/src/main/java/com/phantasia/core/tools/SpellBaker.java
package com.phantasia.core.tools;

import com.phantasia.core.data.DataPaths;
import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.Spell;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Bakes spells.dat from spells.csv.
 *
 * Default paths (resolved via DataPaths, relative to the JVM working directory):
 *   Input  : assets/csv/spells.csv
 *   Output : assets/spells.dat
 *
 * Override at runtime via system properties:
 *   -Dphantasia.data.dir=<path>   redirects .dat output directory
 *   -Dphantasia.csv.dir=<path>    redirects .csv input directory
 *
 * Or pass explicit paths as program arguments:
 *   SpellBaker <input.csv> <output.dat>
 *
 * CSV format (pipe or comma delimited, header row optional):
 *   ID | Name | MP Cost | Target | Effect Type | Power | Level Req | Spell Type
 *
 * Records are written SPARSE — spell ID N lives at byte offset (N-1)*48,
 * so SpellFactory.getSpell(id) seeks directly without scanning.
 * Gaps between non-contiguous IDs are zero-filled.
 */
public class SpellBaker {

    public static final int RECORD_SIZE = DataLayout.RECORD_SIZE;

    public static void main(String[] args) {
        String csvPath = (args.length > 0) ? args[0] : DataPaths.SPELLS_CSV;
        String datPath = (args.length > 1) ? args[1] : DataPaths.SPELLS_DAT;

        System.out.println(">>> [SPELL BAKER] STARTING...");
        System.out.println("    JVM working dir : " + System.getProperty("user.dir"));
        System.out.println("    Reading CSV     : " + DataPaths.absolute(csvPath));
        System.out.println("    Writing DAT     : " + DataPaths.absolute(datPath));
        System.out.println();

        // Fail fast with a clear message if the CSV is missing
        File csv = new File(csvPath);
        if (!csv.exists()) {
            System.err.println("!!! [SPELL BAKER] CSV NOT FOUND: " + csv.getAbsolutePath());
            System.err.println("    Create the file at that location and re-run.");
            return;
        }

        try {
            new SpellBaker().bakeSpells(csvPath, datPath);
        } catch (IOException e) {
            System.err.println("!!! [SPELL BAKER] ERROR: " + e.getMessage());
            System.err.println("    DAT target was: " + DataPaths.absolute(datPath));
        }
    }

    // ------------------------------------------------------------------
    // Main bake entry point
    // ------------------------------------------------------------------

    public void bakeSpells(String csvPath, String datPath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(csvPath));

        TreeMap<Integer, byte[]> records = new TreeMap<>();
        int skipped = 0;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.toUpperCase().startsWith("ID")) continue;  // header row

            String   delim = line.contains("|") ? "\\|" : ",";
            String[] parts = line.split(delim, -1);

            // Expected columns:
            //   0: ID
            //   1: Name
            //   2: MP Cost
            //   3: Target      (Single / Group / Party / Ally)
            //   4: Effect Type (Damage / Heal / Sleep / Awaken / Buff / Debuff / Utility)
            //   5: Power
            //   6: Level Req   (optional)
            //   7: Spell Type  (Wizard / Priest, optional)
            if (parts.length < 6) {
                System.out.printf("  [SKIP] Too few columns (%d): %s%n", parts.length, line);
                skipped++;
                continue;
            }

            try {
                int id = Integer.parseInt(parts[0].trim());
                if (id < 1 || id > 255) {
                    System.out.printf("  [SKIP] ID out of range (%d): %s%n", id, line);
                    skipped++;
                    continue;
                }

                byte[]   block = new byte[RECORD_SIZE];
                DataCore core  = new DataCore(block);

                core.setName(parts[1].trim());
                core.setStat(DataLayout.SPELL_ID,        id);
                core.setStat(DataLayout.SPELL_MP_COST,   clamp(parseInt(parts[2]), 0, 255));
                core.setStat(DataLayout.SPELL_TARGET,    mapTarget(parts[3].trim()));
                core.setStat(DataLayout.SPELL_EFFECT_TYPE, mapEffectType(parts[4].trim()));
                core.setStat(DataLayout.SPELL_POWER,     clamp(parseInt(parts[5]), 0, 255));

                if (parts.length > 6)
                    core.setStat(DataLayout.SPELL_LEVEL_REQ, clamp(parseInt(parts[6]), 0, 255));
                if (parts.length > 7)
                    core.setStat(DataLayout.SPELL_TYPE, mapSpellType(parts[7].trim()));

                records.put(id, block);
                System.out.printf("  [OK]   ID %3d  %s%n", id, core.getName());

            } catch (NumberFormatException e) {
                System.out.printf("  [SKIP] Parse error on: %s  (%s)%n", line, e.getMessage());
                skipped++;
            }
        }

        System.out.println();

        if (records.isEmpty()) {
            System.out.println("!!! No valid records parsed — aborting. Check CSV format.");
            return;
        }

        // Ensure output directory exists before writing
        DataPaths.ensureParentDirs(datPath);

        int  maxId    = records.lastKey();
        long fileSize = (long) maxId * RECORD_SIZE;

        try (RandomAccessFile raf = new RandomAccessFile(datPath, "rw")) {
            raf.setLength(0);
            raf.setLength(fileSize);   // zero-fill entire file first

            for (Map.Entry<Integer, byte[]> entry : records.entrySet()) {
                long offset = (long)(entry.getKey() - 1) * RECORD_SIZE;
                raf.seek(offset);
                raf.write(entry.getValue());
            }
        }

        // Confirm the file actually landed on disk
        File written = new File(datPath);
        System.out.println(">>> [SPELL BAKER] DONE.");
        System.out.printf("    Spells written : %d  (max ID=%d)%n", records.size(), maxId);
        System.out.printf("    File size      : %d bytes%n", written.length());
        System.out.printf("    File location  : %s%n", written.getAbsolutePath());
        if (skipped > 0)
            System.out.printf("    Lines skipped  : %d%n", skipped);
    }

    // ------------------------------------------------------------------
    // Mappers
    // ------------------------------------------------------------------

    private int mapTarget(String s) {
        return switch (s.toLowerCase()) {
            case "single", "item", "monster" -> Spell.TargetType.SINGLE_ENEMY.ordinal();
            case "group"                     -> Spell.TargetType.ALL_ENEMIES.ordinal();
            case "party"                     -> Spell.TargetType.ALL_ALLIES.ordinal();
            case "ally"                      -> Spell.TargetType.SINGLE_ALLY.ordinal();
            default -> {
                System.out.printf("  [WARN] Unknown target '%s' — using SINGLE_ENEMY%n", s);
                yield Spell.TargetType.SINGLE_ENEMY.ordinal();
            }
        };
    }

    private int mapEffectType(String s) {
        return switch (s.toLowerCase()) {
            case "damage"  -> Spell.EffectType.DAMAGE.ordinal();
            case "heal"    -> Spell.EffectType.HEAL.ordinal();
            case "sleep"   -> Spell.EffectType.SLEEP.ordinal();
            case "awaken"  -> Spell.EffectType.AWAKEN.ordinal();
            case "buff"    -> Spell.EffectType.BUFF.ordinal();
            case "debuff"  -> Spell.EffectType.DEBUFF.ordinal();
            case "utility" -> Spell.EffectType.UTILITY.ordinal();
            default -> {
                System.out.printf("  [WARN] Unknown effect type '%s' — using UTILITY%n", s);
                yield Spell.EffectType.UTILITY.ordinal();
            }
        };
    }

    private int mapSpellType(String s) {
        return switch (s.toLowerCase()) {
            case "wizard" -> Spell.SpellType.WIZARD.ordinal();
            case "priest" -> Spell.SpellType.PRIEST.ordinal();
            default -> {
                System.out.printf("  [WARN] Unknown spell type '%s' — using WIZARD%n", s);
                yield Spell.SpellType.WIZARD.ordinal();
            }
        };
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static int parseInt(String s)             { return Integer.parseInt(s.trim()); }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}