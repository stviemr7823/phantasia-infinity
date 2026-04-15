// phantasia-core/src/main/java/com/phantasia/core/data/SaveManager.java
package com.phantasia.core.data;

import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.world.WorldPosition;

import java.io.*;
import java.nio.file.*;

/**
 * Saves and loads the full game state to and from a binary save file.
 *
 * VERSION HISTORY:
 *
 *   Version 1 (original):
 *     Quest flags stored as a 4-byte int (32 flags).
 *     Offsets: quest flags at 0x12, party size at 0x16, roster at 0x18.
 *
 *   Version 2 (current):
 *     Quest flags expanded to an 8-byte long (64 flags).
 *     Offsets: quest flags at 0x12, party size at 0x1A, roster at 0x1C.
 *     All downstream offsets shifted by +4 bytes.
 *     V1 saves are migrated on load: 32-bit flags are zero-extended to 64-bit.
 *
 * FILE LAYOUT — VERSION 2 (all values big-endian):
 *
 *   Offset  Size  Field
 *   ------  ----  -----
 *   0x00     4    Magic bytes: 0x50 0x48 0x41 0x4E ("PHAN")
 *   0x04     2    Save version (currently 2)
 *   0x06     4    Party gold (int)
 *   0x0A     4    Bank gold (int)
 *   0x0E     2    World position X
 *   0x10     2    World position Y
 *   0x12     8    Quest flags (64 flags packed as a long)
 *   0x1A     2    Party size (1–6)
 *   0x1C     ?    Party roster: N × 48 bytes, one DataCore per character
 *
 * USAGE:
 *
 *   // Save:
 *   SaveManager.save(session, "data/GAME.DAT");
 *
 *   // Load (handles both v1 and v2 files automatically):
 *   GameSession session = SaveManager.load("data/GAME.DAT");
 *
 *   // Check if a save exists:
 *   if (SaveManager.saveExists("data/GAME.DAT")) { ... }
 *
 * THREAD SAFETY: not thread-safe — call from the game loop thread only.
 */
public final class SaveManager {

    private SaveManager() {}

    // -------------------------------------------------------------------------
    // File layout constants — Version 2
    // -------------------------------------------------------------------------

    private static final byte[] MAGIC = { 0x50, 0x48, 0x41, 0x4E }; // "PHAN"
    private static final short  CURRENT_VERSION = 2;

    // Shared offsets (same in v1 and v2)
    private static final int OFF_MAGIC       = 0x00;
    private static final int OFF_VERSION     = 0x04;
    private static final int OFF_PARTY_GOLD  = 0x06;
    private static final int OFF_BANK_GOLD   = 0x0A;
    private static final int OFF_WORLD_X     = 0x0E;
    private static final int OFF_WORLD_Y     = 0x10;
    private static final int OFF_QUEST_FLAGS = 0x12;  // same offset, different size

    // Version 2 offsets (quest flags = 8 bytes → downstream shifts by +4)
    private static final int V2_OFF_PARTY_SIZE = 0x1A;
    private static final int V2_OFF_ROSTER     = 0x1C;
    private static final int V2_FIXED_HEADER   = V2_OFF_ROSTER;  // 28 bytes

    // Version 1 offsets (quest flags = 4 bytes)
    private static final int V1_OFF_PARTY_SIZE = 0x16;
    private static final int V1_OFF_ROSTER     = 0x18;

    private static final int PARTY_MAX = 6;

    // -------------------------------------------------------------------------
    // Save (always writes current version)
    // -------------------------------------------------------------------------

