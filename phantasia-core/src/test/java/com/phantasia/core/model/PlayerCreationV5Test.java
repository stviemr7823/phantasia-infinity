package com.phantasia.core.model;

import com.phantasia.core.model.PlayerStateV5.Attribute;
import com.phantasia.core.model.item.EquipSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerCreationV5Test {
    @Test
    void createsIndependentCompleteLevelOnePlayerWithoutLegacyRecords() {
        PlayerStateV5 player = PlayerCreationV5.create(
                "Shepherd", 42, Job.FIGHTER, 0, 14, 12, 9, 36, 0);

        assertEquals("Shepherd", player.name());
        assertEquals(42L, player.recordId());
        assertEquals(Job.FIGHTER.getBitValue(), player.jobId());
        assertEquals(0, player.raceId());
        assertEquals(1, player.level());
        assertEquals(14, player.attribute(Attribute.STRENGTH));
        assertEquals(12, player.attribute(Attribute.AGILITY));
        assertEquals(9, player.attribute(Attribute.MIND));
        assertEquals(36, player.hp());
        assertEquals(36, player.maxHp());
        assertEquals(0, player.mp());
        assertEquals(0, player.maxMp());
        assertEquals(0L, player.experience());
        assertEquals(0, player.statusFlags());
        for (EquipSlot slot : EquipSlot.values()) assertEquals(0, player.equipment(slot));
        for (int i = 0; i < 10; i++) assertEquals(0, player.inventory(i));

        PlayerStateV5 loaded = PlayerStateV5.fromBytes(player.toBytes());
        assertArrayEquals(player.toBytes(), loaded.toBytes());
        loaded.setExperience(0xF0E0D0C0L);
        loaded.setEquipment(EquipSlot.WEAPON, 72);
        loaded.setInventory(9, 105);
        assertEquals(0, player.equipment(EquipSlot.WEAPON));
        assertEquals(0, player.inventory(9));
        assertEquals(0L, player.experience());
        assertEquals(0xF0E0D0C0L, loaded.experience());
    }

    @Test
    void creationRejectsInvalidInputsWithoutClamping() {
        assertThrows(NullPointerException.class, () ->
                PlayerCreationV5.create("A", 1, null, 0, 1, 1, 1, 10, 0));
        assertThrows(IllegalArgumentException.class, () ->
                PlayerCreationV5.create("A", 1, Job.FIGHTER, 0, 1, 1, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () ->
                PlayerCreationV5.create("A", 1, Job.FIGHTER, 0, 1, 1, 1, 10, -1));
        assertThrows(IllegalArgumentException.class, () ->
                PlayerCreationV5.create("A", 1, Job.FIGHTER, 0, 256, 1, 1, 10, 0));
        assertThrows(IllegalArgumentException.class, () ->
                PlayerCreationV5.create("A", 1, Job.FIGHTER, 256, 1, 1, 1, 10, 0));
        assertThrows(IllegalArgumentException.class, () ->
                PlayerCreationV5.create("Name longer than fifteen characters", 1,
                        Job.FIGHTER, 0, 1, 1, 1, 10, 0));
    }
}
