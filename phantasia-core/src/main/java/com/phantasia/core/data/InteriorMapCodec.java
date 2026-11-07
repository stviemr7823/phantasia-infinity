package com.phantasia.core.data;

import com.phantasia.core.logic.Direction;
import com.phantasia.core.model.PlacedNpc;
import com.phantasia.core.world.*;
import com.phantasia.core.world.ServiceData.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Version 2 interior records. Enum names and explicit payload tags are wire IDs.
 * Authored data only: exploration and campaign progress are never written here.
 */
final class InteriorMapCodec {
    private InteriorMapCodec() {}
    static final int MAX_COUNT = 1_000_000;

    static void string(DataOutput out, String value) throws IOException {
        if (value == null) { out.writeInt(-1); return; }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_COUNT) throw new IOException("String exceeds format limit");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    static String string(DataInput in) throws IOException {
        int size = in.readInt();
        if (size == -1) return null;
        size = count(size);
        byte[] bytes = new byte[size];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static int count(int value) throws IOException {
        if (value < 0 || value > MAX_COUNT) throw new IOException("Invalid record size: " + value);
        return value;
    }

    static <E extends Enum<E>> E value(Class<E> type, String name) throws IOException {
        try { return Enum.valueOf(type, name); }
        catch (IllegalArgumentException | NullPointerException e) {
            throw new IOException("Unknown " + type.getSimpleName() + ": " + name, e);
        }
    }

    static void flag(DataOutput out, QuestFlag flag) throws IOException {
        string(out, flag == null ? null : flag.name());
    }

    static QuestFlag flag(DataInput in) throws IOException {
        String name = string(in);
        return name == null ? null : value(QuestFlag.class, name);
    }

    private static void position(DataOutput out, WorldPosition p) throws IOException {
        out.writeBoolean(p != null);
        if (p != null) { out.writeInt(p.x()); out.writeInt(p.y()); }
    }

    private static WorldPosition position(DataInput in) throws IOException {
        return in.readBoolean() ? new WorldPosition(in.readInt(), in.readInt()) : null;
    }

    static void write(DataOutput out, InteriorMap map) throws IOException {
        if (map.getWidth() <= 0 || map.getHeight() <= 0
                || (long) map.getWidth() * map.getHeight() > MAX_COUNT)
            throw new IOException("Invalid interior dimensions");
        out.writeInt(map.getId());
        string(out, map.getName()); string(out, map.getMapType().name());
        string(out, map.getTileSetId());
        out.writeInt(map.getWidth()); out.writeInt(map.getHeight());
        for (int y = 0; y < map.getHeight(); y++)
            for (int x = 0; x < map.getWidth(); x++) out.writeInt(map.getTile(x, y));
        InteriorSettings s = map.getSettings();
        out.writeBoolean(s.isFogOfWar()); out.writeBoolean(s.isEncountersEnabled());
        out.writeInt(s.getEncounterInterval()); string(out, s.getAmbientLight().name());
        out.writeInt(s.getTorchRadius()); string(out, s.getMusicTrackId());
        out.writeInt(count(map.getFeatures().size()));
        for (PlacedFeature f : map.getFeatures()) {
            out.writeInt(f.x()); out.writeInt(f.y()); string(out, f.featureType().name());
            payload(out, f.serviceData()); flag(out, f.requiredFlag());
            flag(out, f.consumedFlag()); string(out, f.assetId());
        }
        out.writeInt(count(map.getPlacedNpcs().size()));
        for (PlacedNpc n : map.getPlacedNpcs()) {
            out.writeInt(n.npcId()); out.writeInt(n.x()); out.writeInt(n.y());
            string(out, n.facing().name()); flag(out, n.appearCondition());
            flag(out, n.disappearCondition());
        }
    }

    static InteriorMap read(DataInput in) throws IOException {
        int id = in.readInt(); String name = string(in);
        InteriorMapType type = value(InteriorMapType.class, string(in));
        String tiles = string(in); int w = count(in.readInt()), h = count(in.readInt());
        if (w == 0 || h == 0 || (long) w * h > MAX_COUNT)
            throw new IOException("Invalid interior dimensions");
        InteriorMap map = new InteriorMap(id, name, type, w, h, tiles);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) map.setTile(x, y, in.readInt());
        InteriorSettings s = map.getSettings();
        s.setFogOfWar(in.readBoolean()); s.setEncountersEnabled(in.readBoolean());
        s.setEncounterInterval(in.readInt());
        s.setAmbientLight(value(InteriorSettings.LightingMode.class, string(in)));
        s.setTorchRadius(in.readInt()); s.setMusicTrackId(string(in));
        int count = count(in.readInt());
        for (int i = 0; i < count; i++) {
            int x = in.readInt(), y = in.readInt();
            PlacedFeatureType ft = value(PlacedFeatureType.class, string(in));
            map.addFeature(new PlacedFeature(x, y, ft, payload(in), flag(in), flag(in), string(in)));
        }
        count = count(in.readInt());
        for (int i = 0; i < count; i++) {
            int npc = in.readInt(), x = in.readInt(), y = in.readInt();
            map.addNpc(new PlacedNpc(npc, x, y, value(Direction.class, string(in)), flag(in), flag(in)));
        }
        return map;
    }

