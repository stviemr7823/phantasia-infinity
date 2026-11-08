package com.phantasia.core.data;

import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;
import com.phantasia.core.world.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.Predicate;

/** Versioned native checkpoints. No Java object deserialization and no legacy-save fallback. */
public final class NativeSaveManager {
    private NativeSaveManager() {}
    private static final int MAGIC = 0x504E5331, VERSION = 3;
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public record Checkpoint(String contentId, NativeCampaignV5.Snapshot campaign,
                             LocalMapTraversal.Snapshot visit, NativeLocalEncounterFlow.Snapshot encounters) {
        public Checkpoint {
            if (contentId == null || !contentId.matches("[0-9a-f]{64}") || (visit == null) != (encounters == null))
                throw new IllegalArgumentException("Invalid checkpoint content ID or visit pair");
            Objects.requireNonNull(campaign);
        }
    }
    public record Loaded(NativeCampaignV5 campaign, LocalMapTraversal traversal,
                         NativeLocalEncounterFlow.Snapshot encounters) {}
    public static Loaded restore(Checkpoint checkpoint, String expectedContent, InteriorMapRegistry maps,
                                  Predicate<WorldPosition> allowedWorld) {
        return restore(checkpoint, expectedContent, maps, new ShopRegistry(), allowedWorld);
    }
    public static Loaded restore(Checkpoint checkpoint, String expectedContent, InteriorMapRegistry maps,
                                  ShopRegistry shops, Predicate<WorldPosition> allowedWorld) {
        if (!checkpoint.contentId().equals(expectedContent)) throw new IllegalArgumentException("Save belongs to different authored content");
        var campaign = NativeCampaignV5.restore(checkpoint.campaign());
        campaign.shopState().validateCatalogs(key -> {
            try {
                var parts = key.split(":");
                if (parts[0].equals("shop")) return shops.get(Integer.parseInt(parts[1]));
                var map = maps.get(Integer.parseInt(parts[1]));
                var feature = map == null ? null : map.getFeatureAt(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                return feature != null && feature.featureType() == PlacedFeatureType.SHOP_COUNTER && feature.serviceData() instanceof ShopInventory inline ? inline : null;
            } catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid saved shop identity", ex); }
        });
        if (!allowedWorld.test(campaign.getWorldPosition())) throw new IllegalArgumentException("Invalid saved world position");
        campaign.getLocalInteractions().snapshot().forEach((key, loot) -> {
            var map = maps.get(key.mapId()); var feature = map == null ? null : map.getFeatureAt(key.x(), key.y());
            if (feature == null || feature.featureType() != PlacedFeatureType.CHEST || !(feature.serviceData() instanceof LootTable table)
                    || feature.consumedFlag() == null || campaign.hasFlag(feature.consumedFlag()) || !feature.isActive(campaign::hasFlag)
                    || loot.gold() < table.getGoldMin() || loot.gold() > table.getGoldMax())
                throw new IllegalArgumentException("Pending chest does not match authored content");
        });
        var traversal = checkpoint.visit() == null ? null : LocalMapTraversal.restore(maps, campaign, checkpoint.visit(), allowedWorld);
        if (traversal != null) NativeLocalEncounterFlow.restore(traversal, campaign, List::of, ignored -> 0, checkpoint.encounters(), maps);
        return new Loaded(campaign, traversal, checkpoint.encounters());
    }
    /** Atomic replacement only. Unsupported atomic moves fail with the previous file intact. */
    public static void save(Path path, Checkpoint checkpoint, String expectedContent, InteriorMapRegistry maps,
                            Predicate<WorldPosition> allowedWorld) throws IOException {
        save(path, checkpoint, expectedContent, maps, new ShopRegistry(), allowedWorld);
    }
    public static void save(Path path, Checkpoint checkpoint, String expectedContent, InteriorMapRegistry maps,
                            ShopRegistry shops, Predicate<WorldPosition> allowedWorld) throws IOException {
        try { restore(checkpoint, expectedContent, maps, shops, allowedWorld); }
        catch (IllegalArgumentException | IllegalStateException ex) { throw new IOException("Invalid native checkpoint: " + ex.getMessage(), ex); }
        byte[] bytes = encode(checkpoint);
        Path target = path.toAbsolutePath().normalize(), parent = target.getParent();
        if (!Files.isDirectory(parent)) throw new IOException("Save parent directory does not exist");
        Path pending = Files.createTempFile(parent, ".native-checkpoint-", ".tmp");
        try {
            try (var channel = FileChannel.open(pending, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
            }
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(pending); }
    }
    public static Loaded load(Path path, String expectedContent, InteriorMapRegistry maps,
                              Predicate<WorldPosition> allowedWorld) throws IOException {
        return load(path, expectedContent, maps, new ShopRegistry(), allowedWorld);
    }
    public static Loaded load(Path path, String expectedContent, InteriorMapRegistry maps,
                              ShopRegistry shops, Predicate<WorldPosition> allowedWorld) throws IOException {
        byte[] bytes;
        try (var in = Files.newInputStream(path)) { bytes = in.readNBytes(MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new IOException("Native save exceeds size limit");
        try { return restore(decode(bytes), expectedContent, maps, shops, allowedWorld); }
        catch (IllegalArgumentException | IllegalStateException ex) { throw new IOException("Invalid native save: " + ex.getMessage(), ex); }
    }
    private static byte[] hash(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static final class LimitedBytes extends ByteArrayOutputStream {
        private void reserve(int n) { if (n > MAX_BYTES - 44 - count) throw new IllegalArgumentException("Native save exceeds size limit"); }
        @Override public synchronized void write(int b) { reserve(1); super.write(b); }
        @Override public synchronized void write(byte[] b, int off, int len) { reserve(len); super.write(b, off, len); }
    }
    public static byte[] encode(Checkpoint saved) throws IOException {
        try {
            var payload = new LimitedBytes(); var out = new DataOutputStream(payload); out.writeUTF(saved.contentId());
            var c = saved.campaign(); out.writeInt(c.gold()); out.writeInt(c.bankGold()); out.writeLong(c.pendingGold()); out.writeInt(c.readySlots());
            out.writeLong(c.questFlags()); position(out, c.worldPosition());
            count(out, c.curriculum().size(), 1524);
            for (var step : c.curriculum()) {
                out.writeUTF(step.job().name()); out.writeInt(step.level()); out.writeLong(step.experience());
                out.writeInt(step.cost()); out.writeInt(step.hpGain()); out.writeInt(step.mpGain()); actions(out, step.learns());
            }
            count(out, c.members().size(), 6);
            for (var member : c.members()) {
                if (member.record().length != 64) throw new IOException("Invalid native record length");
                out.write(member.record()); actions(out, member.learned()); actions(out, member.readied());
            }
            count(out, c.pendingChests().size(), 10000);
            var chests = c.pendingChests().entrySet().stream().sorted(Comparator
                    .comparingInt((Map.Entry<LocalInteractionState.Key, LootTable.LootResult> e) -> e.getKey().mapId())
                    .thenComparingInt(e -> e.getKey().x()).thenComparingInt(e -> e.getKey().y())).toList();
            for (var chest : chests) {
                var key = chest.getKey(); out.writeInt(key.mapId()); out.writeInt(key.x()); out.writeInt(key.y());
                out.writeInt(chest.getValue().gold()); count(out, chest.getValue().items().size(), 10000);
                for (var item : chest.getValue().items()) { out.writeInt(item.itemId()); out.writeInt(item.quantity()); out.writeInt(item.weight()); }
            }
            count(out, c.shopPurchases().size(), 10000);
            for (var purchase : c.shopPurchases()) {
                out.writeUTF(purchase.catalog()); out.writeInt(purchase.row()); out.writeInt(purchase.purchased()); out.writeUTF(purchase.signature());
            }
            out.writeBoolean(saved.visit() != null);
            if (saved.visit() != null) {
                var v = saved.visit(); out.writeInt(v.mapId()); position(out, v.position()); out.writeUTF(v.facing().name());
                position(out, v.worldReturn()); out.writeLong(v.movementCount()); count(out, v.explored().size(), 10000);
                for (var id : new TreeSet<>(v.explored().keySet())) {
                    out.writeInt(id); var words = v.explored().get(id); count(out, words.size(), 131072);
                    for (var word : words) out.writeLong(word);
                }
                var p = saved.encounters(); out.writeLong(p.lastMovement()); count(out, p.paces().size(), 10000);
                for (var id : new TreeSet<>(p.paces().keySet())) { out.writeInt(id); out.writeInt(p.paces().get(id)); }
            }
            out.flush(); byte[] body = payload.toByteArray(); var envelope = new ByteArrayOutputStream(); var header = new DataOutputStream(envelope);
            header.writeInt(MAGIC); header.writeInt(VERSION); header.writeInt(body.length); header.write(body); header.write(hash(body));
            return envelope.toByteArray();
        } catch (IllegalArgumentException ex) { throw new IOException(ex.getMessage(), ex); }
    }
    public static Checkpoint decode(byte[] bytes) throws IOException {
        if (bytes.length < 44 || bytes.length > MAX_BYTES) throw new IOException("Invalid native save size");
        try {
            var header = new DataInputStream(new ByteArrayInputStream(bytes));
            if (header.readInt() != MAGIC) throw new IOException("Unsupported native save header");
            int version = header.readInt(); if (version < 1 || version > VERSION) throw new IOException("Unsupported native save version");
            int size = header.readInt(); if (size != bytes.length - 44) throw new IOException("Truncated native save or trailing bytes");
            byte[] body = header.readNBytes(size), digest = header.readNBytes(32);
            if (!MessageDigest.isEqual(hash(body), digest)) throw new IOException("Native save checksum mismatch");
            var in = new DataInputStream(new ByteArrayInputStream(body)); String content = text(in);
            int gold = in.readInt(), bank = version >= 2 ? in.readInt() : 0; long pendingGold = in.readLong(); int slots = in.readInt(); long flags = in.readLong(); var world = position(in);
            int steps = count(in, 1524); var curriculum = new ArrayList<NativeCampaignV5.TrainingStep>();
            for (int i = 0; i < steps; i++) curriculum.add(new NativeCampaignV5.TrainingStep(value(Job.class, text(in)), in.readInt(), in.readLong(), in.readInt(), in.readInt(), in.readInt(), actions(in)));
            int players = count(in, 6); var members = new ArrayList<NativeCampaignV5.MemberSnapshot>();
            for (int i = 0; i < players; i++) {
                byte[] record = new byte[64]; in.readFully(record); members.add(new NativeCampaignV5.MemberSnapshot(record, actions(in), actions(in)));
            }
            int chestCount = count(in, 10000); var chests = new HashMap<LocalInteractionState.Key, LootTable.LootResult>();
            for (int i = 0; i < chestCount; i++) {
                var key = new LocalInteractionState.Key(in.readInt(), in.readInt(), in.readInt()); int rewardGold = in.readInt(), items = count(in, 10000);
                var loot = new ArrayList<LootTable.LootEntry>();
                for (int j = 0; j < items; j++) loot.add(new LootTable.LootEntry(in.readInt(), in.readInt(), in.readInt()));
                if (chests.put(key, new LootTable.LootResult(rewardGold, List.copyOf(loot))) != null) throw new IOException("Duplicate pending chest");
            }
            var purchases = new ArrayList<NativeShopState.Purchase>();
            if (version >= 3) {
                int n = count(in, 10000);
                for (int i = 0; i < n; i++) purchases.add(new NativeShopState.Purchase(text(in), in.readInt(), in.readInt(), text(in)));
            }
            LocalMapTraversal.Snapshot visit = null; NativeLocalEncounterFlow.Snapshot pace = null;
            if (in.readBoolean()) {
                int map = in.readInt(); var pos = position(in); var facing = value(Direction.class, text(in)); var anchor = position(in); long movement = in.readLong();
                int seen = count(in, 10000); var explored = new HashMap<Integer, List<Long>>();
                for (int i = 0; i < seen; i++) {
                    int id = in.readInt(), n = count(in, 131072); var words = new ArrayList<Long>();
                    for (int j = 0; j < n; j++) words.add(in.readLong());
                    if (explored.put(id, List.copyOf(words)) != null) throw new IOException("Duplicate explored map");
                }
                visit = new LocalMapTraversal.Snapshot(map, pos, facing, anchor, movement, explored);
                long last = in.readLong(); int counters = count(in, 10000); var paces = new HashMap<Integer, Integer>();
                for (int i = 0; i < counters; i++) if (paces.put(in.readInt(), in.readInt()) != null) throw new IOException("Duplicate pace counter");
                pace = new NativeLocalEncounterFlow.Snapshot(paces, last);
            }
            if (in.available() != 0) throw new IOException("Trailing native payload");
            return new Checkpoint(content, new NativeCampaignV5.Snapshot(members, gold, bank, pendingGold, slots, curriculum, flags, world, chests, purchases), visit, pace);
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid native save value: " + ex.getMessage(), ex); }
    }
    private static void count(DataOutput out, int n, int max) throws IOException { if (n < 0 || n > max) throw new IOException("Save count outside limit"); out.writeInt(n); }
    private static int count(DataInput in, int max) throws IOException { int n = in.readInt(); if (n < 0 || n > max) throw new IOException("Save count outside limit"); return n; }
    private static void position(DataOutput out, WorldPosition p) throws IOException { out.writeInt(p.x()); out.writeInt(p.y()); }
    private static WorldPosition position(DataInput in) throws IOException { return new WorldPosition(in.readInt(), in.readInt()); }
    private static String text(DataInput in) throws IOException { String text = in.readUTF(); if (text.length() > 128) throw new IOException("Save identifier too long"); return text; }
    private static <E extends Enum<E>> E value(Class<E> type, String name) throws IOException {
        try { return Enum.valueOf(type, name); } catch (IllegalArgumentException ex) { throw new IOException("Unknown save enum: " + name, ex); }
    }
    private static void actions(DataOutput out, List<Action> actions) throws IOException { count(out, actions.size(), 4); for (var action : actions) out.writeUTF(action.name()); }
    private static List<Action> actions(DataInput in) throws IOException { int n = count(in, 4); var actions = new ArrayList<Action>(); for (int i = 0; i < n; i++) actions.add(value(Action.class, text(in))); return List.copyOf(actions); }
}
