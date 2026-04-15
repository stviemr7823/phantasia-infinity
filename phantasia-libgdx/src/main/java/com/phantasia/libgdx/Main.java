package com.phantasia.libgdx;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/**
 * LibGDX desktop entry point.
 *
 * <h3>Map path argument</h3>
 * When launched by the PhantasiaEditor's "Bake &amp; Tour" button,
 * {@code TourLauncher} passes the absolute path of the baked preview map
 * as {@code args[0]}:
 *
 * <pre>
 *   ./gradlew :phantasia-libgdx:run --args="/path/to/tour_preview.map"
 * </pre>
 *
 * If present, the path is forwarded to {@link PhantasiaGame}, which makes it
 * available to {@code LoadingScreen}.  {@code LoadingScreen} then loads the
 * map from disk and creates a fresh session at the map's start position,
 * bypassing any saved game — exactly matching the j2d {@code TourMain}
 * "editor launch" protocol.
 *
 * If {@code args[0]} is absent (normal standalone launch), the path is
 * {@code null} and {@code LoadingScreen} falls back to
 * {@code PendragonWorldMap.buildWorldMap()} as before.
 */
public class Main {
    public static void main(String[] args) {
        String mapPath = (args.length > 0) ? args[0] : null;

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Phantasia: Infinity");
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(60);
        config.useVsync(true);

        new Lwjgl3Application(new PhantasiaGame(mapPath), config);
    }
}