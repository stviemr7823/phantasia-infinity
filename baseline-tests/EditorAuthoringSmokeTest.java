package com.phantasia.editor;

import com.phantasia.core.data.*;
import com.phantasia.core.logic.Direction;
import com.phantasia.core.model.*;
import com.phantasia.core.world.*;
import com.phantasia.core.world.ServiceData.*;
import com.phantasia.editor.commands.EditFeatureCommand;
import com.phantasia.editor.panels.FeatureEditForm;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Exercises actual Swing form models without opening a window, then export boundaries. */
public final class EditorAuthoringSmokeTest {
    private static int checks;
    private static final QuestFlag A = QuestFlag.values()[0], B = QuestFlag.values()[1];
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void same(Object a, Object b, String message) { check(Objects.equals(a, b), message + ": " + a + " != " + b); }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(EditorAuthoringSmokeTest::forms);
        commands(); validation(); export();
        System.out.println("EDITOR_AUTHORING checks=" + checks + " PASS");
    }
    private static Map<PlacedFeatureType, Object> payloads() {
        Map<PlacedFeatureType, Object> data = new LinkedHashMap<>();
        data.put(PlacedFeatureType.SIGN, "Town notice\n第二行"); data.put(PlacedFeatureType.ALTAR, "Remember");
        data.put(PlacedFeatureType.SHOP_COUNTER, 42); data.put(PlacedFeatureType.INN_COUNTER, InnData.standard());
        data.put(PlacedFeatureType.GUILD_COUNTER, GuildData.standard()); data.put(PlacedFeatureType.BANK_COUNTER, BankData.standard());
        data.put(PlacedFeatureType.LOCKED_DOOR, new LockData(170, A, "A key or permission"));
        data.put(PlacedFeatureType.STAIRS_LINK, new StairsData(2, 0, new WorldPosition(1, 1)));
        data.put(PlacedFeatureType.EXIT, new ExitData(new WorldPosition(4, 5))); data.put(PlacedFeatureType.TRAP, TrapData.standard());
        data.put(PlacedFeatureType.CHEST, new LootTable(5, 15).rolls(2).addGuaranteed(1, 3).addRandom(2, 1, 7));
        return data;
    }
    private static Component find(Component root, String name) {
        if (name.equals(root.getName())) return root;
        if (root instanceof Container parent) for (Component c : parent.getComponents()) {
            Component found = find(c, name); if (found != null) return found;
        }
        return null;
    }
    private static void field(FeatureEditForm form, String name, String value) {
        ((JTextField) Objects.requireNonNull(find(form, name), name)).setText(value);
    }
    private static void forms() {
        for (var e : payloads().entrySet()) {
            var old = new PlacedFeature(2, 3, e.getKey(), e.getValue(), A, B, "custom");
            var form = new FeatureEditForm(2, 3, old); var copy = form.readFeature();
            same(old.x(), copy.x(), "x"); same(old.y(), copy.y(), "y"); same(old.featureType(), copy.featureType(), "type");
            same(A, copy.requiredFlag(), "activation flag"); same(B, copy.consumedFlag(), "one-shot flag"); same("custom", copy.assetId(), "asset");
            if (old.serviceData() instanceof LootTable loot) {
                var other = (LootTable) copy.serviceData();
                same(loot.getGoldMin(), other.getGoldMin(), "gold min"); same(loot.getGoldMax(), other.getGoldMax(), "gold max");
                same(loot.getGuaranteed(), other.getGuaranteed(), "guaranteed items"); same(loot.getRandomPool(), other.getRandomPool(), "random items");
                same(loot.getRollCount(), other.getRollCount(), "draws");
                // Actual table-cell edits must be read; the original table stays unchanged.
                JTable table = (JTable) find(form, "guaranteed"); table.setValueAt("6", 0, 1);
                ((DefaultTableModel) ((JTable) find(form, "random")).getModel()).addRow(new Object[]{"3", "2", "4"});
                var edited = (LootTable) form.readFeature().serviceData();
                same(6, edited.getGuaranteed().get(0).quantity(), "edited item quantity");
                same(2, edited.getRandomPool().size(), "added random item"); same(3, loot.getGuaranteed().get(0).quantity(), "draft isolation");
            } else same(old.serviceData(), copy.serviceData(), "typed payload");
        }
        var sign = new FeatureEditForm(0, 0, new PlacedFeature(0, 0, PlacedFeatureType.SIGN, "Before", A, B, null));
        ((JTextArea) find(sign, "text")).setText("After\nSecond line");
        @SuppressWarnings("unchecked") var type = (JComboBox<PlacedFeatureType>) find(sign, "featureType");
        type.setSelectedItem(PlacedFeatureType.GUILD_COUNTER); field(sign, "maxLevel", "20");
        check(sign.readFeature().serviceData() instanceof GuildData, "switch type creates typed data");
        type.setSelectedItem(PlacedFeatureType.SIGN);
        same("After\nSecond line", sign.readFeature().serviceData(), "switching types keeps drafts");
        same(B, sign.readFeature().consumedFlag(), "type switch keeps metadata");
        type.setSelectedItem(PlacedFeatureType.GUILD_COUNTER);
        same(20, ((GuildData) sign.readFeature().serviceData()).maxLevel(), "numeric draft retained");
        field(sign, "maxLevel", "oops");
        try { sign.readFeature(); throw new AssertionError("bad integer accepted"); }
        catch (IllegalArgumentException expected) { check(true, "bad integer rejected"); }
        var shop = new ShopInventory(42, "Inline shop"); shop.addEntry(new ShopEntry(1, -1, 1f, A, false));
        var inline = new FeatureEditForm(0, 0, new PlacedFeature(0, 0, PlacedFeatureType.SHOP_COUNTER, shop));
        check(shop == inline.readFeature().serviceData(), "inline stock preserved on unrelated edits");
        ((JCheckBox) find(inline, "useReference")).setSelected(true); field(inline, "shopId", "45");
        same(45, inline.readFeature().serviceData(), "explicit inline-to-reference conversion");
        var exit = new FeatureEditForm(0, 0, new PlacedFeature(0, 0, PlacedFeatureType.EXIT));
        same(new ExitData(null), exit.readFeature().serviceData(), "entry-point exit default");
        ((JCheckBox) find(exit, "entryPoint")).setSelected(false); field(exit, "arrivalX", "12"); field(exit, "arrivalY", "13");
        same(new ExitData(new WorldPosition(12, 13)), exit.readFeature().serviceData(), "explicit world exit");
        var nullable = new FeatureEditForm(0, 0, new PlacedFeature(0, 0, PlacedFeatureType.INN_COUNTER, new InnData(1, null, false)));
        same(new InnData(1, null, false), nullable.readFeature().serviceData(), "optional null greeting preserved");
    }
    private static void commands() {
        EditorState state = new EditorState(); var map = map(1);
        var first = new PlacedFeature(0, 0, PlacedFeatureType.SIGN, "First");
        var middle = new PlacedFeature(1, 0, PlacedFeatureType.SIGN, "Middle", A, B, "visual");
        var last = new PlacedFeature(2, 0, PlacedFeatureType.SIGN, "Last");
        map.addFeature(first); map.addFeature(middle); map.addFeature(last); state.putInteriorMap(map);
        List<PlacedFeature> before = List.copyOf(map.getFeatures());
        var replacement = new PlacedFeature(1, 0, PlacedFeatureType.LOCKED_DOOR, LockData.byFlag(A, "Closed"), A, B, "gate");
        state.execute(new EditFeatureCommand(map, middle, replacement));
        same(List.of(first, replacement, last), map.getFeatures(), "edit preserves order"); check(state.isDirty(), "edit dirties map");
        state.undo(); same(before, map.getFeatures(), "undo exact original with metadata");
        state.redo(); same(replacement, map.getFeatureAt(1, 0), "redo edit");
        state.execute(new EditFeatureCommand(map, replacement, null)); check(map.getFeatureAt(1, 0) == null, "remove");
        state.undo(); same(List.of(first, replacement, last), map.getFeatures(), "undo removal preserves order");
        var added = new PlacedFeature(3, 0, PlacedFeatureType.SIGN, "New");
        state.execute(new EditFeatureCommand(map, null, added)); same(added, map.getFeatureAt(3, 0), "place");
        state.undo(); check(map.getFeatureAt(3, 0) == null, "undo place"); state.redo(); same(added, map.getFeatureAt(3, 0), "redo place");
    }
    private static InteriorMap map(int id) { return new InteriorMap(id, "Area " + id, InteriorMapType.DUNGEON, 6, 6, "dungeon_standard"); }
    private static EditorState validState() {
        var state = new EditorState(); var source = map(1); var target = map(2);
        int i = 0;
        for (var e : payloads().entrySet()) { source.addFeature(new PlacedFeature(i % 6, i / 6, e.getKey(), e.getValue(), A, B, "visual")); i++; }
        target.addFeature(new PlacedFeature(0, 0, PlacedFeatureType.STAIRS_LINK, new StairsData(1, 0, new WorldPosition(5, 5))));
        source.addNpc(new PlacedNpc(10, 4, 4, Direction.SOUTH));
        var npc = new NpcDefinition(10, "Keeper", NpcRole.MERCHANT, null).shopId(42);
        var shop = new ShopInventory(42, "Supplies"); shop.addEntry(new ShopEntry(1, -1, 1f, null, false));
        state.putInteriorMap(source); state.putInteriorMap(target); state.putNpc(npc); state.putShop(shop); return state;
    }
    private static void code(EditorState state, String expected) {
        check(state.validateAuthoredContent().stream().anyMatch(i -> i.code().equals(expected)), "diagnostic " + expected);
    }
    private static void validation() {
        var state = validState(); check(state.validateAuthoredContent().isEmpty(), "valid connected content: " + state.validateAuthoredContent());
        var defaults = new EditorState(); defaults.importBuiltinData();
        check(defaults.validateAuthoredContent().isEmpty(), "built-in defaults remain exportable: " + defaults.validateAuthoredContent());
        state.getInteriorMap(2).setTile(1, 1, 1); code(state, "ARRIVAL");
        state = validState(); state.removeInteriorMap(2); code(state, "MAP_REFERENCE");
        state = validState(); state.removeNpc(10); code(state, "NPC_REFERENCE");
        state = validState(); state.removeShop(42); code(state, "SHOP_REFERENCE");
        state = validState(); state.getInteriorMap(1).setTile(0, 0, 99); code(state, "TILE_INDEX");
        state = validState(); state.getInteriorMap(1).addFeature(new PlacedFeature(8, 8, PlacedFeatureType.INN_COUNTER, "old text")); code(state, "PLACEMENT"); code(state, "PAYLOAD");
        state = validState(); state.getInteriorMap(1).addFeature(new PlacedFeature(0, 0, PlacedFeatureType.SIGN, "Overlapping")); code(state, "OVERLAP");
        state = validState(); state.getInteriorMap(1).addFeature(new PlacedFeature(5, 5, PlacedFeatureType.CHEST, new LootTable(-1, -2).addGuaranteed(9999, 0))); code(state, "RANGE"); code(state, "ITEM_REFERENCE"); code(state, "QUANTITY");
        state = validState(); state.getInteriorMap(1).addFeature(new PlacedFeature(5, 5, PlacedFeatureType.STAIRS_LINK, new StairsData(2, 1, new WorldPosition(1, 1)))); code(state, "FLOOR");
        state = validState(); state.getInteriorMap(1).addFeature(new PlacedFeature(5, 5, PlacedFeatureType.LOCKED_DOOR, LockData.byItem(0, "Closed"))); code(state, "LOCK");
        var m = map(1);
        check(AuthoredContentValidator.validate(List.of(m, map(1)), List.of(), List.of()).stream().anyMatch(i -> i.code().equals("DUPLICATE_ID")), "duplicate IDs rejected");
    }
    private static void export() throws Exception {
        Path dir = Files.createTempDirectory("phantasia_authoring_");
        try {
            var state = validState(); Path project = dir.resolve("draft.phantasia"); state.saveProject(project);
            var reopened = new EditorState(); reopened.openProject(project);
            Path out = dir.resolve("export"); reopened.bakeAll(out);
            same(2, InteriorMapLoader.load(out.resolve("interiors.dat").toString()).size(), "clean project exports all maps");
            same(1, NpcBaker.load(out.resolve("npcs.dat").toString()).size(), "clean project exports NPCs");
            same(1, ShopBaker.load(out.resolve("shops.dat").toString()).size(), "clean project exports shops");
            byte[] original = Files.readAllBytes(out.resolve("interiors.dat"));
            reopened.removeInteriorMap(2); reopened.markDirty("interiorMap:1");
            reopened.saveProject(project); // invalid content remains preservable as a draft
            reopened.markDirty("interiorMap:1");
            for (String category : List.of("all", "interiorMap", "npc", "shop")) {
                try { if (category.equals("all")) reopened.bakeAll(out); else reopened.bakeCategory(out, category);
                    throw new AssertionError("invalid export accepted"); }
                catch (IOException expected) { check(expected.getMessage().contains("destination map #2"), "export explains reference error"); }
                check(Arrays.equals(original, Files.readAllBytes(out.resolve("interiors.dat"))), "invalid bake leaves prior output intact");
                check(reopened.isDirty(), "invalid bake keeps dirty state");
            }
            Path missing = dir.resolve("must-not-exist");
            try { reopened.bakeAll(missing); throw new AssertionError("invalid export accepted"); } catch (IOException expected) { check(!Files.exists(missing), "validation before output creation"); }
            reopened.removeInteriorMap(1); reopened.removeNpc(10); reopened.removeShop(42); reopened.bakeAll(out);
            same(0, InteriorMapLoader.load(out.resolve("interiors.dat").toString()).size(), "empty export clears removed content");
        } finally { try (var files = Files.walk(dir)) { for (var p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); } }
    }
}
