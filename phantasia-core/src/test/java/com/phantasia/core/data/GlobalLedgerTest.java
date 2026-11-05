package com.phantasia.core.data;

import com.phantasia.core.model.DataLayout;
import com.phantasia.core.model.Job;
import com.phantasia.core.model.PlayerCharacter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class GlobalLedgerTest {
    @TempDir Path directory;

    private static PlayerCharacter character(String name) {
        return PlayerCharacter.fromLegacyCreation(
                name, Job.FIGHTER, 40, 5, 12, 8, 10, 10, 3);
    }

    @Test
    void writesFullRosterRecordsAndRoundTripsBySlot() throws Exception {
        Path save = directory.resolve("nested/GAME.DAT");
        GlobalLedger ledger = new GlobalLedger(save);
        PlayerCharacter first = character("Aldric");
        PlayerCharacter sixth = character("Seren");

        ledger.saveCharacter(0, first);
        ledger.saveCharacter(5, sixth);

        byte[] bytes = Files.readAllBytes(save);
        int firstOffset = 0x26;
        int sixthOffset = firstOffset + 5 * DataLayout.RECORD_SIZE;
        assertTrue(bytes.length >= sixthOffset + DataLayout.RECORD_SIZE);
        assertArrayEquals(first.toLegacyRecordBytes(),
                Arrays.copyOfRange(bytes, firstOffset, firstOffset + DataLayout.RECORD_SIZE));
        assertArrayEquals(sixth.toLegacyRecordBytes(),
                Arrays.copyOfRange(bytes, sixthOffset, sixthOffset + DataLayout.RECORD_SIZE));

        GlobalLedger reopened = new GlobalLedger(save);
        assertEquals("Aldric", reopened.loadCharacter(0).getName());
        assertEquals("Seren", reopened.loadCharacter(5).getName());
        assertEquals(first.getHp(), reopened.loadCharacter(0).getHp());
        assertEquals(sixth.getHp(), reopened.loadCharacter(5).getHp());
    }

    @Test
    void rejectsInvalidSlotsWithoutChangingFile() throws Exception {
        Path save = directory.resolve("GAME.DAT");
        GlobalLedger ledger = new GlobalLedger(save);
        PlayerCharacter character = character("Mira");
        ledger.saveCharacter(0, character);
        byte[] before = Files.readAllBytes(save);

        assertThrows(IllegalArgumentException.class, () -> ledger.saveCharacter(-1, character));
        assertThrows(IllegalArgumentException.class, () -> ledger.saveCharacter(6, character));
        assertThrows(IllegalArgumentException.class, () -> ledger.loadCharacter(-1));
        assertThrows(IllegalArgumentException.class, () -> ledger.loadCharacter(6));
        assertThrows(NullPointerException.class, () -> ledger.saveCharacter(1, null));
        assertArrayEquals(before, Files.readAllBytes(save));
    }
}
