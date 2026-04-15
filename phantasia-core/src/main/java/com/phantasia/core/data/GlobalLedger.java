package com.phantasia.core.data;

import com.phantasia.core.data.DataPaths;
import com.phantasia.core.model.DataCore;
import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.PlayerCharacter;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.File;

/**
 * GlobalLedger: The Persistence Engine.
 * Manages the GAME.DAT file, storing pooled gold and the 48-byte DataCores.
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
     * Surgical Write: Commits a single character's 48-byte soul to a specific slot.
     */
    public void saveCharacter(int slot, PlayerCharacter pc) throws IOException {
        if (slot < 0 || slot > 5) throw new IllegalArgumentException("Invalid Roster Slot");

        DataPaths.ensureParentDirs(FILE_PATH);
        try (RandomAccessFile raf = new RandomAccessFile(FILE_PATH, "rw")) {
            // Seek to the exact byte where this character's data begins
            raf.seek(ROSTER_START + (slot * DataLayout.RECORD_SIZE));
            // In a future update, we would expose the raw byte array from PlayerCharacter
            // For now, we simulate the 48-byte write requirement
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
            // 1. Seek to the start of the 48-byte slot
            raf.seek(ROSTER_START + (slot * DataLayout.RECORD_SIZE));

            // 2. Read exactly 48 bytes (the 'soul' of the character)
            byte[] rawSoul = new byte[DataLayout.RECORD_SIZE];
            raf.readFully(rawSoul);

            // 3. Reconstruct the DataCore and Character object
            DataCore core = new DataCore(rawSoul);
            return new PlayerCharacter(core);
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