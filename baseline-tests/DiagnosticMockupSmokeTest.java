package com.phantasia.baseline;

import com.phantasia.core.data.*;
import com.phantasia.core.logic.*;
import com.phantasia.core.world.*;
import com.phantasia.core.world.ServiceData.*;
import com.phantasia.j2d.diagnostic.*;
import com.phantasia.j2d.engine.*;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

public final class DiagnosticMockupSmokeTest {
    private static int checks;
    private static void check(boolean ok, String text) { checks++; if (!ok) throw new AssertionError(text); }
    private static void same(Object a, Object b, String text) { check(Objects.equals(a, b), text + ": " + a + " != " + b); }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("phantasia_diagnostic_test_");
        try {
            var original = DiagnosticContent.create();
            var content = DiagnosticContent.exportAndLoad(original, root.resolve("content"));
            same(5, content.maps().size(), "five connected fixture maps"); same(5, content.npcs().size(), "five reusable NPCs"); same(1, content.shops().size(), "fixture shop");
            content.validate();
            byte[] before = Files.readAllBytes(root.resolve("content/interiors.dat"));
            try { DiagnosticContent.exportAndLoad(original, root.resolve("content")); throw new AssertionError("existing export replaced"); }
            catch (FileAlreadyExistsException expected) { check(Arrays.equals(before, Files.readAllBytes(root.resolve("content/interiors.dat"))), "existing exports preserved"); }
            var quit = new AtomicInteger(); var app = new DiagnosticApplication(content, quit::incrementAndGet);
            same(GameState.WORLD_ROAM, app.manager().getActiveState(), "starts in test entry menu");
            key(app, KeyEvent.VK_ENTER); same(DiagnosticContent.SETTLEMENT, app.traversal().map().getId(), "entry menu starts settlement");
            var t = app.traversal();
            walk(t, app.session(), 20, 6); same(DiagnosticContent.DUNGEON, t.map().getId(), "settlement to dungeon");
            walk(t, app.session(), 14, 5); same(DiagnosticContent.DEEP, t.map().getId(), "first to second dungeon room");
            walk(t, app.session(), 1, 5); same(DiagnosticContent.DUNGEON, t.map().getId(), "second room return");
            walk(t, app.session(), 1, 5); same(DiagnosticContent.SETTLEMENT, t.map().getId(), "dungeon return to settlement");
            walk(t, app.session(), 10, 1); same(DiagnosticContent.COURTYARD, t.map().getId(), "settlement to castle");
            same(LocalMapTraversal.Kind.FEATURE, walk(t, app.session(), 8, 6).kind(), "castle gate starts locked"); check(t.position().y() > 6, "gate cannot be bypassed");
            check(t.visibleNpcAt(6, 4) == null, "guard hidden before permission");
            walk(t, app.session(), 8, 10); same(DiagnosticContent.SETTLEMENT, t.map().getId(), "closed castle has return exit");
            walk(t, app.session(), 7, 8); key(app, KeyEvent.VK_RIGHT);
            same(GameState.DIALOGUE, app.manager().getActiveState(), "guide opens real dialogue screen"); check(app.session().hasFlag(DiagnosticContent.PERMISSION), "actual dialogue grants diagnostic permission");
            key(app, KeyEvent.VK_ENTER);
            if (app.manager().getActiveState() == GameState.DIALOGUE) key(app, KeyEvent.VK_ENTER);
            same(GameState.LOCAL_MAP, app.manager().getActiveState(), "dialogue resumes traversal");
            walk(t, app.session(), 10, 1); walk(t, app.session(), 8, 6); check(app.session().hasFlag(DiagnosticContent.GATE_OPEN), "gate unlock completion recorded");
            check(t.visibleNpcAt(6, 4) != null, "guard visible after permission");
            walk(t, app.session(), 8, 1); same(DiagnosticContent.HALL, t.map().getId(), "unlocked gate leads to castle hall"); check(t.visibleNpcAt(6, 3) != null, "hall NPC visible");
            walk(t, app.session(), 6, 8); same(DiagnosticContent.COURTYARD, t.map().getId(), "hall return");
            walk(t, app.session(), 8, 10); same(DiagnosticContent.SETTLEMENT, t.map().getId(), "courtyard return after unlock");
            walk(t, app.session(), 5, 6); key(app, KeyEvent.VK_RIGHT); same(GameState.SHOP, app.manager().getActiveState(), "merchant opens real shop overlay"); key(app, KeyEvent.VK_ESCAPE);
            walk(t, app.session(), 6, 9); key(app, KeyEvent.VK_DOWN); same(GameState.GUILD, app.manager().getActiveState(), "trainer opens real guild overlay"); key(app, KeyEvent.VK_ESCAPE);
            // Every placeholder scene can render without a window or optional assets.
            for (var map : content.maps().getAll()) {
                app.enter(map.getId()); var image = new BufferedImage(GameCanvas.DESIGN_WIDTH, GameCanvas.DESIGN_HEIGHT, BufferedImage.TYPE_INT_RGB); var g = image.createGraphics();
                try { app.manager().render(g, image.getWidth(), image.getHeight()); } finally { g.dispose(); }
                check(image.getRGB(image.getWidth()/2, image.getHeight()/2) != 0xff000000, "render map " + map.getId());
            }
            app.enter(DiagnosticContent.SETTLEMENT); walk(app.traversal(), app.session(), 2, 6); key(app, KeyEvent.VK_LEFT); app.manager().update(.31f); app.manager().update(.31f);
            same(GameState.WORLD_ROAM, app.manager().getActiveState(), "world exit returns to test menu"); same(new WorldPosition(0, 0), app.session().getWorldPosition(), "test world anchor");
            key(app, KeyEvent.VK_ENTER); key(app, KeyEvent.VK_ESCAPE); same(GameState.PAUSE, app.manager().getActiveState(), "diagnostic pause menu");
            var oldSession = app.session(); key(app, KeyEvent.VK_R); check(oldSession != app.session(), "reset creates independent party/session"); check(!app.session().hasFlag(DiagnosticContent.PERMISSION), "reset clears diagnostic flags");
            same(GameState.WORLD_ROAM, app.manager().getActiveState(), "reset discards overlay stack"); same(400, app.session().getLedger().getPartyGold(), "reset restores test gold");
            key(app, KeyEvent.VK_Q); same(1, quit.get(), "quit callback");
            // Incomplete custom exports and invalid options fail explicitly.
            Files.delete(root.resolve("content/shops.dat"));
            try { DiagnosticContent.load(root.resolve("content")); throw new AssertionError("missing content accepted"); } catch (java.io.IOException expected) { check(expected.getMessage().contains("shops.dat"), "missing custom content is named"); }
            DiagnosticMain.main(new String[]{"--check"});
            DiagnosticMain.main(new String[]{"--render-dir", root.resolve("images").toString()});
            try (var files = Files.list(root.resolve("images"))) { same(6L, files.count(), "five map previews plus menu"); }
            var preview = ImageIO.read(root.resolve("images/map-1000.png").toFile()); same(1920, preview.getWidth(), "preview width"); same(1080, preview.getHeight(), "preview height");
            try { DiagnosticMain.main(new String[]{"--unknown"}); throw new AssertionError("unknown option accepted"); } catch (IllegalArgumentException expected) { check(true, "invalid CLI option rejected"); }
            System.out.println("DIAGNOSTIC_MOCKUP checks=" + checks + " PASS");
        } finally { try (var files = Files.walk(root)) { for (var p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); } }
    }
    private static void key(DiagnosticApplication app, int key) {
        var canvas = new Canvas(); var screen = app.manager().getActiveScreen();
        screen.onKeyPressed(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, 0, key, KeyEvent.CHAR_UNDEFINED));
        app.manager().update(.2f); app.manager().update(.2f);
        var active = app.manager().getActiveScreen(); if (active != null) active.onKeyReleased(new KeyEvent(canvas, KeyEvent.KEY_RELEASED, 0, 0, key, KeyEvent.CHAR_UNDEFINED));
    }
    /** Independent fixture path finder; the actual traversal executes each step. */
    private static LocalMapTraversal.Result walk(LocalMapTraversal t, GameSession session, int tx, int ty) {
        var map = t.map(); var start = t.position(); var goal = new WorldPosition(tx, ty); var queue = new ArrayDeque<WorldPosition>();
        var paths = new HashMap<WorldPosition, List<Direction>>(); queue.add(start); paths.put(start, List.of());
        while (!queue.isEmpty() && !paths.containsKey(goal)) {
            var p = queue.remove();
            for (Direction d : Direction.values()) {
                int dx = d == Direction.EAST ? 1 : d == Direction.WEST ? -1 : 0, dy = d == Direction.SOUTH ? 1 : d == Direction.NORTH ? -1 : 0;
                var n = new WorldPosition(p.x()+dx, p.y()+dy); if (!map.inBounds(n.x(), n.y()) || paths.containsKey(n)) continue;
                var feature = map.getFeatureAt(n.x(), n.y()); var tile = AuthoredContentValidator.tileSet(map.getTileSetId()).getEntry(map.getTile(n.x(), n.y()));
                boolean door = feature != null && feature.featureType() == PlacedFeatureType.LOCKED_DOOR;
                if (!n.equals(goal) && (t.visibleNpcAt(n.x(), n.y()) != null || feature != null && !door)) continue;
                if (!tile.passable() && !(door && (n.equals(goal) || session.hasFlag(DiagnosticContent.PERMISSION)))) continue;
                var route = new ArrayList<>(paths.get(p)); route.add(d); paths.put(n, route); queue.add(n);
            }
        }
        if (!paths.containsKey(goal)) throw new AssertionError("No fixture route to " + goal + " in " + map.getName());
        LocalMapTraversal.Result result = null;
        for (var d : paths.get(goal)) result = t.move(d);
        if (result == null) throw new AssertionError("Test route must move");
        return result;
    }
}
