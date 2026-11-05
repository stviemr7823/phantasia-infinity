package com.phantasia.core.model;

import com.phantasia.core.model.PlayerStateV5.Attribute;
import com.phantasia.core.model.item.EquipSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyPlayerV5AdapterTest {
    @Test
    void copiesUnambiguousFieldsWithoutChangingLegacyBytes() {
        PlayerCharacter legacy = PlayerCharacter.fromLegacyCreation(
                "Seren", Job.FIGHTER, 320, 91, 16, 23, 19, 7, 4);
        legacy.setStat(Stat.XP, 0x1234);
        legacy.setInventoryItem(0, 44);
        legacy.setInventoryItem(7, 75);
        DataCore extended = new DataCore(legacy.toLegacyRecordBytes());
        extended.setExtendedInventorySlot(0, 66);
        extended.setExtendedInventorySlot(1, 77);
        legacy = PlayerCharacter.fromLegacyRecordBytes(extended.getRawBytes());
        byte[] before = legacy.toLegacyRecordBytes();

        PlayerStateV5 v5 = LegacyPlayerV5Adapter.snapshotUnambiguousFields(
                legacy, 0x87654321L);

        assertEquals("Seren", v5.name());
        assertEquals(0x87654321L, v5.recordId());
        assertEquals(16, v5.attribute(Attribute.STRENGTH));
        assertEquals(19, v5.attribute(Attribute.AGILITY));
        assertEquals(23, v5.attribute(Attribute.MIND));
        assertEquals(Job.FIGHTER.getBitValue(), v5.jobId());
        assertEquals(4, v5.level());
        assertEquals(320, v5.hp());
        assertEquals(320, v5.maxHp());
        assertEquals(91, v5.mp());
        assertEquals(91, v5.maxMp());
        assertEquals(0x1234L, v5.experience());
        assertEquals(44, v5.inventory(0));
        assertEquals(75, v5.inventory(7));
        assertEquals(66, v5.inventory(8));
        assertEquals(77, v5.inventory(9));
        assertEquals(0, v5.equipment(EquipSlot.WEAPON),
                "Legacy weapon byte aliases XP and cannot be migrated blindly");
        assertEquals(0, v5.equipment(EquipSlot.SHIELD));
        assertArrayEquals(before, legacy.toLegacyRecordBytes());
        assertEquals(64, PlayerStateV5.fromBytes(v5.toBytes()).toBytes().length);
    }

    @Test
    void refusesToInventAnUndeadStatusMapping() {
        PlayerCharacter legacy = PlayerCharacter.fromLegacyCreation(
                "Lea", Job.PRIEST, 20, 8, 12, 10, 15, 5, 1);
        legacy.setUndead(true);
        assertThrows(IllegalArgumentException.class,
                () -> LegacyPlayerV5Adapter.snapshotUnambiguousFields(legacy, 1L));
        assertThrows(NullPointerException.class,
                () -> LegacyPlayerV5Adapter.snapshotUnambiguousFields(null, 1L));
    }
}