    private static void payload(DataOutput out, Object data) throws IOException {
        if (data == null) { string(out, "none"); }
        else if (data instanceof String s) { string(out, "text"); string(out, s); }
        else if (data instanceof Integer i) { string(out, "reference"); out.writeInt(i); }
        else if (data instanceof InnData d) {
            string(out, "inn"); out.writeInt(d.costPerHp()); string(out, d.greeting()); out.writeBoolean(d.restoredDead());
        } else if (data instanceof GuildData d) {
            string(out, "guild"); out.writeInt(d.trainingCostBase()); out.writeInt(d.trainingCostScale()); out.writeInt(d.maxLevel());
        } else if (data instanceof BankData d) {
            string(out, "bank"); out.writeInt(d.depositFeePercent()); out.writeInt(d.withdrawalFeePercent());
        } else if (data instanceof LockData d) {
            string(out, "lock"); out.writeInt(d.requiredItemId()); flag(out, d.requiredFlag()); string(out, d.lockedMessage());
        } else if (data instanceof StairsData d) {
            string(out, "stairs"); out.writeInt(d.targetMapId()); out.writeInt(d.targetFloor()); position(out, d.targetPosition());
        } else if (data instanceof ExitData d) {
            string(out, "exit"); position(out, d.returnPosition());
        } else if (data instanceof TrapData d) {
            string(out, "trap"); out.writeInt(d.damageMin()); out.writeInt(d.damageMax()); string(out, d.message()); out.writeInt(d.detectSkill());
        } else if (data instanceof LootTable d) {
            string(out, "loot"); out.writeInt(d.getGoldMin()); out.writeInt(d.getGoldMax()); out.writeInt(d.getRollCount());
            out.writeInt(count(d.getGuaranteed().size()));
            for (var e : d.getGuaranteed()) { out.writeInt(e.itemId()); out.writeInt(e.quantity()); }
            out.writeInt(count(d.getRandomPool().size()));
            for (var e : d.getRandomPool()) { out.writeInt(e.itemId()); out.writeInt(e.quantity()); out.writeInt(e.weight()); }
        } else if (data instanceof ShopInventory d) {
            string(out, "shop"); out.writeInt(d.getShopId()); string(out, d.getName()); flag(out, d.getRestockFlag());
            out.writeInt(count(d.getEntries().size()));
            for (var e : d.getEntries()) {
                out.writeInt(e.itemId()); out.writeInt(e.quantity()); out.writeFloat(e.priceMultiplier());
                flag(out, e.requiredFlag()); out.writeBoolean(e.oneTimePurchase());
            }
        } else throw new IOException("Unsupported interior payload: " + data.getClass().getName());
    }

    private static Object payload(DataInput in) throws IOException {
        String tag = string(in);
        if (tag == null) throw new IOException("Missing payload tag");
        return switch (tag) {
            case "none" -> null;
            case "text" -> string(in);
            case "reference" -> in.readInt();
            case "inn" -> new InnData(in.readInt(), string(in), in.readBoolean());
            case "guild" -> new GuildData(in.readInt(), in.readInt(), in.readInt());
            case "bank" -> new BankData(in.readInt(), in.readInt());
            case "lock" -> new LockData(in.readInt(), flag(in), string(in));
            case "stairs" -> new StairsData(in.readInt(), in.readInt(), position(in));
            case "exit" -> new ExitData(position(in));
            case "trap" -> new TrapData(in.readInt(), in.readInt(), string(in), in.readInt());
            case "loot" -> readLoot(in);
            case "shop" -> readShop(in);
            default -> throw new IOException("Unknown interior payload tag: " + tag);
        };
    }

    private static LootTable readLoot(DataInput in) throws IOException {
        LootTable loot = new LootTable(in.readInt(), in.readInt()).rolls(in.readInt());
        int n = count(in.readInt());
        for (int i = 0; i < n; i++) loot.addGuaranteed(in.readInt(), in.readInt());
        n = count(in.readInt());
        for (int i = 0; i < n; i++) loot.addRandom(in.readInt(), in.readInt(), in.readInt());
        return loot;
    }

    private static ShopInventory readShop(DataInput in) throws IOException {
        ShopInventory shop = new ShopInventory(in.readInt(), string(in));
        shop.setRestockFlag(flag(in));
        int n = count(in.readInt());
        for (int i = 0; i < n; i++)
            shop.addEntry(new ShopEntry(in.readInt(), in.readInt(), in.readFloat(), flag(in), in.readBoolean()));
        return shop;
    }
}
