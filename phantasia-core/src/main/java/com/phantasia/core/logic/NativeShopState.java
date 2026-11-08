package com.phantasia.core.logic;

import com.phantasia.core.data.*;
import com.phantasia.core.model.item.ItemTable;
import java.util.*;
import java.util.function.*;
import java.security.*;
import java.nio.charset.StandardCharsets;

/** Campaign-owned purchase counts. Authored listings are never depleted in-place. */
public final class NativeShopState {
    public record Key(String catalog, int row) {}
    public record Purchase(String catalog, int row, int purchased, String signature) {}
    public record Quote(int price, int remaining, boolean visible, boolean priceInRange) {}
    private final Map<Key, Purchase> purchases = new HashMap<>();
    public static String registered(ShopInventory shop) { return "shop:" + shop.getShopId(); }
    public static String inline(int map, int x, int y) { return "map:" + map + ":" + x + ":" + y; }
    private static void key(String catalog, int row) {
        if (catalog == null || !catalog.matches("(?:shop:[0-9]+|map:[0-9]+:[0-9]+:[0-9]+)")
                || catalog.length() > 128 || row < 0 || row > 65535) throw new IllegalArgumentException("Invalid shop listing identity");
        for (var number : catalog.substring(catalog.indexOf(':') + 1).split(":"))
            if (!Integer.toString(Integer.parseInt(number)).equals(number)) throw new IllegalArgumentException("Noncanonical shop identity");
    }
    private static String signature(ShopEntry entry) {
        String value = entry.itemId() + ":" + entry.quantity() + ":" + Float.floatToIntBits(entry.priceMultiplier())
                + ":" + (entry.requiredFlag() == null ? -1 : entry.requiredFlag().bit) + ":" + entry.oneTimePurchase();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public Quote quote(String catalog, ShopInventory shop, int row, Predicate<QuestFlag> flags) {
        key(catalog, row); Objects.requireNonNull(shop); Objects.requireNonNull(flags);
        if (catalog.startsWith("shop:") && !catalog.equals(registered(shop))) throw new IllegalArgumentException("Shop ID does not match catalog");
        if (row >= shop.getEntries().size()) throw new IllegalArgumentException("Unknown shop row");
        var entry = shop.getEntries().get(row);
        if (!ItemTable.exists(entry.itemId()) || entry.quantity() < -1 || entry.quantity() > 65534
                || !Float.isFinite(entry.priceMultiplier()) || entry.priceMultiplier() <= 0)
            throw new IllegalArgumentException("Invalid authored shop listing");
        double price = (double) ItemTable.get(entry.itemId()).gold() * entry.priceMultiplier();
        boolean priceInRange = Double.isFinite(price) && price <= Integer.MAX_VALUE;
        var previous = purchases.get(new Key(catalog, row)); int sold = previous == null ? 0 : previous.purchased();
        if (previous != null && !previous.signature().equals(signature(entry))) throw new IllegalArgumentException("Shop listing changed since purchase");
        int limit = entry.oneTimePurchase() ? (entry.quantity() == 0 ? 0 : 1) : entry.quantity();
        int remaining = limit == -1 ? -1 : Math.max(0, limit - sold);
        return new Quote(Math.max(1, (int) price), remaining,
                (entry.requiredFlag() == null || flags.test(entry.requiredFlag())) && !(entry.oneTimePurchase() && sold > 0), priceInRange);
    }
    void record(String catalog, ShopInventory shop, int row) {
        var quote = quote(catalog, shop, row, ignored -> true);
        if (quote.remaining() == 0 || !quote.visible()) throw new IllegalStateException("This listing is sold out");
        var entry = shop.getEntries().get(row); if (entry.quantity() == -1 && !entry.oneTimePurchase()) return;
        var key = new Key(catalog, row); var previous = purchases.get(key);
        if (previous == null && purchases.size() >= 10000) throw new IllegalStateException("Campaign shop-state limit reached");
        purchases.put(key, new Purchase(catalog, row, previous == null ? 1 : previous.purchased() + 1, signature(entry)));
    }
    public List<Purchase> snapshot() { return purchases.values().stream().sorted(Comparator.comparing(Purchase::catalog).thenComparingInt(Purchase::row)).toList(); }
    public static NativeShopState restore(List<Purchase> records) {
        if (records.size() > 10000) throw new IllegalArgumentException("Too many shop records");
        var state = new NativeShopState();
        for (var record : records) {
            key(record.catalog(), record.row());
            if (record.purchased() < 1 || record.purchased() > 65534 || record.signature() == null || !record.signature().matches("[0-9a-f]{64}")
                    || state.purchases.put(new Key(record.catalog(), record.row()), record) != null)
                throw new IllegalArgumentException("Invalid or duplicate shop purchase record");
        }
        return state;
    }
    public void validateCatalogs(Function<String, ShopInventory> resolve) {
        for (var record : snapshot()) {
            var shop = resolve.apply(record.catalog());
            if (shop == null || record.row() >= shop.getEntries().size()) throw new IllegalArgumentException("Saved shop catalog no longer exists");
            var entry = shop.getEntries().get(record.row());
            if (!quote(record.catalog(), shop, record.row(), ignored -> true).priceInRange()) throw new IllegalArgumentException("Saved purchase has invalid price");
            int limit = entry.oneTimePurchase() ? (entry.quantity() == 0 ? 0 : 1) : entry.quantity();
            if (limit < 0 || record.purchased() > limit) throw new IllegalArgumentException("Invalid saved shop purchase count");
        }
    }
}
