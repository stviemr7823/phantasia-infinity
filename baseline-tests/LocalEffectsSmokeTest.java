package com.phantasia.baseline;

import com.phantasia.core.data.*;
import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;
import com.phantasia.core.model.item.*;
import com.phantasia.core.world.*;
import com.phantasia.core.world.ServiceData.*;
import com.phantasia.j2d.diagnostic.*;
import com.phantasia.j2d.engine.*;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public final class LocalEffectsSmokeTest {
    private static int checks;
    private static final QuestFlag DONE = QuestFlag.MISC_FLAG_60, GATE = QuestFlag.MISC_FLAG_58;
    private static void check(boolean ok, String text) { checks++; if (!ok) throw new AssertionError(text); }
    private static void same(Object a, Object b, String text) { check(Objects.equals(a, b), text + ": " + a + " != " + b); }
    private static PlayerCharacter player(String name, int hp, int dex) { return PlayerCharacter.fromLegacyCreation(name, Job.WIZARD, hp, 20, 10, 10, dex, 10, 1); }
    private static GameSession session(PlayerCharacter... party) { return new GameSession(List.of(party), PartyLedger.newGame(), new WorldPosition(3, 4), 0); }
    private static PlacedFeature chest(LootTable loot) { return new PlacedFeature(2, 1, PlacedFeatureType.CHEST, loot, null, DONE, null); }
    private static final class Roll extends Random {
        private final long[] picks; int calls;
        Roll(long... picks) { this.picks = picks; }
        @Override public long nextLong(long bound) { if (calls >= picks.length) throw new AssertionError("Unexpected reroll"); long pick = picks[calls++]; if (pick < 0 || pick >= bound) throw new AssertionError("Bad test draw"); return pick; }
    }
    private static LocalFeatureEffects.Result apply(GameSession s, PlacedFeature f, Random roll) { return LocalFeatureEffects.apply(s, 7, f, roll); }
    public static void main(String[] args) throws Exception {
        weighted(); treasure(); capacityAndFailures(); traps(); traversalAndScreen();
        System.out.println("LOCAL_EFFECTS checks=" + checks + " PASS");
    }
    private static void weighted() {
        var table = new LootTable(0, 0).addRandom(101, 1, 2).addRandom(102, 2, 3);
        for (int pick = 0; pick < 5; pick++) same(pick < 2 ? 101 : 102, table.evaluate(new Roll(pick)).items().get(0).itemId(), "weighted boundary " + pick);
        same(Integer.MAX_VALUE, new LootTable(0, Integer.MAX_VALUE).evaluate(new Roll(Integer.MAX_VALUE)).gold(), "inclusive gold range without overflow");
        try { new LootTable(0, 0).addRandom(9999, 1, 1).evaluate(new Roll()); throw new AssertionError("bad item accepted"); } catch (IllegalArgumentException expected) { check(true, "invalid item rejected"); }
    }
    private static void treasure() throws Exception {
        var a = player("First", 30, 10); var b = player("Second", 40, 10); var s = session(a, b);
        var f = chest(new LootTable(5, 5).addGuaranteed(101, 10));
        var result = apply(s, f, new Roll()); same(LocalFeatureEffects.Status.OPENED, result.status(), "treasure opens"); same(405, s.getLedger().getPartyGold(), "gold granted once");
        same(8, new Inventory(a).count(), "first pack filled in party order"); same(2, new Inventory(b).count(), "remaining items distributed"); check(s.hasFlag(DONE), "completion set");
        same(LocalFeatureEffects.Status.UNAVAILABLE, apply(s, f, new Roll()).status(), "repeat claim rejected"); same(405, s.getLedger().getPartyGold(), "repeat gold unchanged"); same(10, new Inventory(a).count()+new Inventory(b).count(), "repeat items unchanged");
        var path = Files.createTempFile("local_effect_save_", ".dat");
        try {
            SaveManager.save(s, path.toString()); var loaded = SaveManager.load(path.toString());
            check(loaded.hasFlag(DONE), "completion survives save/reload"); same(405, loaded.getLedger().getPartyGold(), "reward gold survives save");
            same(10, loaded.getParty().stream().mapToInt(p -> new Inventory(p).count()).sum(), "reward items survive save");
            same(LocalFeatureEffects.Status.UNAVAILABLE, apply(loaded, f, new Roll()).status(), "saved chest cannot be claimed again");
        } finally { Files.delete(path); }
        var empty = session(player("Empty reward", 20, 10)); var emptyChest = chest(new LootTable(0, 0));
        same(LocalFeatureEffects.Status.OPENED, apply(empty, emptyChest, new Roll()).status(), "empty chest still resolves"); check(empty.hasFlag(DONE), "empty chest marked");
        var reentrant = session(player("Listener", 20, 10)); var notificationChest = chest(new LootTable(7, 7));
        var seen = new ArrayList<LocalFeatureEffects.Status>();
        var subscription = GameEventBus.get().subscribe(event -> { if (event instanceof GameEvent.PartyGoldChanged) seen.add(apply(reentrant, notificationChest, new Roll()).status()); });
        try { apply(reentrant, notificationChest, new Roll()); } finally { subscription.cancel(); }
        same(List.of(LocalFeatureEffects.Status.UNAVAILABLE), seen, "notification cannot reenter chest claim"); same(407, reentrant.getLedger().getPartyGold(), "notification does not duplicate gold");
    }
    private static void capacityAndFailures() {
        var p = player("Full pack", 20, 10); var s = session(p); var inventory = new Inventory(p);
        for (int i = 0; i < 7; i++) inventory.add(61);
        var f = chest(new LootTable(10, 20).addGuaranteed(101, 1).addRandom(102, 1, 1).addRandom(103, 1, 1)); var roll = new Roll(1, 1);
        same(LocalFeatureEffects.Status.INVENTORY_FULL, apply(s, f, roll).status(), "full pack blocks entire reward"); same(2, roll.calls, "one stable gold/item roll");
        same(400, s.getLedger().getPartyGold(), "no partial gold"); same(7, inventory.count(), "no partial items"); check(!s.hasFlag(DONE), "full chest not consumed");
        var key = new LocalInteractionState.Key(7, 2, 1); var pending = s.getLocalInteractions().pending(key);
        same(11, pending.gold(), "blocked roll cached"); same(103, pending.items().get(1).itemId(), "random item cached");
        same(LocalFeatureEffects.Status.INVENTORY_FULL, apply(s, f, new Roll()).status(), "retry does not reroll");
        var map = new InteriorMap(7, "Retry area", InteriorMapType.TOWN, 4, 4, "town_standard");
        map.addEntrance(new MapEntrance("entry", new WorldPosition(1,1), Direction.EAST)); map.setDefaultEntranceId("entry"); map.addFeature(f);
        var maps = new InteriorMapRegistry(); maps.register(map);
        var revisit = new LocalMapTraversal(maps, s, 7, null);
        same(LocalFeatureEffects.Status.UNAVAILABLE, revisit.interact(f, new Roll()).status(), "remote reward cannot be claimed");
        revisit.move(Direction.EAST);
        same(LocalFeatureEffects.Status.INVENTORY_FULL, revisit.interact(f, new Roll()).status(), "new visit retains cached roll");
        try { revisit.completeInteraction(f); throw new AssertionError("reward skipped through generic completion"); }
        catch (IllegalArgumentException expected) { check(!s.hasFlag(DONE), "generic completion cannot skip reward transfer"); }
        inventory.removeAt(0); var granted = revisit.interact(f, new Roll()); same(LocalFeatureEffects.Status.OPENED, granted.status(), "retry after making room succeeds"); same(pending.items(), granted.items(), "retry grants original roll"); same(411, s.getLedger().getPartyGold(), "cached gold granted"); same(null, s.getLocalInteractions().pending(key), "pending state cleared");
        var noFlag = new PlacedFeature(2, 1, PlacedFeatureType.CHEST, new LootTable(5, 5));
        same(LocalFeatureEffects.Status.UNAVAILABLE, apply(session(player("No flag", 20, 10)), noFlag, new Roll()).status(), "completion flag required");
        var gated = new PlacedFeature(2, 1, PlacedFeatureType.CHEST, new LootTable(5, 5), GATE, DONE, null); var locked = session(player("Gated", 20, 10));
        same(LocalFeatureEffects.Status.UNAVAILABLE, apply(locked, gated, new Roll()).status(), "gated chest unchanged"); check(!locked.hasFlag(DONE), "gated chest not marked");
        var overflow = session(player("Gold limit", 20, 10)); overflow.getLedger().addGold(Integer.MAX_VALUE - 400);
        same(LocalFeatureEffects.Status.GOLD_LIMIT, apply(overflow, chest(new LootTable(1, 1)), new Roll()).status(), "gold overflow rejected"); same(Integer.MAX_VALUE, overflow.getLedger().getPartyGold(), "overflow leaves purse unchanged"); check(!overflow.hasFlag(DONE), "overflow not consumed");
        var duplicate = player("Same object", 20, 10); var dupParty = session(duplicate, duplicate);
        same(LocalFeatureEffects.Status.INVENTORY_FULL, apply(dupParty, chest(new LootTable(0, 0).addGuaranteed(101, 9)), new Roll()).status(), "duplicate party references cannot double capacity");
        var invalid = session(player("Invalid", 20, 10)); same(LocalFeatureEffects.Status.UNAVAILABLE, apply(invalid, chest(new LootTable(5, 5).addGuaranteed(9999, 1)), new Roll()).status(), "invalid reward refused"); same(400, invalid.getLedger().getPartyGold(), "invalid reward has no partial gold");
        same(LocalFeatureEffects.Status.UNAVAILABLE, apply(session(), f, new Roll()).status(), "empty party cannot claim loot");
    }
    private static void traps() {
        var a = player("Alive", 30, 9); var b = player("Low HP", 9, 9); var dead = player("Dead", 0, 255); var s = session(a,b,dead);
        var trap = new PlacedFeature(2, 1, PlacedFeatureType.TRAP, new TrapData(3, 10, "Test trap", 10), null, DONE, null);
        var result = apply(s, trap, new Roll(0,7)); same(LocalFeatureEffects.Status.TRIGGERED, result.status(), "dead member cannot detect trap");
        same(27, a.getHp(), "minimum inclusive damage"); same(0, b.getHp(), "overkill clamps to zero"); same(0, dead.getHp(), "dead unchanged"); same(2, result.damage().size(), "living members only"); check(s.hasFlag(DONE), "trap marked after effect");
        same(LocalFeatureEffects.Status.UNAVAILABLE, apply(s, trap, new Roll()).status(), "one-shot trap does not repeat"); same(27, a.getHp(), "repeat damage absent");
        var aware = player("Aware", 30, 10); var detected = session(aware); var avoid = apply(detected, trap, new Roll());
        same(LocalFeatureEffects.Status.AVOIDED, avoid.status(), "detection threshold inclusive"); same(30, aware.getHp(), "avoidance does no damage"); check(detected.hasFlag(DONE), "one-shot avoidance resolves trap");
        var recurring = new PlacedFeature(2, 1, PlacedFeatureType.TRAP, new TrapData(2, 2, "Recurring", 0)); var survivor = player("Repeat", 10, 255); var repeat = session(survivor);
        apply(repeat, recurring, new Roll()); apply(repeat, recurring, new Roll()); same(6, survivor.getHp(), "unflagged trap can recur; zero threshold means undetectable");
        var dying = player("Fallen", 1, 1); var wipe = session(dying); same(LocalFeatureEffects.Status.DEFEATED, apply(wipe, recurring, new Roll()).status(), "trap can defeat party");
        same(LocalFeatureEffects.Status.DEFEATED, apply(wipe, chest(new LootTable(5, 5)), new Roll()).status(), "defeated party cannot loot");
        var fresh = session(player("Bad trap", 20, 1)); var bad = new PlacedFeature(2, 1, PlacedFeatureType.TRAP, new TrapData(5, 3, "Bad", 0), null, DONE, null);
        same(LocalFeatureEffects.Status.UNAVAILABLE, apply(fresh, bad, new Roll()).status(), "invalid trap rejected"); same(20, fresh.getParty().get(0).getHp(), "invalid trap no damage"); check(!fresh.hasFlag(DONE), "invalid trap not consumed");
    }
    private static void traversalAndScreen() throws Exception {
        var content = DiagnosticContent.create(); var app = new DiagnosticApplication(content, () -> {}); app.enter(DiagnosticContent.DUNGEON);
        var t = app.traversal(); for (int i=0;i<7;i++) t.move(Direction.EAST); for (int i=0;i<2;i++) t.move(Direction.SOUTH);
        // Now adjacent to the fixture chest at (9,8); actual screen applies its effect.
        same(new WorldPosition(9,7), t.position(), "test chest approach"); key(app, KeyEvent.VK_DOWN);
        same(405, app.session().getLedger().getPartyGold(), "screen grants chest gold"); same(1, new Inventory(app.session().getParty().get(0)).count(), "screen grants item");
        check(app.session().hasFlag(QuestFlag.MISC_FLAG_61), "screen consumes fixture chest");
        t.move(Direction.NORTH); key(app, KeyEvent.VK_DOWN); same(405, app.session().getLedger().getPartyGold(), "screen does not grant chest twice");
        int hp = app.session().getParty().get(0).getHp(); key(app, KeyEvent.VK_RIGHT); int after = app.session().getParty().get(0).getHp();
        check(hp-after>=3 && hp-after<=10, "screen applies authored trap damage"); check(app.session().hasFlag(QuestFlag.MISC_FLAG_62), "screen consumes fixture trap");
        t.move(Direction.NORTH); key(app, KeyEvent.VK_DOWN); same(after, app.session().getParty().get(0).getHp(), "one-shot trap does not fire twice");
        var g = new BufferedImage(GameCanvas.DESIGN_WIDTH, GameCanvas.DESIGN_HEIGHT, BufferedImage.TYPE_INT_RGB).createGraphics(); try { app.manager().render(g, GameCanvas.DESIGN_WIDTH, GameCanvas.DESIGN_HEIGHT); check(true, "HP/gold HUD renders"); } finally { g.dispose(); }
        var p = t.position(); app.session().getParty().get(0).setHp(0); key(app, KeyEvent.VK_RIGHT); same(p, t.position(), "defeated screen blocks movement"); key(app, KeyEvent.VK_ESCAPE); same(GameState.PAUSE, app.manager().getActiveState(), "defeat still permits diagnostic pause"); key(app, KeyEvent.VK_R); same(100, app.session().getParty().get(0).getHp(), "reset recovers test party"); check(!app.session().hasFlag(QuestFlag.MISC_FLAG_61), "reset clears chest completion");
        // Runtime validation requires stable one-shot completion and passable trigger tiles.
        var map = content.maps().get(DiagnosticContent.DEEP); map.addFeature(new PlacedFeature(4,4,PlacedFeatureType.CHEST,new LootTable(1,1)));
        check(AuthoredContentValidator.validate(content.maps().getAll(), content.npcs().getAll(), content.shops().getAll()).stream().anyMatch(i->i.code().equals("CHEST_COMPLETION")), "bake diagnoses missing completion flag");
        map.setTile(4,4,1); check(AuthoredContentValidator.validate(content.maps().getAll(), content.npcs().getAll(), content.shops().getAll()).stream().anyMatch(i->i.code().equals("TRIGGER_TILE")), "bake diagnoses blocked trigger tile");
    }
    private static void key(DiagnosticApplication app, int key) {
        var canvas=new Canvas(); app.manager().getActiveScreen().onKeyPressed(new KeyEvent(canvas,KeyEvent.KEY_PRESSED,0,0,key,KeyEvent.CHAR_UNDEFINED));
        app.manager().update(.2f); app.manager().update(.2f);
        app.manager().getActiveScreen().onKeyReleased(new KeyEvent(canvas,KeyEvent.KEY_RELEASED,0,0,key,KeyEvent.CHAR_UNDEFINED));
    }
}
