// phantasia-core/src/main/java/com/phantasia/core/data/ShopBaker.java
package com.phantasia.core.data;

import com.phantasia.core.data.QuestFlag;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Serializes and deserializes {@link ShopRegistry} contents to {@code shops.dat}.
 *
 * <h3>Binary format — version 1</h3>
 * <pre>
 *   Header:
 *     4 bytes  magic "SHOP"
 *     2 bytes  version = 1
 *     2 bytes  shop count
 *
 *   Per shop:
 *     2 bytes  shopId
 *     string   name
 *
 *     Entries:
 *       2 bytes  entry count
 *       Per entry:
 *         2 bytes  itemId
 *         2 bytes  quantity  (-1 = unlimited, stored as 0xFFFF)
 *         4 bytes  priceMultiplier (IEEE 754 float)
 *         1 byte   flags  bit0=hasRequiredFlag, bit1=oneTimePurchase
 *         if hasRequiredFlag: 1 byte QuestFlag.bit value
 *
 *   String encoding: 2-byte length (big-endian) + UTF-8 bytes.
 * </pre>
 */
public final class ShopBaker {

    private static final Logger LOG = Logger.getLogger(ShopBaker.class.getName());

    private static final byte[] MAGIC   = {'S', 'H', 'O', 'P'};
    private static final short  VERSION = 1;

    private static final int FLAG_HAS_REQUIRED    = 0x01;
    private static final int FLAG_ONE_TIME         = 0x02;

    private ShopBaker() {}

    // -------------------------------------------------------------------------
    // Bake (write)
    // -------------------------------------------------------------------------

    /**
     * Serializes all shops in the registry to {@code outPath}.
     */
    public static void bake(ShopRegistry registry, String outPath) throws IOException {
        File file = DataPaths.ensureParentDirs(outPath);
        Collection<ShopInventory> shops = registry.getAll();

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {

            out.write(MAGIC);
            out.writeShort(VERSION);
            out.writeShort(shops.size());

            for (ShopInventory shop : shops) {
                writeShop(out, shop);
            }
        }

        LOG.info("[ShopBaker] Baked " + shops.size() + " shops → " + outPath);
    }

    private static void writeShop(DataOutputStream out, ShopInventory shop)
            throws IOException {
        out.writeShort(shop.getShopId());
        writeString(out, shop.getName());

        List<ShopEntry> entries = shop.getEntries();
        out.writeShort(entries.size());
        for (ShopEntry entry : entries) {
            writeEntry(out, entry);
        }
    }

    private static void writeEntry(DataOutputStream out, ShopEntry entry)
            throws IOException {
        out.writeShort(entry.itemId());

        // quantity: -1 (unlimited) stored as 0xFFFF
        int qty = entry.quantity() < 0 ? 0xFFFF : entry.quantity();
        out.writeShort(qty);

        out.writeInt(Float.floatToIntBits(entry.priceMultiplier()));

        int flags = 0;
        if (entry.requiredFlag() != null) flags |= FLAG_HAS_REQUIRED;
        if (entry.oneTimePurchase())       flags |= FLAG_ONE_TIME;
        out.writeByte(flags);

        if (entry.requiredFlag() != null) {
            out.writeByte(entry.requiredFlag().bit);
        }
    }

    // -------------------------------------------------------------------------
    // Load (read)
    // -------------------------------------------------------------------------

    /**
     * Deserializes {@code shops.dat} into a {@link ShopRegistry}.
     * Returns an empty registry (not null) if the file is absent.
     *
     * @throws IOException if the file exists but cannot be parsed
     */
    public static ShopRegistry load(String path) throws IOException {
        ShopRegistry registry = new ShopRegistry();
        File file = new File(path);

        if (!file.exists()) {
            LOG.info("[ShopBaker] No shops.dat at " + file.getAbsolutePath()
                    + " — empty registry.");
            return registry;
        }

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {

            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IOException("Not a shops.dat file — bad magic.");
            }

            short version = in.readShort();
            if (version != VERSION) {
                throw new IOException("Unsupported shops.dat version: " + version);
            }

            int count = in.readShort() & 0xFFFF;
            for (int i = 0; i < count; i++) {
                registry.register(readShop(in));
            }
        }

        LOG.info("[ShopBaker] Loaded " + registry.size() + " shops from " + path);
        return registry;
    }

    private static ShopInventory readShop(DataInputStream in) throws IOException {
        int    shopId = in.readShort() & 0xFFFF;
        String name   = readString(in);

        ShopInventory shop = new ShopInventory(shopId, name);

        int entryCount = in.readShort() & 0xFFFF;
        for (int i = 0; i < entryCount; i++) {
            shop.addEntry(readEntry(in));
        }

        return shop;
    }

    private static ShopEntry readEntry(DataInputStream in) throws IOException {
        int   itemId    = in.readShort() & 0xFFFF;
        int   qty       = in.readShort() & 0xFFFF;
        int   quantity  = (qty == 0xFFFF) ? -1 : qty;
        float priceMult = Float.intBitsToFloat(readInt(in));
        int   flags     = in.readByte() & 0xFF;
        boolean hasFlag = (flags & FLAG_HAS_REQUIRED) != 0;
        boolean oneTime = (flags & FLAG_ONE_TIME)       != 0;

        QuestFlag reqFlag = null;
        if (hasFlag) {
            int bit = in.readByte() & 0xFF;
            for (QuestFlag f : QuestFlag.values()) {
                if (f.bit == bit) { reqFlag = f; break; }
            }
        }

        return new ShopEntry(itemId, quantity, priceMult, reqFlag, oneTime);
    }

    // -------------------------------------------------------------------------
    // I/O helpers
    // -------------------------------------------------------------------------

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int    len   = in.readShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readInt(DataInputStream in) throws IOException {
        return ((in.readByte() & 0xFF) << 24)
                | ((in.readByte() & 0xFF) << 16)
                | ((in.readByte() & 0xFF) <<  8)
                |  (in.readByte() & 0xFF);
    }
}
