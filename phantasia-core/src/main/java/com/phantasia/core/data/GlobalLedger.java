package com.phantasia.core.data;

import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.PlayerCharacter;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.File;

/**
 * GlobalLedger: The Persistence Engine.
 * Manages the legacy GAME.DAT file, storing pooled gold and 64-byte v4 player records.
 *
 * Path is resolved via DataPaths.DAT_DIR so it moves with the rest of the
 * data layer and respects the -Dphantasia.data.dir override.
 */
public class GlobalLedger {
    private static final String FILE_PATH =
            DataPaths.DAT_DIR + "/GAME.DAT";

    // File Offsets based on architectural specs
    private static final int GOLD_OFFSET  = 0x00;   // 4-byte integer for Party Gold
    private static final int BANK_OFFSET  = 0x04;   // 4-byte integer for Bank Gold
    private static final int ROSTER_START = 0x26;   // Start of the 6-slot character roster

    private int partyGold;
    private int bankGold;

    public GlobalLedger() {
        loadGlobalData();
    }

    /**
     * Writes one complete legacy v4 player record to the selected roster slot.
     * The record is exported through PlayerCharacter's defensive-copy gateway.
     */
    public void saveCharacter(int slot, PlayerCharacter pc) throws IOException {
        if (slot < 0 || slot > 5) throw new IllegalArgumentException("Invalid Roster Slot");
        java.util.Objects.requireNonNull(pc, "pc");

        byte[] record = pc.toLegacyRecordBytes();
        if (record.length != DataLayout.RECORD_SIZE) {
            throw new IOException("Unexpected legacy player record size: " + record.length);
        }

        DataPaths.ensureParentDirs(FILE_PATH);
        try (RandomAccessFile raf = new RandomAccessFile(FILE_PATH, "rw")) {
            raf.seek(ROSTER_START + (slot * DataLayout.RECORD_SIZE));
            raf.write(record);
        }
    }

    /**
     * Updates the shared Party Gold in the global save file.
     */
    public void saveGold() throws IOException {
        DataPaths.ensureParentDirs(FILE_PATH);
        try (RandomAccessFile raf = new RandomAccessFile(FILE_PATH, "rw")) {
            raf.seek(GOLD_OFFSET);
            raf.writeInt(partyGold); // 4-byte write
            raf.seek(BANK_OFFSET);
            raf.writeInt(bankGold);
        }
    }

    private void loadGlobalData() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            this.partyGold = 400; // Starting gold for new journey
            this.bankGold  = 0;
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(FILE_PATH, "r")) {
            raf.seek(GOLD_OFFSET);
            this.partyGold = raf.readInt();
            raf.seek(BANK_OFFSET);
            this.bankGold  = raf.readInt();
        } catch (IOException e) {
            System.err.println("Ledger Read Error: Falling back to defaults. ("
                    + DataPaths.absolute(FILE_PATH) + ")");
        }
    }

    public PlayerCharacter loadCharacter(int slot) throws IOException {
        if (slot < 0 || slot > 5) throw new IllegalArgumentException("Invalid Roster Slot");

        try (RandomAccessFile raf = new RandomAccessFile(FILE_PATH, "r")) {
            // 1. Seek to the start of the 64-byte slot
            raf.seek(ROSTER_START + (slot * DataLayout.RECORD_SIZE));

            // 2. Read one complete legacy v4 record
            byte[] rawSoul = new byte[DataLayout.RECORD_SIZE];
            raf.readFully(rawSoul);

            // 3. Reconstruct the DataCore and Character object
            return PlayerCharacter.fromLegacyRecordBytes(rawSoul);
        }
    }
    // --- Getters and Setters ---
    public int  getPartyGold()             { return partyGold; }
    public void addPartyGold(int amount)   { this.partyGold += amount; }
    public int  getBankGold()              { return bankGold; }
    public void addBankGold(int amount)    { this.bankGold += amount; }
    public void withdrawBankGold(int amount) { this.bankGold -= amount; }
    public void withdrawPartyGold(int amount) { this.partyGold -= amount; }
}
