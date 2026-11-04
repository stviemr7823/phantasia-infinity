// phantasia-core/src/main/java/com/phantasia/core/data/SaveManager.java
package com.phantasia.core.data;

import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.world.WorldPosition;

import java.io.*;
import java.nio.file.*;
import java.util.List;

/**
 * Saves and loads the full game state to and from a binary save file.
 *
 * <h3>Version history</h3>
 * <pre>
 *   v1  Quest flags 4-byte int (32 flags). DataCore 48 bytes.
 *   v2  Quest flags 8-byte long (64 flags). DataCore 48 bytes.
 *   v3  WorldClock time added (4-byte float). DataCore 48 bytes.
 *   v4  DataCore expanded 48→64 bytes (cache-line alignment). Current.
 * </pre>
 *
 * <h3>Migration</h3>
 * v1–v3 saves carry 48-byte DataCore records. On load they are zero-padded
 * to 64 bytes. The expansion zone (bytes 48–63) reads as zero, which is the
 * correct default for every expansion field (portrait=0, traits=0,
 * resonance=0, extended inventory empty).
 *
 * <h3>File layout — v4 (all values big-endian)</h3>
 * <pre>
 *   Offset  Size  Field
 *   ------  ----  -----
 *   0x00     4    Magic "PHAN"
 *   0x04     2    Version (4)
 *   0x06     4    Party gold
 *   0x0A     4    Bank gold
 *   0x0E     2    World X
 *   0x10     2    World Y
 *   0x12     8    Quest flags (64-bit long)
 *   0x1A     4    WorldClock normalised time (IEEE 754 float)
 *   0x1E     2    Party size
 *   0x20     ?    Roster: N × 64 bytes
 * </pre>
 */
public final class SaveManager {

    private SaveManager() {}

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final byte[] MAGIC           = { 0x50, 0x48, 0x41, 0x4E };
    private static final short  CURRENT_VERSION = 4;

    private static final int OFF_MAGIC       = 0x00;
    private static final int OFF_VERSION     = 0x04;
    private static final int OFF_PARTY_GOLD  = 0x06;
    private static final int OFF_BANK_GOLD   = 0x0A;
    private static final int OFF_WORLD_X     = 0x0E;
    private static final int OFF_WORLD_Y     = 0x10;
    private static final int OFF_QUEST_FLAGS = 0x12;

    private static final int V1_OFF_PARTY_SIZE = 0x16; private static final int V1_OFF_ROSTER = 0x18; private static final int V1_RECORD = 48;
    private static final int V2_OFF_PARTY_SIZE = 0x1A; private static final int V2_OFF_ROSTER = 0x1C; private static final int V2_RECORD = 48;
    private static final int V3_OFF_CLOCK      = 0x1A; private static final int V3_OFF_PARTY_SIZE = 0x1E; private static final int V3_OFF_ROSTER = 0x20; private static final int V3_RECORD = 48;
    private static final int V4_OFF_CLOCK      = 0x1A; private static final int V4_OFF_PARTY_SIZE = 0x1E; private static final int V4_OFF_ROSTER = 0x20; private static final int V4_RECORD = 64;
    private static final int V4_FIXED_HEADER   = V4_OFF_ROSTER; // 32 bytes

