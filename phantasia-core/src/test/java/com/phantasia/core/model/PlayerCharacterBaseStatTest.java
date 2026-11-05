package com.phantasia.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerCharacterBaseStatTest {
    @Test
    void baseStatsReadTheRequestedPersistentStatWithoutRecursion() {
        PlayerCharacter player = PlayerCharacter.fromLegacyCreation(
                "Seren", Job.FIGHTER, 40, 12, 11, 17, 23, 5, 2);

        assertEquals(11, player.getBaseStat(Stat.STRENGTH));
        assertEquals(17, player.getBaseStat(Stat.INTELLIGENCE));
        assertEquals(23, player.getBaseStat(Stat.DEXTERITY));
        assertNotEquals(player.getBaseStat(Stat.STRENGTH),
                player.getBaseStat(Stat.DEXTERITY));
        assertThrows(NullPointerException.class, () -> player.getBaseStat(null));
    }

    @Test
    void baseStatReadsSurviveRecordRoundTrip() {
        PlayerCharacter original = PlayerCharacter.fromLegacyCreation(
                "Alaric", Job.FIGHTER, 30, 8, 13, 15, 19, 7, 1);
        PlayerCharacter restored = PlayerCharacter.fromLegacyRecordBytes(
                original.toLegacyRecordBytes());

        for (Stat stat : new Stat[]{Stat.STRENGTH, Stat.INTELLIGENCE, Stat.DEXTERITY}) {
            assertEquals(original.getStat(stat), restored.getBaseStat(stat));
        }
    }
}
