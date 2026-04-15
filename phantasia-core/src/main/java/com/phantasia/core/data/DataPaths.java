// phantasia-core/src/main/java/com/phantasia/core/data/DataPaths.java
package com.phantasia.core.data;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Single source of truth for all data file paths in the Phantasia engine.
 *
 * DIRECTORY CONVENTION:
 *   phantasia-core/data/        — compiled .dat files (runtime + dumpers)
 *   phantasia-core/data/csv/    — source .csv files (bakers only, never runtime)
 *
 * All paths resolve relative to the JVM working directory, which for all
 * Gradle tasks and IntelliJ run configs in this module is phantasia-core/.
 *
 * OVERRIDE: Set the system property -Dphantasia.data.dir=<path> to redirect
 * all dat file I/O to a different directory (useful for integration tests or
 * running from a different working directory).
 *
 * Usage:
 *   // In a baker:
 *   new SpellBaker().bakeSpells(DataPaths.SPELLS_CSV, DataPaths.SPELLS_DAT);
 *
 *   // In a runtime loader:
 *   spellFactory.loadSpells(DataPaths.SPELLS_DAT);
 *
 *   // In a dumper:
 *   new SpellDataDumper().dump(DataPaths.SPELLS_DAT);
 */
public final class DataPaths {

    private DataPaths() {}

    // -------------------------------------------------------------------------
    // Root directories
    // -------------------------------------------------------------------------

    /**
     * Root directory for all .dat files.
     * Respects -Dphantasia.data.dir override; defaults to ./data
     */
    public static final String DAT_DIR =
            System.getProperty("phantasia.data.dir", "data");

    /**
     * Root directory for source .csv files used by bakers.
     * Never read at runtime — bakers only.
     */
    public static final String CSV_DIR =
            System.getProperty("phantasia.csv.dir", "data/csv");

    // -------------------------------------------------------------------------
    // .dat paths  (runtime loaders, dumpers, editors)
    // -------------------------------------------------------------------------

    public static final String SPELLS_DAT   = DAT_DIR + "/spells.dat";
    public static final String MONSTERS_DAT = DAT_DIR + "/monsters.dat";
    public static final String ITEMS_DAT    = DAT_DIR + "/items.dat";
    public static final String WORLD_DAT    = DAT_DIR + "/world.dat";

    // -------------------------------------------------------------------------
    // .csv paths  (bakers only)
    // -------------------------------------------------------------------------

    public static final String SPELLS_CSV   = CSV_DIR + "/spells.csv";
    public static final String MONSTERS_CSV = CSV_DIR + "/monsters.csv";
    public static final String ITEMS_CSV    = CSV_DIR + "/items.csv";
    public static final String NPC_SCHEMA = "data/game/npc_schema.json";

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /**
     * Returns a File for the given path, creating parent directories if needed.
     * Call this in bakers before writing to ensure the directory exists.
     */
    public static File ensureParentDirs(String path) {
        File f = new File(path);
        f.getParentFile().mkdirs();
        return f;
    }

    /**
     * Returns the absolute path string — useful for diagnostic output so
     * the user can see exactly where files are being read from or written to.
     */
    public static String absolute(String relativePath) {
        return Paths.get(relativePath).toAbsolutePath().toString();
    }
}