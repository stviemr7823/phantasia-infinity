package com.phantasia.core.model;

import com.phantasia.core.model.DataLayoutV5.Field;
import com.phantasia.core.model.DataLayoutV5.Kind;
import com.phantasia.core.model.item.EquipSlot;
import java.util.Objects;

/**
 * Storage-facing player state for the v5 format. No game rules or v4 data
 * conversion belong here. The live PlayerCharacter continues using v4 until
 * its call sites and formulas have been migrated deliberately.
 */
public final class PlayerStateV5 {
    public enum Attribute {
        STRENGTH(DataLayoutV5.PC_STRENGTH),
        AGILITY(DataLayoutV5.PC_AGILITY),
        MIND(DataLayoutV5.PC_MIND);

        private final Field field;
        Attribute(Field field) { this.field = field; }
    }

    private final DataCoreV5 record;

    private PlayerStateV5(DataCoreV5 record) {
        if (Objects.requireNonNull(record, "record").kind() != Kind.PLAYER)
            throw new IllegalArgumentException("PlayerStateV5 requires a PLAYER record");
        this.record = record;
    }

    /** A new player record; 0 is an unassigned ID until a store assigns one. */
    public static PlayerStateV5 create(String name, long recordId) {
        DataCoreV5 record = new DataCoreV5(Kind.PLAYER);
        record.setName(name);
        record.setRecordId(recordId);
        return new PlayerStateV5(record);
    }

    /** Strict import of the v5 byte format; never silently accept v4 bytes. */
    public static PlayerStateV5 fromBytes(byte[] bytes) {
        return new PlayerStateV5(DataCoreV5.fromBytes(bytes));
    }

    /** Copies the supplied record; external changes cannot mutate this state. */
    public static PlayerStateV5 fromRecord(DataCoreV5 record) {
        Objects.requireNonNull(record, "record");
        return new PlayerStateV5(DataCoreV5.fromBytes(record.toBytes()));
    }

    public byte[] toBytes() { return record.toBytes(); }
    public String name() { return record.name(); }
    public void setName(String value) { record.setName(value); }
    public long recordId() { return record.recordId(); }
    public void setRecordId(long value) { record.setRecordId(value); }
    public int sharedFlags() { return record.sharedFlags(); }
    public void setSharedFlags(int value) { record.setSharedFlags(value); }

    public int attribute(Attribute attribute) {
        return record.readU8(Objects.requireNonNull(attribute, "attribute").field);
    }
    public void setAttribute(Attribute attribute, int value) {
        record.writeU8(Objects.requireNonNull(attribute, "attribute").field, value);
    }

    public int jobId() { return record.readU8(DataLayoutV5.PC_JOB); }
    public void setJobId(int id) { record.writeU8(DataLayoutV5.PC_JOB, id); }
    public int raceId() { return record.readU8(DataLayoutV5.PC_RACE); }
    public void setRaceId(int id) { record.writeU8(DataLayoutV5.PC_RACE, id); }
    public int level() { return record.readU8(DataLayoutV5.PC_LEVEL); }
    public void setLevel(int value) { record.writeU8(DataLayoutV5.PC_LEVEL, value); }
    public int statusFlags() { return record.readU8(DataLayoutV5.PC_STATUS); }
    public void setStatusFlags(int value) { record.writeU8(DataLayoutV5.PC_STATUS, value); }
    public int portraitId() { return record.readU8(DataLayoutV5.PC_PORTRAIT); }
    public void setPortraitId(int value) { record.writeU8(DataLayoutV5.PC_PORTRAIT, value); }

    public int hp() { return record.readU16(DataLayoutV5.PC_HP); }
    public void setHp(int value) { record.writeU16(DataLayoutV5.PC_HP, value); }
    public int maxHp() { return record.readU16(DataLayoutV5.PC_MAX_HP); }
    public void setMaxHp(int value) { record.writeU16(DataLayoutV5.PC_MAX_HP, value); }
    public int mp() { return record.readU16(DataLayoutV5.PC_MP); }
    public void setMp(int value) { record.writeU16(DataLayoutV5.PC_MP, value); }
    public int maxMp() { return record.readU16(DataLayoutV5.PC_MAX_MP); }
    public void setMaxMp(int value) { record.writeU16(DataLayoutV5.PC_MAX_MP, value); }
    public long experience() { return record.readU32(DataLayoutV5.PC_XP); }
    public void setExperience(long value) { record.writeU32(DataLayoutV5.PC_XP, value); }

    private static Field equipmentField(EquipSlot slot) {
        return switch (Objects.requireNonNull(slot, "slot")) {
            case WEAPON -> DataLayoutV5.PC_WEAPON;
            case SHIELD -> DataLayoutV5.PC_SHIELD;
            case ARMOR -> DataLayoutV5.PC_ARMOR;
            case BOW -> DataLayoutV5.PC_BOW;
        };
    }
    public int equipment(EquipSlot slot) { return record.readU8(equipmentField(slot)); }
    public void setEquipment(EquipSlot slot, int itemId) {
        record.writeU8(equipmentField(slot), itemId);
    }
    public int inventory(int slot) { return record.readIndexedU8(DataLayoutV5.PC_INVENTORY, slot); }
    public void setInventory(int slot, int itemId) {
        record.writeIndexedU8(DataLayoutV5.PC_INVENTORY, slot, itemId);
    }
}
