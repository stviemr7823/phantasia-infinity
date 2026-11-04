package com.phantasia.baseline;

import com.phantasia.core.model.DataCoreV5;
import com.phantasia.core.model.DataLayoutV5;
import com.phantasia.core.model.PlayerStateV5;
import com.phantasia.core.model.PlayerStateV5.Attribute;
import com.phantasia.core.model.item.EquipSlot;

/** Standalone checks; does not depend on JUnit or a graphics engine. */
public final class PlayerStateV5SmokeTest {
    private static int checks;
    private static void ok(boolean truth, String msg) {
        if (!truth) throw new AssertionError(msg);
        checks++;
    }
    private static void rejects(Runnable action, String msg) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(msg);
    }
    public static void main(String[] args) {
        PlayerStateV5 p = PlayerStateV5.create("Seren", 123456L);
        p.setAttribute(Attribute.STRENGTH, 15);
        p.setAttribute(Attribute.AGILITY, 20);
        p.setAttribute(Attribute.MIND, 25);
        p.setHp(120);
        p.setMaxHp(190);
        p.setMp(40);
        p.setMaxMp(75);
        p.setExperience(0xAABBCCDDL);
        p.setEquipment(EquipSlot.WEAPON, 71);
        p.setEquipment(EquipSlot.SHIELD, 30);
        p.setEquipment(EquipSlot.ARMOR, 9);
        p.setEquipment(EquipSlot.BOW, 22);
        p.setInventory(0, 1);
        p.setInventory(9, 222);
        p.setJobId(3);
        p.setRaceId(4);
        p.setLevel(5);
        p.setStatusFlags(7);
        p.setPortraitId(11);
        PlayerStateV5 q = PlayerStateV5.fromBytes(p.toBytes());
        ok(q.name().equals("Seren") && q.recordId() == 123456L, "identity");
        ok(q.attribute(Attribute.STRENGTH) == 15, "strength");
        ok(q.attribute(Attribute.AGILITY) == 20, "agility");
        ok(q.attribute(Attribute.MIND) == 25, "mind");
        ok(q.hp() == 120 && q.maxHp() == 190, "HP values");
        ok(q.mp() == 40 && q.maxMp() == 75, "MP values");
        ok(q.experience() == 0xAABBCCDDL, "full 32-bit XP");
        ok(q.equipment(EquipSlot.WEAPON) == 71, "weapon independent of XP");
        ok(q.equipment(EquipSlot.SHIELD) == 30 && q.equipment(EquipSlot.ARMOR) == 9
            && q.equipment(EquipSlot.BOW) == 22, "equipment slots");
        ok(q.inventory(0) == 1 && q.inventory(9) == 222, "ten inventory slots");
        ok(q.jobId() == 3 && q.raceId() == 4 && q.level() == 5, "identity fields");
        ok(q.statusFlags() == 7 && q.portraitId() == 11, "status and portrait");
        ok(q.toBytes().length == 64, "64 byte record");
        byte[] serialized = q.toBytes();
        serialized[DataLayoutV5.PC_WEAPON.offset()] = 0;
        ok(q.equipment(EquipSlot.WEAPON) == 71, "defensive export");
        DataCoreV5 source = new DataCoreV5(DataLayoutV5.Kind.PLAYER);
        source.setName("Copy");
        PlayerStateV5 copy = PlayerStateV5.fromRecord(source);
        source.setName("Mutated");
        ok(copy.name().equals("Copy"), "defensive source copy");
        rejects(() -> PlayerStateV5.fromRecord(new DataCoreV5(DataLayoutV5.Kind.MONSTER)), "wrong kind");
        rejects(() -> PlayerStateV5.fromBytes(new byte[48]), "legacy record");
        rejects(() -> p.setHp(65536), "HP overflow");
        rejects(() -> p.setExperience(0x1_0000_0000L), "XP overflow");
        rejects(() -> p.setInventory(10, 1), "inventory overflow");
        rejects(() -> p.setAttribute(Attribute.MIND, 256), "attribute overflow");
        ok(q.toBytes()[DataLayoutV5.PC_RESERVED.offset()] == 0, "reserved bytes untouched");
        System.out.println("PASS v5 player accessors checks=" + checks + " (live gameplay unchanged)");
    }
}
