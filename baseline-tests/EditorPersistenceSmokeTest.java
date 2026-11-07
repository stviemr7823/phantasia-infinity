package com.phantasia.editor;

import com.phantasia.core.data.*;
import com.phantasia.core.logic.Direction;
import com.phantasia.core.model.*;
import com.phantasia.core.world.*;
import com.phantasia.core.world.ServiceData.*;
import com.phantasia.editor.EditorState;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** No window, engine, Gradle or third-party test dependency required. */
public final class EditorPersistenceSmokeTest {
    private static int checks;
    private static final QuestFlag A = QuestFlag.values()[0], B = QuestFlag.values()[1];
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static void same(Object a, Object b, String message) {
        check(Objects.equals(a, b), message + ": " + a + " != " + b);
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("phantasia_persistence_test_");
        try {
            roundTrip(dir);
            legacy(dir);
            corrupt(dir);
            System.out.println("EDITOR_PERSISTENCE checks=" + checks + " PASS");
        } finally {
            try (var files = Files.walk(dir)) {
                for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }

    private static void roundTrip(Path dir) throws Exception {
        ShopInventory shop = new ShopInventory(42, "Smith — 鉄");
        shop.setRestockFlag(A);
        shop.addEntry(new ShopEntry(19, -1, 1.25f, B, true));
        InteriorMap map = new InteriorMap(70_000, "Hall — 北", InteriorMapType.DUNGEON, 5, 4, "stone");
        map.setTile(3, 2, 300); // v1 silently clamped this value
        var s = map.getSettings();
        s.setFogOfWar(false); s.setEncountersEnabled(true); s.setEncounterInterval(70_000);
        s.setAmbientLight(InteriorSettings.LightingMode.DARK); s.setTorchRadius(300);
        s.setMusicTrackId("quiet_hall"); map.setExplored(1, 1, true);
        Object[] payloads = {null, "Sign\n第二行", 42, InnData.standard(), GuildData.standard(),
                BankData.standard(), LockData.byFlag(A, "Closed"),
                new StairsData(71_000, 2, new WorldPosition(3, 1)),
                new StairsData(0, 0, null), ExitData.toEntryPoint(),
                new ExitData(new WorldPosition(8, 9)), TrapData.standard(),
                new LootTable(4, 9).rolls(3).addGuaranteed(5, 2).addRandom(8, 1, 7), shop};
        for (Object payload : payloads)
            map.addFeature(new PlacedFeature(2, 1, PlacedFeatureType.SIGN, payload, A, B, "custom"));
        map.addNpc(new PlacedNpc(111, 1, 2, Direction.WEST, A, B));
        NpcDefinition npc = new NpcDefinition(111, "Ada — keeper", NpcRole.MERCHANT, "")
                .portraitAssetId("portrait_ada").shopId(42)
                .animationProfile(AnimationProfile.wanderer(2.5f, 0.75f));
        npc.getDialogue().addNode(new DialogueNode(List.of("Welcome", "A second line"), Set.of(A), Set.of(B), B, 8));
        npc.getDialogue().addNode(new DialogueNode(List.of("Afterwards"), Set.of(), Set.of(), null, 0));
        EditorState state = new EditorState();
        state.putInteriorMap(map); state.putNpc(npc); state.putShop(shop);
        Path archive = dir.resolve("test.phantasia");
        state.saveProject(archive);
        EditorState fresh = new EditorState(); fresh.openProject(archive);
        verifyMap(map, fresh.getInteriorMap(map.getId()));
        NpcDefinition copy = fresh.getNpc(111);
        same(npc.getName(), copy.getName(), "NPC name"); same(npc.getRole(), copy.getRole(), "NPC role");
        same(npc.getShopId(), copy.getShopId(), "NPC service");
        same(npc.getSpriteAssetId(), copy.getSpriteAssetId(), "empty sprite preserved");
        same(npc.getPortraitAssetId(), copy.getPortraitAssetId(), "portrait");
        same(npc.getAnimationProfile(), copy.getAnimationProfile(), "animation");
        same(npc.getDialogue().getNodes(), copy.getDialogue().getNodes(), "ordered dialogue and flags");
        same(shop.getEntries(), fresh.getShop(42).getEntries(), "shop entries");
        same(A, fresh.getShop(42).getRestockFlag(), "shop restock");
        // Saving clears dirty state; exporting to a new folder must still export everything.
        Path baked = dir.resolve("baked"); fresh.bakeAll(baked);
        verifyMap(map, InteriorMapLoader.load(baked.resolve("interiors.dat").toString()).get(map.getId()));
        same(npc.getAnimationProfile(), NpcBaker.load(baked.resolve("npcs.dat").toString()).get(111).getAnimationProfile(), "runtime NPC load");
        EditorState loose = new EditorState(); loose.loadFromDirectory(baked);
        verifyMap(map, loose.getInteriorMap(map.getId()));
        same(A, ShopBaker.load(baked.resolve("shops.dat").toString()).get(42).getRestockFlag(), "runtime shop load");

        byte[] before = Files.readAllBytes(archive);
        map.addFeature(new PlacedFeature(0, 0, PlacedFeatureType.SIGN, new Object()));
        state.markDirty("interiorMap:" + map.getId());
        try { state.saveProject(archive); throw new AssertionError("unsupported payload accepted"); }
        catch (IOException expected) { check(true, "unsupported payload rejected"); }
        check(Arrays.equals(before, Files.readAllBytes(archive)), "failed save keeps original archive");
        check(state.getDirtyCount() > 0, "failed save keeps dirty state");
        // Resave to the same archive with all three collections emptied.
        fresh.removeInteriorMap(map.getId()); fresh.removeNpc(111); fresh.removeShop(42);
        fresh.saveProject();
        EditorState empty = new EditorState(); empty.openProject(archive);
        check(empty.getInteriorMaps().isEmpty() && empty.getNpcs().isEmpty() && empty.getShops().isEmpty(), "deleted content stays deleted");
        // Absent optional values stay absent, including an explicitly static/null animation.
        state = new EditorState(); state.putNpc(new NpcDefinition(3, "Static", NpcRole.INFORMANT, null).animationProfile(null));
        state.saveProject(dir.resolve("nulls.phantasia"));
        empty.openProject(dir.resolve("nulls.phantasia"));
        same(null, empty.getNpc(3).getAnimationProfile(), "null animation");
        same(null, empty.getNpc(3).getSpriteAssetId(), "null sprite");
    }

    private static void verifyMap(InteriorMap original, InteriorMap copy) {
        check(copy != null, "map exists");
        same(original.getId(), copy.getId(), "map ID"); same(original.getName(), copy.getName(), "map name");
        same(original.getMapType(), copy.getMapType(), "map type"); same(original.getTileSetId(), copy.getTileSetId(), "tile set");
        same(original.getWidth(), copy.getWidth(), "width"); same(original.getHeight(), copy.getHeight(), "height");
        for (int y = 0; y < original.getHeight(); y++)
            for (int x = 0; x < original.getWidth(); x++) same(original.getTile(x, y), copy.getTile(x, y), "tile");
        var a = original.getSettings(); var b = copy.getSettings();
        same(a.isFogOfWar(), b.isFogOfWar(), "fog"); same(a.isEncountersEnabled(), b.isEncountersEnabled(), "encounters");
        same(a.getEncounterInterval(), b.getEncounterInterval(), "interval"); same(a.getAmbientLight(), b.getAmbientLight(), "light");
        same(a.getTorchRadius(), b.getTorchRadius(), "torch"); same(a.getMusicTrackId(), b.getMusicTrackId(), "music");
        same(original.getPlacedNpcs(), copy.getPlacedNpcs(), "NPC placements and visibility");
        same(original.getFeatures().size(), copy.getFeatures().size(), "feature count");
        for (int i = 0; i < original.getFeatures().size(); i++) {
            var f = original.getFeatures().get(i); var g = copy.getFeatures().get(i);
            same(f.x(), g.x(), "feature x"); same(f.y(), g.y(), "feature y"); same(f.featureType(), g.featureType(), "feature type");
            same(f.requiredFlag(), g.requiredFlag(), "activation"); same(f.consumedFlag(), g.consumedFlag(), "consumption");
            same(f.assetId(), g.assetId(), "visual");
            if (f.serviceData() instanceof LootTable l) {
                LootTable m = (LootTable) g.serviceData();
                same(l.getGoldMin(), m.getGoldMin(), "gold min"); same(l.getGoldMax(), m.getGoldMax(), "gold max");
                same(l.getRollCount(), m.getRollCount(), "rolls"); same(l.getGuaranteed(), m.getGuaranteed(), "guaranteed loot"); same(l.getRandomPool(), m.getRandomPool(), "random loot");
            } else if (f.serviceData() instanceof ShopInventory l) {
                ShopInventory m = (ShopInventory) g.serviceData();
                same(l.getShopId(), m.getShopId(), "inline shop id"); same(l.getName(), m.getName(), "inline shop name");
                same(l.getEntries(), m.getEntries(), "inline inventory"); same(l.getRestockFlag(), m.getRestockFlag(), "inline restock");
            } else same(f.serviceData(), g.serviceData(), "typed payload");
        }
        check(copy.getExploredBits().isEmpty(), "campaign exploration excluded");
    }

    private static void text(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeShort(bytes.length); out.write(bytes);
    }
    private static void legacy(Path dir) throws Exception {
        Path path = dir.resolve("v1.dat");
        try (var out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeBytes("INTR"); out.writeShort(1); out.writeShort(1);
            out.writeShort(9); text(out, "Old room"); out.writeByte(0); text(out, "town");
            out.writeShort(1); out.writeShort(1); out.writeByte(7);
            out.writeByte(0); out.writeByte(0); out.writeShort(0); out.writeByte(0); out.writeByte(0);
            out.writeShort(1); out.writeShort(0); out.writeShort(0); out.writeByte(PlacedFeatureType.SHOP_COUNTER.ordinal()); out.writeShort(42);
            out.writeShort(1); out.writeShort(111); out.writeShort(0); out.writeShort(0); out.writeByte(Direction.WEST.ordinal());
        }
        var map = InteriorMapLoader.load(path.toString()).get(9);
        same(7, map.getTile(0, 0), "v1 tile"); same(42, map.getFeatures().get(0).serviceData(), "v1 shop reference");
        same(Direction.WEST, map.getPlacedNpcs().get(0).facing(), "v1 facing");
        try (var out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeBytes("NPCS"); out.writeShort(1); out.writeShort(1);
            out.writeShort(111); text(out, "Legacy NPC"); out.writeByte(NpcRole.INFORMANT.ordinal());
            out.writeShort(0); text(out, ""); text(out, ""); out.writeShort(0);
        }
        same(AnimationProfile.STATIC, NpcBaker.load(path.toString()).get(111).getAnimationProfile(), "v1 animation default");
        try (var out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeBytes("SHOP"); out.writeShort(1); out.writeShort(1);
            out.writeShort(42); text(out, "Legacy shop"); out.writeShort(0);
        }
        same(null, ShopBaker.load(path.toString()).get(42).getRestockFlag(), "v1 shop restock default");
    }

    private static void corrupt(Path dir) throws Exception {
        Path path = dir.resolve("bad.dat");
        for (byte[] bytes : List.of(new byte[]{'I','N','T','R',0,2,0,1},
                new byte[]{'I','N','T','R',0,99,0,0}, new byte[]{'I','N','T','R',0,2,0,0,7})) {
            Files.write(path, bytes);
            try { InteriorMapLoader.load(path.toString()); throw new AssertionError("corruption accepted"); }
            catch (IOException expected) { check(true, "bad file rejected"); }
        }
    }
}