    /**
     * Writes the full GameSession to the given path.
     * Creates parent directories if they don't exist.
     * Always writes in the current format (version 2).
     *
     * @throws IOException if the file cannot be written
     */
    public static void save(GameSession session, String path) throws IOException {
        DataPaths.ensureParentDirs(path);

        int partySize = Math.min(session.getParty().size(), PARTY_MAX);
        int fileSize  = V2_FIXED_HEADER + partySize * DataLayout.RECORD_SIZE;
        byte[] buf = new byte[fileSize];

        // --- Magic ---
        System.arraycopy(MAGIC, 0, buf, OFF_MAGIC, 4);

        // --- Version ---
        writeShort(buf, OFF_VERSION, CURRENT_VERSION);

        // --- Gold ---
        writeInt(buf, OFF_PARTY_GOLD, session.getLedger().getPartyGold());
        writeInt(buf, OFF_BANK_GOLD,  session.getLedger().getBankGold());

        // --- World position ---
        WorldPosition pos = session.getWorldPosition();
        writeShort(buf, OFF_WORLD_X, (short) pos.x());
        writeShort(buf, OFF_WORLD_Y, (short) pos.y());

        // --- Quest flags (64-bit) ---
        writeLong(buf, OFF_QUEST_FLAGS, session.getQuestFlags());

        // --- Party roster ---
        writeShort(buf, V2_OFF_PARTY_SIZE, (short) partySize);
        for (int i = 0; i < partySize; i++) {
            PlayerCharacter pc = session.getParty().get(i);
            byte[] record = pc.getDataCore().getRawBytes();
            System.arraycopy(record, 0, buf,
                    V2_OFF_ROSTER + i * DataLayout.RECORD_SIZE,
                    DataLayout.RECORD_SIZE);
        }

        Files.write(Path.of(path), buf);
        System.out.printf("[SaveManager] Game saved to %s (%d bytes, %d characters, v%d).%n",
                DataPaths.absolute(path), fileSize, partySize, CURRENT_VERSION);
    }

    // -------------------------------------------------------------------------
    // Load (handles v1 and v2 transparently)
    // -------------------------------------------------------------------------

    /**
     * Reads a save file and reconstructs a GameSession.
     * Supports both version 1 (32-bit flags) and version 2 (64-bit flags).
     * Version 1 saves are migrated by zero-extending the flags to 64 bits.
     *
     * @throws IOException              if the file cannot be read
     * @throws SaveFormatException      if the magic header is wrong or
     *                                  the version is unsupported
     */
    public static GameSession load(String path) throws IOException {
        byte[] buf = Files.readAllBytes(Path.of(path));

        // --- Validate magic ---
        for (int i = 0; i < 4; i++) {
            if (buf[OFF_MAGIC + i] != MAGIC[i]) {
                throw new SaveFormatException(
                        "Not a Phantasia save file: " + DataPaths.absolute(path));
            }
        }

        // --- Read version ---
        short version = readShort(buf, OFF_VERSION);

        // --- Gold (same offset in all versions) ---
        int partyGold = readInt(buf, OFF_PARTY_GOLD);
        int bankGold  = readInt(buf, OFF_BANK_GOLD);
        PartyLedger ledger = PartyLedger.fromRaw(partyGold, bankGold);

        // --- World position (same offset in all versions) ---
        int worldX = readShort(buf, OFF_WORLD_X);
        int worldY = readShort(buf, OFF_WORLD_Y);
        WorldPosition worldPos = new WorldPosition(worldX, worldY);

        // --- Version-specific parsing ---
        long questFlags;
        int  offPartySize;
        int  offRoster;

        switch (version) {
            case 1 -> {
                // V1: quest flags are a 4-byte int at 0x12
                // Zero-extend to 64 bits (upper 32 bits are 0)
                questFlags   = readInt(buf, OFF_QUEST_FLAGS) & 0xFFFFFFFFL;
                offPartySize = V1_OFF_PARTY_SIZE;
                offRoster    = V1_OFF_ROSTER;
                System.out.println("[SaveManager] Migrating v1 save → v2 "
                        + "(32-bit flags zero-extended to 64-bit).");
            }
            case 2 -> {
                // V2: quest flags are an 8-byte long at 0x12
                questFlags   = readLong(buf, OFF_QUEST_FLAGS);
                offPartySize = V2_OFF_PARTY_SIZE;
                offRoster    = V2_OFF_ROSTER;
            }
            default -> throw new SaveFormatException(
                    "Unsupported save version " + version
                            + " (supported: 1, 2). File: " + DataPaths.absolute(path));
        }

        // --- Party roster ---
        int partySize = readShort(buf, offPartySize);
        if (partySize < 0 || partySize > PARTY_MAX) {
            throw new SaveFormatException("Corrupt party size: " + partySize);
        }

        java.util.List<PlayerCharacter> party = new java.util.ArrayList<>(partySize);
        for (int i = 0; i < partySize; i++) {
            int offset = offRoster + i * DataLayout.RECORD_SIZE;
            byte[] record = new byte[DataLayout.RECORD_SIZE];
            System.arraycopy(buf, offset, record, 0, DataLayout.RECORD_SIZE);
            party.add(new PlayerCharacter(new DataCore(record)));
        }

        System.out.printf("[SaveManager] Loaded %d characters from %s (v%d).%n",
                partySize, DataPaths.absolute(path), version);

        return new GameSession(party, ledger, worldPos, questFlags);
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /** True if a save file exists at the given path. */
    public static boolean saveExists(String path) {
        return Files.exists(Path.of(path));
    }

    /**
     * Creates a brand-new GameSession with a fresh party and default starting values.
     * This is the "New Game" path — no file I/O.
     *
     * @param party     the newly-created 6-member party from character creation
     * @param startPos  the world start position (read from WorldMap.getStartPosition())
     */
    public static GameSession newGame(java.util.List<PlayerCharacter> party,
                                      WorldPosition startPos) {
        return new GameSession(party, PartyLedger.newGame(), startPos, 0L);
    }

    /**
     * Creates a GameSession from explicit values — used by JudgmentEngine
     * and other systems that reconstruct sessions from partial state.
     */
    public static GameSession withSurvivors(
            java.util.List<PlayerCharacter> survivors,
            PartyLedger                     ledger,
            WorldPosition                   position,
            long                            questFlags) {
        return new GameSession(survivors, ledger, position, questFlags);
    }

    // -------------------------------------------------------------------------
    // Binary helpers
    // -------------------------------------------------------------------------

    private static void writeShort(byte[] buf, int offset, short value) {
        buf[offset]     = (byte) ((value >> 8) & 0xFF);
        buf[offset + 1] = (byte) (value        & 0xFF);
    }

    private static void writeShort(byte[] buf, int offset, int value) {
        writeShort(buf, offset, (short) value);
    }

    private static void writeInt(byte[] buf, int offset, int value) {
        buf[offset]     = (byte) ((value >> 24) & 0xFF);
        buf[offset + 1] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 2] = (byte) ((value >>  8) & 0xFF);
        buf[offset + 3] = (byte) (value          & 0xFF);
    }

