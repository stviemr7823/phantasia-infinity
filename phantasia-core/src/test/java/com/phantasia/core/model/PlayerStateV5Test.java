package com.phantasia.core.model;

import com.phantasia.core.model.DataLayoutV5.Kind;
import com.phantasia.core.model.PlayerStateV5.Attribute;
import com.phantasia.core.model.item.EquipSlot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerStateV5Test {
    @Test void playerFieldsRoundTripAndRemainIndependent() {
        PlayerStateV5 p = PlayerStateV5.create("Seren", 0x87654321L);
        p.setAttribute(Attribute.STRENGTH, 19);
        p.setAttribute(Attribute.AGILITY, 25);
        p.setAttribute(Attribute.MIND, 32);
        p.setJobId(7);
        p.setRaceId(9);
        p.setLevel(12);
        p.setStatusFlags(0x5A);
        p.setPortraitId(18);
        p.setHp(320);
        p.setMaxHp(500);
        p.setMp(17);
        p.setMaxMp(100);
        p.setExperience(0xFEDCBA98L);
        p.setEquipment(EquipSlot.WEAPON, 199);
        p.setEquipment(EquipSlot.SHIELD, 12);
        p.setEquipment(EquipSlot.ARMOR, 44);
        p.setEquipment(EquipSlot.BOW, 87);
        p.setInventory(0, 33);
        p.setInventory(9, 255);
        PlayerStateV5 q = PlayerStateV5.fromBytes(p.toBytes());
        assertEquals("Seren", q.name());
        assertEquals(0x87654321L, q.recordId());
        assertEquals(19, q.attribute(Attribute.STRENGTH));
        assertEquals(25, q.attribute(Attribute.AGILITY));
        assertEquals(32, q.attribute(Attribute.MIND));
        assertEquals(7, q.jobId());
        assertEquals(9, q.raceId());
        assertEquals(12, q.level());
        assertEquals(0x5A, q.statusFlags());
        assertEquals(18, q.portraitId());
        assertEquals(320, q.hp());
        assertEquals(500, q.maxHp());
        assertEquals(17, q.mp());
        assertEquals(100, q.maxMp());
        assertEquals(0xFEDCBA98L, q.experience());
        assertEquals(199, q.equipment(EquipSlot.WEAPON));
        assertEquals(12, q.equipment(EquipSlot.SHIELD));
        assertEquals(44, q.equipment(EquipSlot.ARMOR));
        assertEquals(87, q.equipment(EquipSlot.BOW));
        assertEquals(33, q.inventory(0));
        assertEquals(255, q.inventory(9));
        byte[] b = p.toBytes();
        b[0x2C] = 0;
        assertEquals(199, p.equipment(EquipSlot.WEAPON));
        assertEquals(199, q.equipment(EquipSlot.WEAPON));
    }

    @Test void rejectsWrongKindsAndOverflow() {
        DataCoreV5 monster = new DataCoreV5(Kind.MONSTER);
        assertThrows(IllegalArgumentException.class, () -> PlayerStateV5.fromRecord(monster));
        assertThrows(IllegalArgumentException.class, () -> PlayerStateV5.fromBytes(new byte[48]));
        PlayerStateV5 p = PlayerStateV5.create("A", 0);
        assertThrows(IllegalArgumentException.class, () -> p.setExperience(0x1_0000_0000L));
        assertThrows(IllegalArgumentException.class, () -> p.setHp(65536));
        assertThrows(IllegalArgumentException.class, () -> p.setAttribute(Attribute.MIND, -1));
        assertThrows(IllegalArgumentException.class, () -> p.setInventory(10, 2));
        assertThrows(NullPointerException.class, () -> p.attribute(null));
        assertThrows(NullPointerException.class, () -> p.setEquipment(null, 2));
    }

    @Test void noImplicitLegacyConversionOrExternalMutation() {
        DataCoreV5 record = new DataCoreV5(Kind.PLAYER);
        record.setName("Lea");
        PlayerStateV5 p = PlayerStateV5.fromRecord(record);
        record.setName("Other");
        assertEquals("Lea", p.name());
        assertEquals(Kind.PLAYER, DataCoreV5.fromBytes(p.toBytes()).kind());
        assertEquals(64, p.toBytes().length);
    }
}
