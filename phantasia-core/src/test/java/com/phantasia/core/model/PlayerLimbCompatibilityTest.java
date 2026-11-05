package com.phantasia.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerLimbCompatibilityTest {
    @Test
    void legacyLimbWritesDoNotCorruptMagicOrExperience() {
        PlayerCharacter player = PlayerCharacter.fromLegacyCreation(
                "Seren", Job.FIGHTER, 40, 0x1234, 11, 17, 23, 5, 2);
        int mp = player.getStat(Stat.MAGIC_POWER);
        int maxMp = player.getStat(Stat.MAX_MAGIC);
        int xp = player.getStat(Stat.XP);

        for (Entity.Limb limb : Entity.Limb.values()) {
            player.setLimbStatus(limb, 3);
            assertEquals(0, player.getLimbStatus(limb));
        }

        assertEquals(mp, player.getStat(Stat.MAGIC_POWER));
        assertEquals(maxMp, player.getStat(Stat.MAX_MAGIC));
        assertEquals(xp, player.getStat(Stat.XP));
        assertTrue(player.canPerform(Entity.ActionType.SWING));

        PlayerCharacter restored = PlayerCharacter.fromLegacyRecordBytes(
                player.toLegacyRecordBytes());
        assertEquals(mp, restored.getStat(Stat.MAGIC_POWER));
        assertEquals(maxMp, restored.getStat(Stat.MAX_MAGIC));
    }

    @Test
    void activeStateRatherThanLimbFlagsControlsAbilityToAct() {
        PlayerCharacter player = PlayerCharacter.fromLegacyCreation(
                "Lea", Job.FIGHTER, 20, 4, 12, 11, 13, 5, 1);
        assertTrue(player.canPerform(Entity.ActionType.CAST));
        player.setHp(0);
        assertFalse(player.canPerform(Entity.ActionType.CAST));
        assertThrows(NullPointerException.class, () -> player.getLimbStatus(null));
        assertThrows(NullPointerException.class, () -> player.setLimbStatus(null, 1));
    }
}
