package com.phantasia.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerRecordConstructionTest {
    @Test
    void constructingAPlayerMustNotOverwriteExperience() {
        DataCore raw = new DataCore();
        raw.setName("Seren");
        raw.setShort(DataLayout.PC_HP, 27);
        raw.setShort(DataLayout.PC_XP, 0x1234);
        byte[] before = raw.getRawBytes().clone();

        PlayerCharacter player = new PlayerCharacter(raw);

        assertEquals(0x1234, player.getExperience());
        assertArrayEquals(before, player.toLegacyRecordBytes(),
                "Wrapping an existing record must not modify its bytes");
    }

    @Test
    void loadingAPlayerMustNotModifySavedBytes() {
        DataCore raw = new DataCore();
        raw.setName("Lea");
        raw.setShort(DataLayout.PC_HP, 31);
        raw.setShort(DataLayout.PC_XP, 0xABCD);
        byte[] saved = raw.getRawBytes().clone();

        PlayerCharacter loaded = PlayerCharacter.fromLegacyRecordBytes(saved);
        assertArrayEquals(saved, loaded.toLegacyRecordBytes());
        assertEquals(0xABCD, loaded.getExperience());
        assertEquals(31, loaded.getHp());

        saved[DataLayout.PC_XP] = 0;
        assertEquals(0xABCD, loaded.getExperience(),
                "Caller-owned save bytes must not alias the character record");
    }
}