    private static void writeLong(byte[] buf, int offset, long value) {
        buf[offset]     = (byte) ((value >>> 56) & 0xFF);
        buf[offset + 1] = (byte) ((value >>> 48) & 0xFF);
        buf[offset + 2] = (byte) ((value >>> 40) & 0xFF);
        buf[offset + 3] = (byte) ((value >>> 32) & 0xFF);
        buf[offset + 4] = (byte) ((value >>> 24) & 0xFF);
        buf[offset + 5] = (byte) ((value >>> 16) & 0xFF);
        buf[offset + 6] = (byte) ((value >>>  8) & 0xFF);
        buf[offset + 7] = (byte) (value           & 0xFF);
    }

    private static short readShort(byte[] buf, int offset) {
        return (short) (((buf[offset] & 0xFF) << 8)
                | (buf[offset + 1] & 0xFF));
    }

    private static int readInt(byte[] buf, int offset) {
        return ((buf[offset]     & 0xFF) << 24)
                | ((buf[offset + 1] & 0xFF) << 16)
                | ((buf[offset + 2] & 0xFF) <<  8)
                |  (buf[offset + 3] & 0xFF);
    }

    private static long readLong(byte[] buf, int offset) {
        return ((long)(buf[offset]     & 0xFF) << 56)
                | ((long)(buf[offset + 1] & 0xFF) << 48)
                | ((long)(buf[offset + 2] & 0xFF) << 40)
                | ((long)(buf[offset + 3] & 0xFF) << 32)
                | ((long)(buf[offset + 4] & 0xFF) << 24)
                | ((long)(buf[offset + 5] & 0xFF) << 16)
                | ((long)(buf[offset + 6] & 0xFF) <<  8)
                |  (long)(buf[offset + 7] & 0xFF);
    }

    // -------------------------------------------------------------------------
    // Checked exception for bad save files
    // -------------------------------------------------------------------------

    public static class SaveFormatException extends IOException {
        public SaveFormatException(String message) {
            super(message);
        }
    }
}