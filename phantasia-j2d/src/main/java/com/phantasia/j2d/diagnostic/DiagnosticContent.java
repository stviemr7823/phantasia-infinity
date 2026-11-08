package com.phantasia.j2d.diagnostic;

import com.phantasia.core.data.*;
import com.phantasia.core.logic.Direction;
import com.phantasia.core.model.*;
import com.phantasia.core.world.*;
import com.phantasia.core.world.ServiceData.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/** Non-canon authoring fixture. Never reads or writes a normal game save. */
public final class DiagnosticContent {
    public static final int SETTLEMENT = 1000, DUNGEON = 1001, DEEP = 1002, COURTYARD = 1003, HALL = 1004;
    public static final int GUIDE = 9101, MERCHANT = 9102, TRAINER = 9103, GUARD = 9104, HALL_NPC = 9105, SHOP = 9001;
    public static final QuestFlag PERMISSION = QuestFlag.MISC_FLAG_58, GATE_OPEN = QuestFlag.MISC_FLAG_59;
    public record Bundle(InteriorMapRegistry maps, NpcRegistry npcs, ShopRegistry shops) {
        public void validate() throws IOException {
            var errors = AuthoredContentValidator.validate(maps.getAll(), npcs.getAll(), shops.getAll());
            if (!errors.isEmpty()) throw new IOException(String.join("\n", errors.stream().map(Object::toString).toList()));
        }
    }
    private DiagnosticContent() {}
    public static Bundle create() throws IOException {
        var maps = new InteriorMapRegistry(); var npcs = new NpcRegistry(); var shops = new ShopRegistry();
        var town = room(SETTLEMENT, "Test settlement", 22, 14, false);
        entry(town, "entry", 3, 6, Direction.EAST, true); entry(town, "dungeon", 17, 6, Direction.WEST, false); entry(town, "castle", 10, 3, Direction.SOUTH, false);
        town.addFeature(new PlacedFeature(1, 6, PlacedFeatureType.EXIT, ExitData.toEntryPoint()));
        sign(town, 3, 4, "Diagnostic area only. East: two dungeon rooms. North: castle. Speak to Test guide for gate permission. West exit returns to the test menu.");
        link(town, 20, 6, DUNGEON, "entry"); link(town, 10, 1, COURTYARD, "entry");
        town.addNpc(new PlacedNpc(GUIDE, 8, 8, Direction.NORTH)); town.addNpc(new PlacedNpc(MERCHANT, 6, 6, Direction.WEST)); town.addNpc(new PlacedNpc(TRAINER, 6, 10, Direction.NORTH));
        town.addFeature(new PlacedFeature(15, 9, PlacedFeatureType.INN_COUNTER, new InnData(2, "Test inn: rest here before returning to the dungeon.", false)));
        town.addFeature(new PlacedFeature(17, 9, PlacedFeatureType.BANK_COUNTER, BankData.standard()));
        town.addFeature(new PlacedFeature(19, 9, PlacedFeatureType.GUILD_COUNTER, new GuildData(5, 2, 2)));
        for (int y = 8; y <= 11; y++) town.setTile(12, y, 2);
        for (int x = 2; x <= 5; x++) town.setTile(x, 11, 15);
        var dungeon = room(DUNGEON, "Test dungeon / room 1", 16, 12, true);
        entry(dungeon, "entry", 2, 5, Direction.EAST, true); entry(dungeon, "deep", 12, 7, Direction.WEST, false);
        link(dungeon, 1, 5, SETTLEMENT, "dungeon"); link(dungeon, 14, 5, DEEP, "entry");
        for (int y = 2; y <= 9; y++) if (y != 5) dungeon.setTile(8, y, 1);
        sign(dungeon, 5, 3, "East leads to the second test room. West returns to the settlement. Chest rewards, trap damage and test encounters are active. Battle Enter: engage; Space: select actions.");
        dungeon.addFeature(new PlacedFeature(9, 8, PlacedFeatureType.CHEST, new LootTable(5, 5).addGuaranteed(101, 1), null, QuestFlag.MISC_FLAG_61, null));
        dungeon.addFeature(new PlacedFeature(10, 8, PlacedFeatureType.TRAP, TrapData.standard(), null, QuestFlag.MISC_FLAG_62, null));
        var deep = room(DEEP, "Test dungeon / room 2", 14, 10, true);
        entry(deep, "entry", 2, 5, Direction.EAST, true); link(deep, 1, 5, DUNGEON, "deep");
        sign(deep, 7, 5, "Second diagnostic dungeon room. This text is an editable placeholder, not story content. Return west.");
        var courtyard = room(COURTYARD, "Test castle courtyard", 18, 12, false);
        entry(courtyard, "entry", 8, 9, Direction.NORTH, true); entry(courtyard, "hall", 8, 4, Direction.SOUTH, false);
        link(courtyard, 8, 10, SETTLEMENT, "castle"); link(courtyard, 8, 1, HALL, "entry");
        for (int x = 1; x < 17; x++) courtyard.setTile(x, 6, 2);
        courtyard.addFeature(new PlacedFeature(8, 6, PlacedFeatureType.LOCKED_DOOR, LockData.byFlag(PERMISSION, "Test gate is closed. Speak to Test guide in the settlement."), null, GATE_OPEN, null));
        courtyard.addNpc(new PlacedNpc(GUARD, 6, 4, Direction.SOUTH, PERMISSION, null));
        var hall = room(HALL, "Test castle hall", 14, 10, false);
        entry(hall, "entry", 6, 7, Direction.NORTH, true); link(hall, 6, 8, COURTYARD, "hall");
        hall.addNpc(new PlacedNpc(HALL_NPC, 6, 3, Direction.SOUTH, PERMISSION, null));
        for (var map : List.of(town, dungeon, deep, courtyard, hall)) maps.register(map);
        var guide = new NpcDefinition(GUIDE, "Test guide", NpcRole.INFORMANT, null);
        guide.getDialogue().addNode(new DialogueNode("Test gate permission is already set. The castle is north; the dungeon is east.", PERMISSION, null, null));
        guide.getDialogue().addNode(new DialogueNode("Diagnostic gate permission granted. The castle gate will now open. This flag is confined to the test session.", null, PERMISSION, PERMISSION));
        npcs.register(guide); npcs.register(new NpcDefinition(MERCHANT, "Test merchant", NpcRole.MERCHANT, null).shopId(SHOP));
        npcs.register(new NpcDefinition(TRAINER, "Test trainer", NpcRole.TRAINER, null));
        npcs.register(speaker(GUARD, "Test guard", "Conditional NPC visible after test gate permission. Return routes remain open."));
        npcs.register(speaker(HALL_NPC, "Test hall NPC", "Castle-room travel and gated NPC visibility are connected. Story details remain undecided."));
        var shop = new ShopInventory(SHOP, "Test supplies"); shop.addEntry(new ShopEntry(101)); shop.addEntry(new ShopEntry(61));
        shop.addEntry(new ShopEntry(102, 2, 0.1f)); shop.addEntry(new ShopEntry(111, -1, 0.25f, null, true)); shops.register(shop);
        var bundle = new Bundle(maps, npcs, shops); bundle.validate(); return bundle;
    }
    private static NpcDefinition speaker(int id, String name, String text) { var n = new NpcDefinition(id, name, NpcRole.GUARD, null); n.getDialogue().addNode(new DialogueNode(text, null, null, null)); return n; }
    private static InteriorMap room(int id, String name, int w, int h, boolean dungeon) {
        var m = new InteriorMap(id, name, dungeon ? InteriorMapType.DUNGEON : InteriorMapType.TOWN, w, h, dungeon ? "dungeon_standard" : "town_standard");
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) m.setTile(x, y, x == 0 || y == 0 || x == w-1 || y == h-1 ? dungeon ? 1 : 2 : dungeon ? 0 : 14);
        m.getSettings().setEncountersEnabled(dungeon); m.getSettings().setEncounterInterval(dungeon ? 6 : 0);
        m.getSettings().setTorchRadius(4); return m;
    }
    private static void entry(InteriorMap m, String name, int x, int y, Direction facing, boolean selected) { m.addEntrance(new MapEntrance(name, new WorldPosition(x, y), facing)); if (selected) m.setDefaultEntranceId(name); }
    private static void link(InteriorMap m, int x, int y, int target, String name) { m.addFeature(new PlacedFeature(x, y, PlacedFeatureType.STAIRS_LINK, new MapLinkData(target, name))); }
    private static void sign(InteriorMap m, int x, int y, String text) { m.addFeature(new PlacedFeature(x, y, PlacedFeatureType.SIGN, text)); }

    /** Writes only into a new directory, then reloads the runtime representations. */
    public static Bundle exportAndLoad(Bundle content, Path directory) throws IOException {
        content.validate(); Files.createDirectory(directory);
        InteriorMapBaker.bake(content.maps(), directory.resolve("interiors.dat").toString());
        NpcBaker.bake(content.npcs(), directory.resolve("npcs.dat").toString()); ShopBaker.bake(content.shops(), directory.resolve("shops.dat").toString());
        return load(directory);
    }
    public static Bundle load(Path directory) throws IOException {
        for (String file : List.of("interiors.dat", "npcs.dat", "shops.dat")) if (!Files.isRegularFile(directory.resolve(file))) throw new IOException("Missing diagnostic content: " + file);
        var bundle = new Bundle(InteriorMapLoader.load(directory.resolve("interiors.dat").toString()), NpcBaker.load(directory.resolve("npcs.dat").toString()), ShopBaker.load(directory.resolve("shops.dat").toString()));
        bundle.validate(); return bundle;
    }
}
