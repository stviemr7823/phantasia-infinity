// phantasia-core/src/main/java/com/phantasia/core/tools/TourLauncher.java
package com.phantasia.core.tools;

import com.phantasia.core.data.DataPaths;
import com.phantasia.core.world.TileType;
import com.phantasia.core.world.WorldFeature;
import com.phantasia.core.world.WorldMapBaker;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Launches the Phantasia Touring Engine (j2d) as a child process from
 * within the PhantasiaEditor.
 *
 * <h3>Approach</h3>
 * Uses the Gradle wrapper ({@code gradlew} / {@code gradlew.bat}) rather than
 * trying to reconstruct the JVM classpath.  The editor lives in
 * {@code phantasia-core}; the touring engine lives in {@code phantasia-j2d}
 * — two separate modules.  Delegating to Gradle solves the classpath problem
 * cleanly: Gradle knows the full multi-module dependency graph and assembles
 * the correct classpath automatically, exactly as when you run:
 * <pre>
 *   ./gradlew :phantasia-j2d:run --args="&lt;mapPath&gt;"
 * </pre>
 *
 * <h3>Protocol</h3>
 * <ol>
 *   <li>Editor calls {@link #bakeAndLaunch} with the current in-memory map state.
 *   <li>{@code TourLauncher} bakes the map to {@code data/tour_preview.map}.
 *   <li>Finds the Gradle wrapper by walking up the directory tree.
 *       On Windows the wrapper is {@code gradlew.bat}; on all other platforms
 *       it is {@code gradlew}.
 *   <li>Spawns: {@code gradlew :phantasia-j2d:run --args="<mapPath>"}.
 *       The map path is double-quoted inside {@code --args} so that paths
 *       containing spaces are passed correctly to {@code TourMain.main()}.
 *   <li>Editor continues normally — child process runs independently.
 * </ol>
 *
 * <h3>Tour preview map</h3>
 * Written to {@code DataPaths.DAT_DIR + "/tour_preview.map"}.
 * Overwritten on every "Bake &amp; Tour" click.
 *
 * <h3>Module targets</h3>
 * The j2d touring engine is the default target — it is the editor's
 * companion app for fast, lightweight map testing.  The libGDX module
 * ({@code :phantasia-libgdx:run}) remains available for testing the
 * actual game renderer; use {@link #bakeAndLaunchLibGDX} for that path.
 */
public final class TourLauncher {

    private TourLauncher() {}

    private static final String PREVIEW_MAP_PATH =
            DataPaths.DAT_DIR + "/tour_preview.map";

    /** Gradle task for the j2d touring engine (editor companion). */
    private static final String J2D_TASK    = ":phantasia-j2d:run";

    /** Gradle task for the libGDX game renderer (full game testing). */
    private static final String LIBGDX_TASK = ":phantasia-libgdx:run";

    /** True when running on a Windows host. */
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    // -------------------------------------------------------------------------
    // Public API — called by WorldEditorPanel
    // -------------------------------------------------------------------------

    /**
     * Bakes the current map and launches the j2d Touring Engine.
     * This is the primary editor workflow — fast, lightweight preview.
     */
    public static void bakeAndLaunch(
            TileType[][]       terrain,
            WorldFeature[][]   features,
            int                mapW,
            int                mapH,
            int                startX,
            int                startY,
            java.awt.Component parentCmp) {

        bakeAndLaunchTarget(terrain, features, mapW, mapH,
                startX, startY, parentCmp, J2D_TASK, "Touring Engine (j2d)");
    }

    /**
     * Bakes the current map and launches the libGDX game renderer.
     * Use this to test the map in the actual game engine with GPU rendering.
     */
    public static void bakeAndLaunchLibGDX(
            TileType[][]       terrain,
            WorldFeature[][]   features,
            int                mapW,
            int                mapH,
            int                startX,
            int                startY,
            java.awt.Component parentCmp) {

        bakeAndLaunchTarget(terrain, features, mapW, mapH,
                startX, startY, parentCmp, LIBGDX_TASK, "Game (libGDX)");
    }

    // -------------------------------------------------------------------------
    // Shared launch implementation
    // -------------------------------------------------------------------------

    private static void bakeAndLaunchTarget(
            TileType[][]       terrain,
            WorldFeature[][]   features,
            int                mapW,
            int                mapH,
            int                startX,
            int                startY,
            java.awt.Component parentCmp,
            String             gradleTask,
            String             targetLabel) {

        // 1. Bake the current map state to tour_preview.map
        try {
            DataPaths.ensureParentDirs(PREVIEW_MAP_PATH);
            WorldMapBaker.bake(PREVIEW_MAP_PATH,
                    mapW, mapH, startX, startY, terrain, features);
            System.out.println("[TourLauncher] Baked preview map → "
                    + DataPaths.absolute(PREVIEW_MAP_PATH));
        } catch (IOException e) {
            showError(parentCmp, "Failed to bake preview map:\n" + e.getMessage());
            return;
        }

        // 2. Find the Gradle wrapper
        File gradlew = findGradleWrapper();
        if (gradlew == null) {
            showError(parentCmp,
                    "Could not find 'gradlew' (or 'gradlew.bat') in the project tree.\n"
                            + "Searched from: " + new File(".").getAbsolutePath()
                            + "\nMake sure you are running from within the Phantasia project.");
            return;
        }
        System.out.println("[TourLauncher] Found Gradle wrapper: "
                + gradlew.getAbsolutePath());

        // 3. Build the Gradle command
        String mapAbsPath = DataPaths.absolute(PREVIEW_MAP_PATH);
        String argsValue  = "--args=\"" + mapAbsPath + "\"";

        List<String> cmd = new ArrayList<>();
        if (IS_WINDOWS) {
            cmd.add("cmd.exe");
            cmd.add("/c");
        }
        cmd.add(gradlew.getAbsolutePath());
        cmd.add(gradleTask);
        cmd.add(argsValue);

        System.out.println("[TourLauncher] " + targetLabel
                + "  Command: " + String.join(" ", cmd));

        // 4. Spawn child process — fire and forget
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(gradlew.getParentFile());
            pb.inheritIO();
            pb.start();
            System.out.println("[TourLauncher] " + targetLabel + " launched.");
        } catch (IOException e) {
            showError(parentCmp,
                    "Failed to launch " + targetLabel + ":\n" + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Walks up the directory tree from the JVM working directory looking for
     * a Gradle wrapper script.
     *
     * <p>Search order per directory:
     * <ol>
     *   <li>{@code gradlew.bat} — Windows batch wrapper (tried first on Windows)
     *   <li>{@code gradlew}    — Unix shell wrapper
     * </ol>
     *
     * @return the wrapper {@link File} if found, or {@code null}
     */
    private static File findGradleWrapper() {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (dir != null) {
            if (IS_WINDOWS) {
                File bat = new File(dir, "gradlew.bat");
                if (bat.exists() && bat.isFile()) return bat;
            }
            File unix = new File(dir, "gradlew");
            if (unix.exists() && unix.isFile()) return unix;
            dir = dir.getParentFile();
        }
        return null;
    }

    private static void showError(java.awt.Component parent, String message) {
        System.err.println("[TourLauncher] ERROR: " + message);
        javax.swing.JOptionPane.showMessageDialog(parent,
                message, "Tour Launch Error",
                javax.swing.JOptionPane.ERROR_MESSAGE);
    }
}