// phantasia-core/src/main/java/com/phantasia/core/tools/ItemBaker.java
package com.phantasia.core.tools;

import com.phantasia.core.model.item.ItemDefinition;
import com.phantasia.core.model.item.ItemTable;

import java.io.*;

/**
 * Bakes the complete 180-item manifest into a binary items.dat file.
 *
 * RECORD LAYOUT (16 bytes):
 *   Byte  0    : item ID
 *   Byte  1    : ItemCategory ordinal
 *   Byte  2    : attack bonus
 *   Byte  3    : defense bonus
 *   Byte  4    : enchantment level
 *   Byte  5    : potion rank (0 on non-potions)
 *   Byte  6    : scroll content ID (0 on non-scrolls)
 *   Byte  7    : flags (bit 0 = quest item)
 *   Bytes 8–9  : gold value, 16-bit big-endian
 *   Bytes 10–11: job restriction bitmask, 16-bit big-endian
 *   Bytes 12–13: race restriction bitmask, 16-bit big-endian
 *   Bytes 14–15: reserved (zero-padded)
 */
public class ItemBaker {

    public static final int RECORD_SIZE = 16;

    public static void main(String[] args) throws IOException {
        String outPath = args.length > 0 ? args[0] : "items.dat";
        new ItemBaker().bake(outPath);
    }

    public void bake(String outPath) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(outPath, "rw")) {
            raf.setLength(0);

            for (int id = 1; id <= 180; id++) {
                if (!ItemTable.exists(id)) {
                    // Write a blank record to keep the file indexed correctly
                    raf.write(new byte[RECORD_SIZE]);
                    continue;
                }

                ItemDefinition item = ItemTable.get(id);
                byte[] record = new byte[RECORD_SIZE];

                record[0] = (byte) item.id();
                record[1] = (byte) item.category().ordinal();
                record[2] = (byte) item.attack();
                record[3] = (byte) item.defense();
                record[4] = (byte) item.enchant();
                record[5] = (byte) item.potionRank();
                record[6] = (byte) item.scrollId();     // scroll content ID; 0 on non-scrolls

                record[7] = (byte) (item.isQuestItem() ? 1 : 0);

                // Gold — 16-bit big-endian, clamped to 65535
                int gold = Math.min(item.gold(), 65535);
                record[8]  = (byte) (gold >> 8);
                record[9]  = (byte) (gold & 0xFF);

                // Restriction bitmasks — 16-bit big-endian
                record[10] = (byte) (item.jobRestriction() >> 8);
                record[11] = (byte) (item.jobRestriction() & 0xFF);
                record[12] = (byte) (item.raceRestriction() >> 8);
                record[13] = (byte) (item.raceRestriction() & 0xFF);

                // Bytes 14–15 reserved
                record[14] = 0;
                record[15] = 0;

                raf.write(record);

                System.out.printf("  [%3d] %-20s CAT:%-16s ATK:%2d DEF:%2d +%d GOLD:%d%n",
                        item.id(), item.name(), item.category(),
                        item.attack(), item.defense(),
                        item.enchant(), item.gold());
            }
        }

        long fileSize = new File(outPath).length();
        System.out.println("\nBaked " + (fileSize / RECORD_SIZE)
                + " item records into " + outPath
                + " (" + fileSize + " bytes)");
    }
}