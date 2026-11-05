package com.phantasia.core.data;

import com.phantasia.core.model.Job;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.world.WorldPosition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class SaveManagerValidationTest {
    @TempDir Path tempDir;

    private static PlayerCharacter player(String name) {
        return PlayerCharacter.fromLegacyCreation(
                name, Job.FIGHTER, 30, 8, 12, 11, 10, 7, 1);
    }

    @Test
    void refusesOversizedActivePartyWithoutCreatingSave() {
        Path path = tempDir.resolve("save.dat");
        List<PlayerCharacter> players = new ArrayList<>();
        for (int i = 0; i < 7; i++) players.add(player("Member" + i));
        GameSession session = SaveManager.newGame(players, new WorldPosition(1, 2));
        assertThrows(java.io.IOException.class, () -> SaveManager.save(session, path.toString()));
        assertFalse(Files.exists(path), "A rejected save must not leave a partial file");
    }

    @Test
    void rejectsTruncationAndTrailingDataWithSaveFormatException() throws Exception {
        Path path = tempDir.resolve("save.dat");
        GameSession session = SaveManager.newGame(
                List.of(player("Seren")), new WorldPosition(2, 3));
        SaveManager.save(session, path.toString());
        byte[] valid = Files.readAllBytes(path);
        assertEquals("Seren", SaveManager.load(path.toString()).getParty().getFirst().getName());

        int[] lengths = {0, 1, 3, 5, 6, 15, 31, valid.length - 1};
        for (int length : lengths) {
            Files.write(path, Arrays.copyOf(valid, length));
            assertThrows(SaveManager.SaveFormatException.class,
                    () -> SaveManager.load(path.toString()),
                    "Truncated file length " + length + " must be rejected cleanly");
        }

        Files.write(path, Arrays.copyOf(valid, valid.length + 1));
        assertThrows(SaveManager.SaveFormatException.class,
                () -> SaveManager.load(path.toString()),
                "Unexpected trailing bytes must be rejected");
    }

    @Test
    void rejectsCorruptPartyCountBeforeCopyingRecords() throws Exception {
        Path path = tempDir.resolve("save.dat");
        SaveManager.save(SaveManager.newGame(
                List.of(player("Lea")), new WorldPosition(0, 0)), path.toString());
        byte[] bytes = Files.readAllBytes(path);
        bytes[0x1E] = 0;
        bytes[0x1F] = 7;
        Files.write(path, bytes);
        assertThrows(SaveManager.SaveFormatException.class,
                () -> SaveManager.load(path.toString()));
    }
}
