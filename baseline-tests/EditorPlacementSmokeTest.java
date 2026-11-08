package com.phantasia.editor;

import com.phantasia.core.data.*;
import com.phantasia.core.logic.Direction;
import com.phantasia.core.model.*;
import com.phantasia.core.world.*;
import com.phantasia.core.world.ServiceData.MapLinkData;
import com.phantasia.editor.commands.*;
import com.phantasia.editor.panels.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public final class EditorPlacementSmokeTest {
    private static int checks;
    private static final QuestFlag A = QuestFlag.values()[0], B = QuestFlag.values()[1];
    private static void check(boolean ok, String text) { checks++; if (!ok) throw new AssertionError(text); }
    private static void same(Object a, Object b, String text) { check(Objects.equals(a, b), text + ": " + a + " != " + b); }
    private static Component find(Component c, String name) {
        if (name.equals(c.getName())) return c;
        if (c instanceof Container parent) for (Component child : parent.getComponents()) { var result = find(child, name); if (result != null) return result; }
        return null;
    }
    private static void text(Component form, String name, String value) { ((JTextField) Objects.requireNonNull(find(form, name))).setText(value); }
    @SuppressWarnings("unchecked") private static void select(Component form, String name, Object value) { ((JComboBox<Object>) find(form, name)).setSelectedItem(value); }
    private static void rejected(Runnable action, String text) { try { action.run(); throw new AssertionError(text); } catch (IllegalArgumentException | IllegalStateException e) { check(true, text); } }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(EditorPlacementSmokeTest::forms);
        commandsAndResolution(); persistenceAndMigration();
        System.out.println("EDITOR_PLACEMENT checks=" + checks + " PASS");
    }
    private static NpcDefinition npc() { return new NpcDefinition(10, "Keeper", NpcRole.INFORMANT, "keeper"); }
    private static InteriorMap map(int id) {
        var m = new InteriorMap(id, "Area " + id, InteriorMapType.TOWN, 4, 4, "town_standard");
        m.addEntrance(new MapEntrance("entry", new WorldPosition(1, 1), Direction.SOUTH));
        m.addEntrance(new MapEntrance("gate", new WorldPosition(2, 2), Direction.WEST)); m.setDefaultEntranceId("entry"); return m;
    }
    private static void forms() {
        var old = new PlacedNpc(10, 3, 2, Direction.WEST, A, B);
        var form = new NpcPlacementForm(0, 0, old, List.of(npc())); same(old, form.readPlacement(), "NPC fields survive editing");
        text(form, "npcX", "1"); select(form, "facing", Direction.NORTH);
        same(new PlacedNpc(10, 1, 2, Direction.NORTH, A, B), form.readPlacement(), "move and face preserve flags");
        select(form, "disappearFlag", A); rejected(form::readPlacement, "contradictory flags rejected"); select(form, "disappearFlag", B);
        text(form, "npcX", "-1"); rejected(form::readPlacement, "negative position rejected");
        var missing = new NpcPlacementForm(0, 0, old, List.of()); same(old, missing.readPlacement(), "missing definition ID retained for diagnosis");
        rejected(() -> new NpcPlacementForm(0, 0, null, List.of()).readPlacement(), "empty definition registry not silently filled");
        var entrance = new MapEntrance("gate", new WorldPosition(2, 2), Direction.EAST);
        var entryForm = new EntranceEditForm(0, 0, entrance, "gate"); same(entrance, entryForm.readEntrance(), "entrance fields"); check(entryForm.isDefaultEntrance(), "default checkbox loaded");
        text(entryForm, "entranceId", "north_gate"); text(entryForm, "arrivalY", "1"); select(entryForm, "facing", Direction.NORTH);
        same(new MapEntrance("north_gate", new WorldPosition(2, 1), Direction.NORTH), entryForm.readEntrance(), "entrance edits");
        text(entryForm, "entranceId", " "); rejected(entryForm::readEntrance, "empty entrance name rejected");
        var link = new MapLinkData(0, "gate");
        var feature = new FeatureEditForm(0, 0, new PlacedFeature(0, 0, PlacedFeatureType.STAIRS_LINK, link, A, B, "door"));
        same(link, feature.readFeature().serviceData(), "named link form preserves exact map 0");
        text(feature, "entranceId", "north_gate"); same(new MapLinkData(0, "north_gate"), feature.readFeature().serviceData(), "named link changes");
        same(B, feature.readFeature().consumedFlag(), "link preserves flags");
    }
    private static void commandsAndResolution() {
        var state = new EditorState(); var origin = map(1); var target = map(0); state.putInteriorMap(origin); state.putInteriorMap(target); state.putNpc(npc());
        var link = new MapLinkData(0, "gate"); origin.addFeature(new PlacedFeature(0, 0, PlacedFeatureType.STAIRS_LINK, link));
        var registry = new InteriorMapRegistry(); registry.register(origin); registry.register(target);
        same(target, registry.resolveLink(link).map(), "map 0 is an exact destination"); same(new WorldPosition(2, 2), registry.resolveLink(link).entrance().position(), "named arrival");
        same(new WorldPosition(1, 1), registry.getSpawnPosition(1), "per-map default replaces fixed 9,13");
        var old = target.getEntrance("gate"); var moved = new MapEntrance("gate", new WorldPosition(2, 1), Direction.NORTH);
        state.execute(new EditEntranceCommand(target, old, moved, false));
        same(moved, registry.resolveLink(link).entrance(), "link follows moved entrance without rewrites"); same(link, origin.getFeatureAt(0, 0).serviceData(), "source link unchanged");
        state.undo(); same(old, registry.resolveLink(link).entrance(), "undo entrance movement"); state.redo();
        var renamed = new MapEntrance("north_gate", moved.position(), moved.facing());
        state.execute(new EditEntranceCommand(target, moved, renamed, true)); same("north_gate", target.getDefaultEntranceId(), "new default entrance");
        check(state.validateAuthoredContent().stream().anyMatch(i -> i.code().equals("ENTRANCE_REFERENCE")), "renamed link requires explicit repair");
        rejected(() -> registry.resolveLink(link), "unresolved name fails explicitly"); state.undo(); same("entry", target.getDefaultEntranceId(), "undo restores default");
        rejected(() -> new EditEntranceCommand(target, null, target.getEntrance("entry"), false), "duplicate name rejected");
        var before = List.copyOf(target.getEntrances()); var extra = new MapEntrance("side", new WorldPosition(3, 1), Direction.EAST);
        state.execute(new EditEntranceCommand(target, null, extra, false)); same(extra, target.getEntrance("side"), "place entrance"); state.undo(); same(before, target.getEntrances(), "undo placement preserves order"); state.redo();
        state.execute(new EditEntranceCommand(target, extra, null, false)); state.undo(); same(extra, target.getEntrance("side"), "undo removal");
        var defaultEntry = target.getEntrance("entry"); state.execute(new EditEntranceCommand(target, defaultEntry, null, false));
        same(null, target.getDefaultEntranceId(), "deleting default does not choose another silently");
        check(state.validateAuthoredContent().stream().anyMatch(i -> i.code().equals("DEFAULT_ENTRANCE")), "missing default diagnosed before export"); rejected(() -> registry.getSpawnPosition(0), "missing default fails explicitly"); state.undo();
        var first = new PlacedNpc(10, 3, 2, Direction.WEST, A, B); var last = new PlacedNpc(10, 3, 3, Direction.SOUTH);
        target.addNpc(first); target.addNpc(last); var npcOrder = List.copyOf(target.getPlacedNpcs());
        var movedNpc = new PlacedNpc(10, 2, 3, Direction.NORTH, A, B);
        state.execute(new EditNpcPlacementCommand(target, first, movedNpc)); same(List.of(movedNpc, last), target.getPlacedNpcs(), "NPC move preserves placement order");
        state.undo(); same(npcOrder, target.getPlacedNpcs(), "undo exact NPC record"); state.redo();
        state.execute(new EditNpcPlacementCommand(target, movedNpc, null)); state.undo(); same(movedNpc, target.getPlacedNpcs().get(0), "undo NPC removal");
        var third = new PlacedNpc(10, 0, 3, Direction.EAST); state.execute(new EditNpcPlacementCommand(target, null, third)); state.undo(); same(List.of(movedNpc, last), target.getPlacedNpcs(), "undo NPC placement"); state.redo();
        same("Keeper", state.getNpc(10).getName(), "shared definition unchanged");
        check(state.validateAuthoredContent().isEmpty(), "valid named links and NPCs: " + state.validateAuthoredContent());
        target.addNpc(new PlacedNpc(10, moved.position().x(), moved.position().y(), Direction.SOUTH, A, B));
        check(state.validateAuthoredContent().stream().anyMatch(i -> i.code().equals("ENTRANCE_POSITION")), "conditional NPC blocks arrival conservatively");
        rejected(() -> registry.resolveLink(link), "occupied entrance fails at resolution");
        same(new WorldPosition(9, 13), InteriorMapRegistry.buildDefault().getSpawnPosition(0), "traditional town spawn preserved explicitly");
    }
    private static void persistenceAndMigration() throws Exception {
        Path dir = Files.createTempDirectory("phantasia_placements_");
        try {
            var state = new EditorState(); var origin = map(1); var target = map(0);
            origin.addFeature(new PlacedFeature(0, 0, PlacedFeatureType.STAIRS_LINK, new MapLinkData(0, "gate"), A, B, "gate"));
            target.addNpc(new PlacedNpc(10, 3, 3, Direction.NORTH, A, B)); state.putInteriorMap(origin); state.putInteriorMap(target); state.putNpc(npc());
            Path archive = dir.resolve("project.phantasia"); state.saveProject(archive); var fresh = new EditorState(); fresh.openProject(archive);
            same(target.getEntrances(), fresh.getInteriorMap(0).getEntrances(), "archive entrances"); same("entry", fresh.getInteriorMap(0).getDefaultEntranceId(), "archive default");
            same(origin.getFeatures(), fresh.getInteriorMap(1).getFeatures(), "named payload and metadata"); same(target.getPlacedNpcs(), fresh.getInteriorMap(0).getPlacedNpcs(), "NPC visibility and facing");
            fresh.bakeAll(dir.resolve("baked")); var registry = InteriorMapLoader.load(dir.resolve("baked/interiors.dat").toString());
            same(target.getEntrance("gate"), registry.resolveLink(new MapLinkData(0, "gate")).entrance(), "runtime resolves exported entrance");
            var bytes = Files.readAllBytes(dir.resolve("baked/interiors.dat")); same(3, bytes[5] & 255, "writer emits v3");
            // Independent old-format fixtures: neither contained authored entrances.
            for (int version : List.of(1, 2)) {
                Path old = dir.resolve("v" + version + ".dat");
                try (var out = new DataOutputStream(Files.newOutputStream(old))) {
                    out.writeBytes("INTR"); out.writeShort(version); out.writeShort(1);
                    if (version == 1) {
                        out.writeShort(7); text(out, "Old", false); out.writeByte(0); text(out, "town_standard", false); out.writeShort(2); out.writeShort(2);
                        for (int i = 0; i < 4; i++) out.writeByte(0);
                        out.writeByte(0); out.writeByte(0); out.writeShort(0); out.writeByte(0); out.writeByte(0); out.writeShort(0); out.writeShort(0);
                    } else {
                        out.writeInt(7); text(out, "Old", true); text(out, "TOWN", true); text(out, "town_standard", true); out.writeInt(2); out.writeInt(2);
                        for (int i = 0; i < 4; i++) out.writeInt(0);
                        out.writeBoolean(false); out.writeBoolean(false); out.writeInt(0); text(out, "FULL", true); out.writeInt(0); out.writeInt(-1);
                        out.writeInt(0); out.writeInt(0);
                    }
                }
                var migrated = InteriorMapLoader.load(old.toString()); same(new WorldPosition(0, 0), migrated.getSpawnPosition(7), "v" + version + " small-map migration");
                same("entry", migrated.get(7).getDefaultEntranceId(), "v" + version + " explicit migrated entry");
                Path upgraded = dir.resolve("upgraded" + version + ".dat"); InteriorMapBaker.bake(migrated, upgraded.toString());
                same(migrated.get(7).getEntrances(), InteriorMapLoader.load(upgraded.toString()).get(7).getEntrances(), "migrated entry survives upgrade");
            }
            var draft = map(4); draft.setDefaultEntranceId(null); var draftRegistry = new InteriorMapRegistry(); draftRegistry.register(draft);
            Path path = dir.resolve("draft.dat"); InteriorMapBaker.bake(draftRegistry, path.toString()); var loaded = InteriorMapLoader.load(path.toString());
            same(null, loaded.get(4).getDefaultEntranceId(), "v3 draft is not silently repaired"); rejected(() -> loaded.getSpawnPosition(4), "v3 missing default fails at runtime");
        } finally { try (var files = Files.walk(dir)) { for (var p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); } }
    }
    private static void text(DataOutputStream out, String text, boolean wide) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8); if (wide) out.writeInt(bytes.length); else out.writeShort(bytes.length); out.write(bytes);
    }
}
