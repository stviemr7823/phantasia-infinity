package com.phantasia.core.model;

import com.phantasia.core.model.item.EquipSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerRuntimeV5Test {
    private static PlayerRuntimeV5 newRuntime() {
        return new PlayerRuntimeV5(PlayerCreationV5.create(
                "Shepherd", 44, Job.FIGHTER, 0, 14, 12, 9, 40, 20));
    }

    @Test
    void damageHealingAndMpAreBoundedAndCannotMutateXp() {
        PlayerRuntimeV5 player = newRuntime();
        player.damage(12);
        assertEquals(28, player.hp());
        player.heal(1000);
        assertEquals(40, player.hp());
        assertFalse(player.spendMp(21));
        assertEquals(20, player.mp());
        assertTrue(player.spendMp(7));
        assertEquals(13, player.mp());
        player.restoreMp(1000);
        assertEquals(20, player.mp());
        player.damage(1000);
        assertFalse(player.isAlive());
        player.heal(100);
        assertEquals(0, player.hp());
        assertEquals(0L, player.experience());
        assertThrows(IllegalArgumentException.class, () -> player.damage(-1));
        assertThrows(IllegalArgumentException.class, () -> player.heal(-1));
        assertThrows(IllegalArgumentException.class, () -> player.spendMp(-1));
        assertThrows(IllegalArgumentException.class, () -> player.restoreMp(-1));
    }

    @Test
    void experienceDoesNotWrapAndSnapshotsAreDetached() {
        PlayerRuntimeV5 player = newRuntime();
        player.awardExperience(0xFFFFFFFEL);
        assertEquals(0xFFFFFFFEL, player.experience());
        assertThrows(IllegalArgumentException.class, () -> player.awardExperience(2));
        assertEquals(0xFFFFFFFEL, player.experience());
        player.awardExperience(1);
        assertEquals(0xFFFFFFFFL, player.experience());
        assertThrows(IllegalArgumentException.class, () -> player.awardExperience(1));
        assertThrows(IllegalArgumentException.class, () -> player.awardExperience(-1));
        PlayerStateV5 snapshot = player.snapshot();
        snapshot.setExperience(0);
        assertEquals(0xFFFFFFFFL, player.experience());
    }

    @Test
    void equipmentUsesDedicatedV5StorageNotXp() {
        PlayerRuntimeV5 player = newRuntime();
        player.awardExperience(1000);
        player.equip(EquipSlot.WEAPON, 72);  // Sword
        player.equip(EquipSlot.SHIELD, 2);   // Wooden Shield
        assertEquals(72, player.equippedItemId(EquipSlot.WEAPON));
        assertEquals(2, player.equippedItemId(EquipSlot.SHIELD));
        assertEquals(1000L, player.experience());
        assertEquals(72, PlayerStateV5.fromBytes(player.snapshot().toBytes())
                .equipment(EquipSlot.WEAPON));
        assertThrows(IllegalArgumentException.class, () ->
                player.equip(EquipSlot.ARMOR, 72));
        assertEquals(0, player.equippedItemId(EquipSlot.ARMOR));
        player.unequip(EquipSlot.WEAPON);
        assertEquals(0, player.equippedItemId(EquipSlot.WEAPON));
        assertEquals(1000L, player.experience());
    }
}
