package com.phantasia.core.tools;

import com.phantasia.core.data.DataPaths;
import com.phantasia.core.model.DataLayout;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Packs a monsters.csv source file into the binary monsters.dat bestiary.
 *
 * Paths are resolved via DataPaths — never hardcoded:
 *   Input  : DataPaths.MONSTERS_CSV  (data/csv/monsters.csv by default)
 *   Output : DataPaths.MONSTERS_DAT  (data/monsters.dat by default)
 *
 * Override at runtime:
 *   -Dphantasia.data.dir=<path>   redirects .dat output directory
 *   -Dphantasia.csv.dir=<path>    redirects .csv input directory
 *
 * Or pass explicit paths as program arguments:
 *   MonsterPacker <input.csv> <output.dat>
 *
 * CSV format (comma-delimited, one header row):
 *   Name, MAX, HP, ITM1, ITM2, Undead(U/N), Treasure, XP
 *
 * Binary record layout — 48 bytes per monster (DataLayout offsets):
 *   0x00–0x0E  Name        (15-byte ASCII, null-padded)
 *   0x0F       MON_MAX_SPAWN
 *   0x10       MON_HP
 *   0x11       MON_ITEM_1
 *   0x12       MON_ITEM_2
 *   0x13       MON_FLAGS   (bit 0 = MON_FLAG_UNDEAD)
 *   0x14–0x15  MON_TREASURE (16-bit big-endian)
 *   0x16–0x17  MON_XP      (16-bit big-endian)
 */
public class MonsterPacker {

    public static void main(String[] args) {
        String csvFile = (args.length > 0) ? args[0] : DataPaths.MONSTERS_CSV;
        String datFile = (args.length > 1) ? args[1] : DataPaths.MONSTERS_DAT;

        new MonsterPacker().pack(csvFile, datFile);
    }

    public void pack(String csvPath, String datPath) {
        // Ensure the output directory exists before writing
        File outFile = DataPaths.ensureParentDirs(datPath);

        System.out.println(">>> [PACKER] Reading CSV : " + DataPaths.absolute(csvPath));
        System.out.println(">>> [PACKER] Writing DAT : " + DataPaths.absolute(datPath));
        System.out.println(">>> [PACKER] STARTING BINARY ENCODING...");

        int count = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath));
             FileOutputStream fos = new FileOutputStream(outFile)) {

            br.readLine(); // Skip header row

            String line;
            while ((line = br.readLine()) != null) {
                String[] cols = line.split(",");
                if (cols.length < 8) {
                    System.err.println("    [SKIP] Malformed row (expected 8 columns): " + line);
                    continue;
                }

                byte[] record = new byte[DataLayout.RECORD_SIZE]; // 48-byte soul

                // 1. Name — packed into the shared name block (offsets 0x00–0x0E)
                byte[] nameBytes = cols[0].trim().getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(nameBytes, 0, record,
                        DataLayout.NAME, Math.min(nameBytes.length, DataLayout.NAME_LEN));

                // 2. Stats — single bytes at canonical DataLayout offsets
                record[DataLayout.MON_MAX_SPAWN] = (byte) Integer.parseInt(cols[1].trim());
                record[DataLayout.MON_HP]        = (byte) Integer.parseInt(cols[2].trim());
                record[DataLayout.MON_ITEM_1]    = (byte) Integer.parseInt(cols[3].trim());
                record[DataLayout.MON_ITEM_2]    = (byte) Integer.parseInt(cols[4].trim());

                // 3. Flags — bit 0 = undead (MON_FLAG_UNDEAD = 0x01)
                boolean undead = cols[5].trim().equalsIgnoreCase("U");
                record[DataLayout.MON_FLAGS] =
                        (byte) (undead ? DataLayout.MON_FLAG_UNDEAD : 0);

                // 4. Treasure — 16-bit big-endian at MON_TREASURE (0x14–0x15)
                int treasure = Integer.parseInt(cols[6].trim());
                record[DataLayout.MON_TREASURE]     = (byte) (treasure >> 8);
                record[DataLayout.MON_TREASURE + 1] = (byte) (treasure & 0xFF);

                // 5. XP — 16-bit big-endian at MON_XP (0x16–0x17)
                int xp = Integer.parseInt(cols[7].trim());
                record[DataLayout.MON_XP]     = (byte) (xp >> 8);
                record[DataLayout.MON_XP + 1] = (byte) (xp & 0xFF);

                fos.write(record);
                count++;
                System.out.println("    [PACKED] " + cols[0].trim());
            }

        } catch (IOException e) {
            System.err.println("[CRITICAL ERROR] Packer I/O failed: " + e.getMessage());
            return;
        } catch (NumberFormatException e) {
            System.err.println("[CRITICAL ERROR] Packer parse failed: " + e.getMessage());
            return;
        }

        System.out.println(">>> [SUCCESS] " + datPath + " created with " + count + " records.");
    }
}