    private static final int   PARTY_MAX     = 6;
    private static final float DEFAULT_CLOCK = 0.292f;

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    public static void save(GameSession session, String path) throws IOException {
        DataPaths.ensureParentDirs(path);
        int partySize = Math.min(session.getParty().size(), PARTY_MAX);
        int fileSize  = V4_FIXED_HEADER + partySize * V4_RECORD;
        byte[] buf    = new byte[fileSize];

        System.arraycopy(MAGIC, 0, buf, OFF_MAGIC, 4);
        writeShort(buf, OFF_VERSION,    CURRENT_VERSION);
        writeInt  (buf, OFF_PARTY_GOLD, session.getLedger().getPartyGold());
        writeInt  (buf, OFF_BANK_GOLD,  session.getLedger().getBankGold());

        WorldPosition pos = session.getWorldPosition();
        writeShort(buf, OFF_WORLD_X, pos.x());
        writeShort(buf, OFF_WORLD_Y, pos.y());

        writeLong (buf, OFF_QUEST_FLAGS, session.getQuestFlags());
        writeFloat(buf, V4_OFF_CLOCK,
                (float) session.getWorldClock().getNormalisedTime());

        writeShort(buf, V4_OFF_PARTY_SIZE, partySize);
        for (int i = 0; i < partySize; i++) {
            byte[] rec = session.getParty().get(i).getDataCore().getRawBytes();
            System.arraycopy(rec, 0, buf, V4_OFF_ROSTER + i * V4_RECORD, V4_RECORD);
        }

        Files.write(Path.of(path), buf);
        System.out.printf("[SaveManager] Saved → %s  (%d bytes, %d chars, v4, 64-byte records).%n",
                DataPaths.absolute(path), fileSize, partySize);
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    public static GameSession load(String path) throws IOException {
        byte[] buf = Files.readAllBytes(Path.of(path));

        for (int i = 0; i < 4; i++)
            if (buf[OFF_MAGIC + i] != MAGIC[i])
                throw new SaveFormatException("Not a Phantasia save: " + DataPaths.absolute(path));

        short version = readShort(buf, OFF_VERSION);

        int         partyGold = readInt(buf, OFF_PARTY_GOLD);
        int         bankGold  = readInt(buf, OFF_BANK_GOLD);
        PartyLedger ledger    = PartyLedger.fromRaw(partyGold, bankGold);
        WorldPosition worldPos = new WorldPosition(
                readShort(buf, OFF_WORLD_X), readShort(buf, OFF_WORLD_Y));

        long  questFlags;
        float clockTime;
        int   offSize, offRoster, recordSize;

        switch (version) {
            case 1 -> {
                questFlags = readInt(buf, OFF_QUEST_FLAGS) & 0xFFFFFFFFL;
                clockTime  = DEFAULT_CLOCK;
                offSize    = V1_OFF_PARTY_SIZE; offRoster = V1_OFF_ROSTER; recordSize = V1_RECORD;
                System.out.println("[SaveManager] Migrating v1→v4.");
            }
            case 2 -> {
                questFlags = readLong(buf, OFF_QUEST_FLAGS);
                clockTime  = DEFAULT_CLOCK;
                offSize    = V2_OFF_PARTY_SIZE; offRoster = V2_OFF_ROSTER; recordSize = V2_RECORD;
                System.out.println("[SaveManager] Migrating v2→v4.");
            }
            case 3 -> {
                questFlags = readLong(buf, OFF_QUEST_FLAGS);
                clockTime  = readFloat(buf, V3_OFF_CLOCK);
                offSize    = V3_OFF_PARTY_SIZE; offRoster = V3_OFF_ROSTER; recordSize = V3_RECORD;
                System.out.println("[SaveManager] Migrating v3→v4 (records padded 48→64).");
            }
            case 4 -> {
                questFlags = readLong(buf, OFF_QUEST_FLAGS);
                clockTime  = readFloat(buf, V4_OFF_CLOCK);
                offSize    = V4_OFF_PARTY_SIZE; offRoster = V4_OFF_ROSTER; recordSize = V4_RECORD;
            }
            default -> throw new SaveFormatException(
                    "Unsupported version " + version + " (supported 1–4): "
                            + DataPaths.absolute(path));
        }

        int partySize = readShort(buf, offSize);
        if (partySize < 0 || partySize > PARTY_MAX)
            throw new SaveFormatException("Corrupt party size: " + partySize);

        List<PlayerCharacter> party = new java.util.ArrayList<>(partySize);
        for (int i = 0; i < partySize; i++) {
            byte[] rec64 = new byte[DataLayout.RECORD_SIZE]; // 64, zero-initialised
            int copyLen  = Math.min(recordSize, DataLayout.RECORD_SIZE);
            System.arraycopy(buf, offRoster + i * recordSize, rec64, 0, copyLen);
            // Bytes copyLen–63 remain 0 — correct default for expansion zone.
            party.add(new PlayerCharacter(new DataCore(rec64)));
        }

        System.out.printf("[SaveManager] Loaded %d chars from %s (v%d→v4).%n",
                partySize, DataPaths.absolute(path), version);

        return new GameSession(party, ledger, worldPos, questFlags, clockTime);
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    public static boolean saveExists(String path) {
        return Files.exists(Path.of(path));
    }

    public static GameSession newGame(List<PlayerCharacter> party,
                                      WorldPosition startPos) {
        return new GameSession(party, PartyLedger.newGame(), startPos, 0L);
    }

    // -------------------------------------------------------------------------
    // Binary helpers
    // -------------------------------------------------------------------------

    private static void writeShort(byte[] b, int o, int v) {
        b[o] = (byte)((v>>8)&0xFF); b[o+1] = (byte)(v&0xFF);
    }
    private static void writeInt(byte[] b, int o, int v) {
        b[o]=(byte)((v>>24)&0xFF); b[o+1]=(byte)((v>>16)&0xFF);
        b[o+2]=(byte)((v>>8)&0xFF); b[o+3]=(byte)(v&0xFF);
    }
    private static void writeLong(byte[] b, int o, long v) {
        for (int i = 7; i >= 0; i--) { b[o+7-i] = (byte)((v>>(i*8))&0xFF); }
    }
    private static void writeFloat(byte[] b, int o, float v) { writeInt(b, o, Float.floatToIntBits(v)); }

    private static short readShort(byte[] b, int o) {
        return (short)(((b[o]&0xFF)<<8)|(b[o+1]&0xFF));
    }
    private static int readInt(byte[] b, int o) {
        return ((b[o]&0xFF)<<24)|((b[o+1]&0xFF)<<16)|((b[o+2]&0xFF)<<8)|(b[o+3]&0xFF);
    }
    private static long readLong(byte[] b, int o) {
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v<<8)|(b[o+i]&0xFF);
        return v;
    }
    private static float readFloat(byte[] b, int o) { return Float.intBitsToFloat(readInt(b, o)); }

    public static class SaveFormatException extends IOException {
        public SaveFormatException(String message) { super(message); }
    }
}
