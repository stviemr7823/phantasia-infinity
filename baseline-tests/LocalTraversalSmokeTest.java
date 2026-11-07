package com.phantasia.baseline;

import com.phantasia.core.data.*;
import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;
import com.phantasia.core.model.item.Inventory;
import com.phantasia.core.world.*;
import com.phantasia.core.world.ServiceData.*;
import com.phantasia.j2d.engine.*;
import com.phantasia.j2d.screen.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public final class LocalTraversalSmokeTest {
    private static int checks;
    private static final QuestFlag A = QuestFlag.values()[0], B = QuestFlag.values()[1];
    private static final WorldPosition WORLD = new WorldPosition(12, 14);
    private static void check(boolean ok, String text) { checks++; if (!ok) throw new AssertionError(text); }
    private static void same(Object a, Object b, String text) { check(Objects.equals(a, b), text + ": " + a + " != " + b); }
    private static GameSession session() { return new GameSession(List.of(), PartyLedger.newGame(), WORLD, 0); }
    private static InteriorMap map(int id, boolean dungeon) {
        var m = new InteriorMap(id, "Diagnostic area " + id, dungeon ? InteriorMapType.DUNGEON : InteriorMapType.TOWN, 8, 6,
                dungeon ? "dungeon_standard" : "town_standard");
        m.addEntrance(new MapEntrance("entry", new WorldPosition(1, 1), dungeon ? Direction.NORTH : Direction.EAST));
        m.addEntrance(new MapEntrance("return", new WorldPosition(1, 2), Direction.SOUTH)); m.setDefaultEntranceId("entry");
        m.getSettings().setTorchRadius(1); m.getSettings().setEncountersEnabled(false); return m;
    }
    private static InteriorMapRegistry registry(InteriorMap... maps) { var r = new InteriorMapRegistry(); for (var m : maps) r.register(m); return r; }
    private static LocalMapTraversal visit(InteriorMapRegistry registry, GameSession session) { return new LocalMapTraversal(registry, session, 0, null); }
    public static void main(String[] args) throws Exception {
        travel(); failures(); npcsAndLocks(); screen();
        System.out.println("LOCAL_TRAVERSAL checks=" + checks + " PASS");
    }
    private static void travel() throws Exception {
        var town = map(0, false); var dungeon = map(1, true); var castle = map(2, false);
        town.addFeature(new PlacedFeature(2, 1, PlacedFeatureType.STAIRS_LINK, new MapLinkData(1, "entry")));
        dungeon.addFeature(new PlacedFeature(0, 1, PlacedFeatureType.STAIRS_LINK, new MapLinkData(0, "return")));
        dungeon.addFeature(new PlacedFeature(2, 2, PlacedFeatureType.STAIRS_LINK, new MapLinkData(2, "entry")));
        castle.addFeature(new PlacedFeature(0, 1, PlacedFeatureType.STAIRS_LINK, new MapLinkData(1, "return")));
        town.addFeature(new PlacedFeature(0, 2, PlacedFeatureType.EXIT, ExitData.toEntryPoint()));
        var reg = registry(town, dungeon, castle); var session = session(); var t = visit(reg, session);
        same(Direction.EAST, t.facing(), "default arrival facing"); same(WORLD, session.getWorldPosition(), "world position retained on entry");
        check(t.isExplored(7, 5), "peaceful map fully visible");
        same(LocalMapTraversal.Kind.CHANGED_MAP, t.move(Direction.EAST).kind(), "town to authored dungeon");
        same(1, t.map().getId(), "destination map"); same(new WorldPosition(1, 1), t.position(), "named arrival position"); same(Direction.NORTH, t.facing(), "arrival facing applied");
        same(WORLD, session.getWorldPosition(), "local coordinates do not overwrite world coordinates");
        check(t.isVisible(2, 1) && !t.isVisible(3, 1), "torch radius");
        t.move(Direction.EAST); check(t.isExplored(0, 1) && !t.isVisible(0, 1), "explored memory survives leaving visibility");
        t.move(Direction.SOUTH); same(2, t.map().getId(), "dungeon to castle via same path");
        t.move(Direction.WEST); same(1, t.map().getId(), "castle return link"); same(new WorldPosition(1, 2), t.position(), "return entrance");
        check(t.isExplored(3, 1), "exploration retained after linked-map return");
        t.move(Direction.NORTH); t.move(Direction.WEST); same(0, t.map().getId(), "named link targets exact map 0");
        same(Direction.SOUTH, t.facing(), "return-facing applied");
        same(LocalMapTraversal.Kind.EXIT, t.move(Direction.WEST).kind(), "exit to world"); same(WORLD, session.getWorldPosition(), "original world anchor restored");
        check(t.hasExited(), "visit ended");
        try { t.move(Direction.EAST); throw new AssertionError("ended visit accepted movement"); } catch (IllegalStateException expected) { check(true, "ended visit rejects movement"); }
        check(town.getExploredBits().isEmpty() && dungeon.getExploredBits().isEmpty(), "authored maps retain no exploration state");
        same(new WorldPosition(1, 1), town.getEntrance("entry").position(), "authored entrance unchanged");
        // Export and load the same diagnostic maps before walking them again.
        Path file = Files.createTempFile("local_traversal_", ".dat");
        try { InteriorMapBaker.bake(reg, file.toString()); var loaded = visit(InteriorMapLoader.load(file.toString()), session());
            loaded.move(Direction.EAST); same(1, loaded.map().getId(), "travel over exported content"); }
        finally { Files.delete(file); }
        var coord = map(0, false); coord.addFeature(new PlacedFeature(2, 1, PlacedFeatureType.STAIRS_LINK, new StairsData(0, 0, new WorldPosition(4, 4))));
        var legacy = visit(registry(coord), session()); legacy.move(Direction.EAST); same(new WorldPosition(4, 4), legacy.position(), "legacy same-map coordinates supported"); same(Direction.EAST, legacy.facing(), "coordinate links retain movement facing");
        var north = visit(registry(map(0, false)), session()); north.move(Direction.NORTH); same(new WorldPosition(1, 0), north.position(), "local north is Y-down minus one");
        same(LocalMapTraversal.Kind.BLOCKED, north.move(Direction.NORTH).kind(), "map boundary blocked");
    }
    private static void failures() {
        var source = map(0, false); var target = map(1, true); var s = session();
        var link = new PlacedFeature(2, 1, PlacedFeatureType.STAIRS_LINK, new MapLinkData(1, "entry"), A, B, null); source.addFeature(link);
        var reg = registry(source, target); var t = visit(reg, s);
        same(LocalMapTraversal.Kind.BLOCKED, t.move(Direction.EAST).kind(), "gated passage blocks movement"); same(new WorldPosition(1, 1), t.position(), "gated passage keeps source position");
        s.setFlag(A); target.addNpc(new PlacedNpc(10, 1, 1, Direction.SOUTH));
        same(LocalMapTraversal.Kind.BLOCKED, t.move(Direction.EAST).kind(), "blocked destination rejected"); same(0, t.map().getId(), "failed link keeps source map"); check(!s.hasFlag(B), "failed link not consumed");
        target.removeNpc(target.getPlacedNpcs().get(0)); t.move(Direction.EAST); check(s.hasFlag(B), "successful link consumed after arrival");
        var broken = map(0, false); broken.addFeature(new PlacedFeature(2, 1, PlacedFeatureType.STAIRS_LINK, new MapLinkData(99, "entry"), null, B, null));
        var bad = visit(registry(broken), session()); var result = bad.move(Direction.EAST);
        same(LocalMapTraversal.Kind.BLOCKED, result.kind(), "missing destination blocked"); check(result.message().contains("99"), "broken link reports destination"); same(new WorldPosition(1, 1), bad.position(), "broken link does not move");
        var explicit = map(0, false); explicit.addFeature(new PlacedFeature(2, 1, PlacedFeatureType.EXIT, new ExitData(new WorldPosition(3, 4))));
        var explicitSession = session(); visit(registry(explicit), explicitSession).move(Direction.EAST); same(new WorldPosition(3, 4), explicitSession.getWorldPosition(), "explicit world return");
        var invalidExit = map(0, false); invalidExit.addFeature(new PlacedFeature(2, 1, PlacedFeatureType.EXIT, new ExitData(new WorldPosition(100, 100)), null, B, null));
        var invalidSession = session(); var safe = new LocalMapTraversal(registry(invalidExit), invalidSession, 0, null, p -> p.x() < 20 && p.y() < 20);
        same(LocalMapTraversal.Kind.BLOCKED, safe.move(Direction.EAST).kind(), "out-of-world exit blocked"); same(WORLD, invalidSession.getWorldPosition(), "invalid exit preserves world anchor"); check(!safe.hasExited() && !invalidSession.hasFlag(B), "invalid exit neither finishes nor consumes");
    }
    private static void npcsAndLocks() {
        var m = map(0, false); var s = session();
        var hidden = new PlacedNpc(1, 2, 1, Direction.SOUTH, A, null); var visible = new PlacedNpc(2, 2, 1, Direction.NORTH);
        m.addNpc(hidden); m.addNpc(visible); var t = visit(registry(m), s);
        same(visible, t.move(Direction.EAST).npc(), "hidden placement does not mask visible NPC"); same(new WorldPosition(1, 1), t.position(), "NPC on passable terrain blocks walking through");
        m.removeNpc(visible); same(LocalMapTraversal.Kind.MOVED, t.move(Direction.EAST).kind(), "hidden NPC leaves tile passable");
        var counter = map(0, false); counter.setTile(2, 1, 3); counter.addNpc(new PlacedNpc(3, 3, 1, Direction.SOUTH));
        same(LocalMapTraversal.Kind.NPC, visit(registry(counter), session()).move(Direction.EAST).kind(), "counter interaction searches behind counter");
        counter.setTile(3, 1, 2); same(LocalMapTraversal.Kind.BLOCKED, visit(registry(counter), session()).move(Direction.EAST).kind(), "counter search stops at wall");
        counter.setTile(2, 1, 2); counter.setTile(3, 1, 0); same(LocalMapTraversal.Kind.BLOCKED, visit(registry(counter), session()).move(Direction.EAST).kind(), "ordinary wall cannot reach NPC behind it");
        var locked = map(0, false); locked.setTile(2, 1, 2);
        var door = new PlacedFeature(2, 1, PlacedFeatureType.LOCKED_DOOR, LockData.byFlag(A, "Closed"), null, B, null); locked.addFeature(door);
        var flags = session(); var gate = visit(registry(locked), flags);
        same(LocalMapTraversal.Kind.FEATURE, gate.move(Direction.EAST).kind(), "locked door interaction"); same(new WorldPosition(1, 1), gate.position(), "locked door blocks even if terrain changes");
        flags.setFlag(A); same(LocalMapTraversal.Kind.MOVED, gate.move(Direction.EAST).kind(), "flag unlocks door"); check(flags.hasFlag(B), "unlock recorded");
        flags.clearFlag(A); gate.move(Direction.WEST); same(LocalMapTraversal.Kind.MOVED, gate.move(Direction.EAST).kind(), "recorded unlock remains open"); same(2, locked.getTile(2, 1), "unlock does not rewrite authored terrain");
        var keyMap = map(0, false); keyMap.addFeature(new PlacedFeature(2, 1, PlacedFeatureType.LOCKED_DOOR, LockData.byItem(170, "Need key")));
        var player = PlayerCharacter.fromLegacyCreation("Tester", Job.WIZARD, 20, 10, 10, 10, 10, 10, 1); new Inventory(player).add(170);
        var keyed = new GameSession(List.of(player), PartyLedger.newGame(), WORLD, 0);
        same(LocalMapTraversal.Kind.MOVED, visit(registry(keyMap), keyed).move(Direction.EAST).kind(), "party inventory key unlocks door"); check(new Inventory(player).contains(170), "key not consumed without an authored consumption rule");
        var signMap = map(0, false); var sign = new PlacedFeature(2, 1, PlacedFeatureType.SIGN, "Notice", null, B, null); signMap.addFeature(sign);
        var reading = session(); var reader = visit(registry(signMap), reading);
        same(LocalMapTraversal.Kind.FEATURE, reader.move(Direction.EAST).kind(), "placed sign triggers independently of tile metadata"); check(!reading.hasFlag(B), "interaction not consumed before effect"); reader.completeInteraction(sign); check(reading.hasFlag(B), "completed effect consumes flag");
        try { reader.completeInteraction(sign); throw new AssertionError("consumed interaction repeated"); } catch (IllegalArgumentException expected) { check(true, "consumption guarded"); }
    }
    private static final class Stub implements Screen {
        Object entry;
        public void onEnter(Object data) { entry = data; } public void onExit() {} public void update(float dt) {}
        public void render(Graphics2D g) {} public void onKeyPressed(KeyEvent e) {} public void onKeyReleased(KeyEvent e) {} public void onMouseClicked(MouseEvent e) {}
    }
    private static void tap(ScreenManager manager, int key) {
        var canvas = new Canvas(); var screen = manager.getActiveScreen();
        screen.onKeyPressed(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, 0, key, KeyEvent.CHAR_UNDEFINED));
        manager.update(.2f); manager.update(.2f);
        manager.getActiveScreen().onKeyReleased(new KeyEvent(canvas, KeyEvent.KEY_RELEASED, 0, 0, key, KeyEvent.CHAR_UNDEFINED));
    }
    private static void screen() {
        var a = map(0, false); var b = map(1, true);
        a.addFeature(new PlacedFeature(2, 1, PlacedFeatureType.STAIRS_LINK, new MapLinkData(1, "entry")));
        b.addFeature(new PlacedFeature(0, 1, PlacedFeatureType.STAIRS_LINK, new MapLinkData(0, "return")));
        a.addFeature(new PlacedFeature(0, 2, PlacedFeatureType.EXIT));
        var s = session(); var travel = visit(registry(a, b), s); var manager = new ScreenManager();
        var screen = new LocalMapScreen(manager, s, new NpcRegistry(), new ShopRegistry()); var world = new Stub(); var overlay = new Stub();
        manager.register(GameState.LOCAL_MAP, screen); manager.register(GameState.WORLD_ROAM, world); manager.register(GameState.PAUSE, overlay);
        manager.switchTo(GameState.LOCAL_MAP, new LocalMapScreen.Visit(travel, GameState.WORLD_ROAM));
        tap(manager, KeyEvent.VK_RIGHT); same(1, travel.map().getId(), "screen follows authored link"); same(Direction.NORTH, travel.facing(), "screen uses arrival facing");
        manager.update(.3f); manager.update(.3f); same(new WorldPosition(1, 1), travel.position(), "held input cleared at arrival");
        var image = new BufferedImage(GameCanvas.DESIGN_WIDTH, GameCanvas.DESIGN_HEIGHT, BufferedImage.TYPE_INT_RGB); var g = image.createGraphics();
        try { manager.render(g, image.getWidth(), image.getHeight()); check(image.getRGB(image.getWidth()/2, image.getHeight()/2) != Color.BLACK.getRGB(), "shared screen renders placeholder scene headlessly"); }
        finally { g.dispose(); }
        tap(manager, KeyEvent.VK_ESCAPE); same(GameState.PAUSE, manager.getActiveState(), "pause overlay"); manager.pop(); same(GameState.LOCAL_MAP, manager.getActiveState(), "overlay returns to same traversal"); same(1, travel.map().getId(), "overlay does not reinitialize map");
        tap(manager, KeyEvent.VK_LEFT); same(0, travel.map().getId(), "screen return link");
        tap(manager, KeyEvent.VK_LEFT); manager.update(.31f); manager.update(.31f); same(GameState.WORLD_ROAM, manager.getActiveState(), "screen exit restores world state"); same(WORLD, s.getWorldPosition(), "screen exit world coordinates");
        // An NPC on floor terrain opens dialogue rather than being walked through.
        var nMap = map(0, false); nMap.addNpc(new PlacedNpc(7, 2, 1, Direction.WEST));
        var registry = new NpcRegistry(); registry.register(new NpcDefinition(7, "Diagnostic NPC", NpcRole.INFORMANT, null));
        var npcTraversal = visit(registry(nMap), s); manager.register(GameState.LOCAL_MAP, new LocalMapScreen(manager, s, registry, new ShopRegistry())); manager.register(GameState.DIALOGUE, overlay);
        manager.switchTo(GameState.LOCAL_MAP, new LocalMapScreen.Visit(npcTraversal, GameState.WORLD_ROAM)); tap(manager, KeyEvent.VK_RIGHT);
        same(GameState.DIALOGUE, manager.getActiveState(), "floor NPC opens dialogue overlay"); same(7, ((DialogueScreen.DialogueData) overlay.entry).npc().getId(), "correct reusable definition dispatched");
        manager.pop(); manager.update(.3f); manager.update(.3f); same(new WorldPosition(1, 1), npcTraversal.position(), "NPC overlay resume does not repeat held movement");
    }
}
