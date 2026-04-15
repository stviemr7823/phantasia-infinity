// phantasia-j2d/src/main/java/com/phantasia/j2d/tour/TourMain.java
package com.phantasia.j2d.tour;

import com.phantasia.core.data.DataPaths;
import com.phantasia.core.data.EncounterFactory;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.SaveManager;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.world.PendragonWorldMap;
import com.phantasia.core.world.WorldMap;
import com.phantasia.core.world.WorldPosition;

import javax.swing.*;
import java.io.IOException;
import java.util.List;

/**
 * Entry point for the Phantasia Touring Engine.
 *
 * Run standalone:
 *   java -cp <classpath> com.phantasia.j2d.tour.TourMain
 *
 * Run with a specific map file (launched by WorldEditorPanel):
 *   java -cp <classpath> com.phantasia.j2d.tour.TourMain data/tour_preview.map
 *
 * Save file: data/tour/TOUR.DAT  (isolated from the JME GAME.DAT)
 */
public class TourMain {

    public static final String TOUR_SAVE_PATH =
            DataPaths.DAT_DIR + "/tour/TOUR.DAT";

    public static void main(String[] args) {

        String mapPath = (args.length > 0) ? args[0] : null;
        SwingUtilities.invokeLater(() -> launch(mapPath));
    }

    private static void launch(String mapPath) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}

        WorldMap    worldMap = loadWorldMap(mapPath);
        GameSession session  = loadOrCreateSession(worldMap, mapPath);

        TourFrame frame = new TourFrame(worldMap, session);
        frame.setVisible(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try   { SaveManager.save(session, TOUR_SAVE_PATH); }
            catch (IOException e) { System.err.println("[TourMain] Auto-save failed: " + e.getMessage()); }
        }));
    }

    public static WorldMap loadWorldMap(String mapPath) {
        if (mapPath != null) {
            try {
                return WorldMap.loadFromFile(mapPath);
            } catch (IOException e) {
                System.err.println("[TourMain] Could not load '" + mapPath
                        + "': " + e.getMessage() + " — using built-in map.");
            }
        }
        return PendragonWorldMap.buildWorldMap();
    }

    public static GameSession loadOrCreateSession(WorldMap worldMap, String mapPath) {
        if (mapPath != null) {
            System.out.println("[TourMain] Editor launch — ignoring save, using map start: "
                    + worldMap.getStartPosition());
            List<PlayerCharacter> party = EncounterFactory.generateParty();
            return SaveManager.newGame(party, worldMap.getStartPosition());
        }
        if (SaveManager.saveExists(TOUR_SAVE_PATH)) {
            try {
                return SaveManager.load(TOUR_SAVE_PATH);
            } catch (Exception e) {
                System.err.println("[TourMain] Save load failed — starting fresh. (" + e.getMessage() + ")");
            }
        }
        List<PlayerCharacter> party = EncounterFactory.generateParty();
        WorldPosition         start = worldMap.getStartPosition();
        return SaveManager.newGame(party, start);
    }
}