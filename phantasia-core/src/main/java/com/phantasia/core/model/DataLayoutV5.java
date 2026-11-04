package com.phantasia.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * New, incompatible 64-byte entity layout. This is a v5 contract only:
 * production code continues using DataLayout/DataCore and save v4 until migrated.
 * Offsets are unsigned; integers longer than a byte use big-endian order.
 */
public final class DataLayoutV5 {
    private DataLayoutV5() {}

    public static final int RECORD_SIZE = 64;
    public static final int HEADER_SIZE = 24;
    public static final int PAYLOAD_START = 0x18;

    public enum Kind {
        PLAYER(1), MONSTER(2), SPELL(3);
        public final int code;
        Kind(int code) { this.code = code; }
        public static Kind fromCode(int code) {
            for (Kind kind : values()) if (kind.code == code) return kind;
            throw new IllegalArgumentException("Unsupported v5 entity kind: " + code);
        }
    }

    /** A declared byte range. A null kind means shared by all record types. */
    public static final class Field {
        private final String name;
        private final Kind kind;
        private final int offset;
        private final int length;
        private final boolean reserved;
        private Field(String name, Kind kind, int offset, int length, boolean reserved) {
            if (offset < 0 || length < 1 || offset + length > RECORD_SIZE)
                throw new IllegalArgumentException("Invalid v5 field: " + name);
            this.name = name;
            this.kind = kind;
            this.offset = offset;
            this.length = length;
            this.reserved = reserved;
        }
        public String name() { return name; }
        public Kind kind() { return kind; }
        public int offset() { return offset; }
        public int length() { return length; }
        public boolean reserved() { return reserved; }
        @Override public String toString() { return name; }
    }

    private static Field shared(String name, int offset, int length) {
        return new Field(name, null, offset, length, false);
    }
    private static Field field(String name, Kind kind, int offset, int length) {
        return new Field(name, kind, offset, length, false);
    }
    private static Field reserved(String name, Kind kind, int offset, int length) {
        return new Field(name, kind, offset, length, true);
    }

    // Header: identical on all records. ID=0 means unassigned in memory.
    public static final Field NAME            = shared("NAME",           0x00, 15);
    public static final Field KIND            = shared("KIND",           0x0F, 1);
    public static final Field RECORD_ID       = shared("RECORD_ID",      0x10, 4);
    public static final Field SHARED_FLAGS    = shared("SHARED_FLAGS",   0x14, 2);
    public static final Field HEADER_RESERVED = reserved("HEADER_RESERVED", null, 0x16, 2);

    // Player payload: 3 attributes; identity; vitals; 32-bit XP; 4 equipment slots;
    // ten carried item IDs. No limb packing or duplicated social statistics.
    public static final Field PC_STRENGTH   = field("PC_STRENGTH",   Kind.PLAYER, 0x18, 1);
    public static final Field PC_AGILITY    = field("PC_AGILITY",    Kind.PLAYER, 0x19, 1);
    public static final Field PC_MIND       = field("PC_MIND",       Kind.PLAYER, 0x1A, 1);
    public static final Field PC_JOB        = field("PC_JOB",        Kind.PLAYER, 0x1B, 1);
    public static final Field PC_RACE       = field("PC_RACE",       Kind.PLAYER, 0x1C, 1);
    public static final Field PC_LEVEL      = field("PC_LEVEL",      Kind.PLAYER, 0x1D, 1);
    public static final Field PC_STATUS     = field("PC_STATUS",     Kind.PLAYER, 0x1E, 1);
    public static final Field PC_PORTRAIT   = field("PC_PORTRAIT",   Kind.PLAYER, 0x1F, 1);
    public static final Field PC_HP         = field("PC_HP",         Kind.PLAYER, 0x20, 2);
    public static final Field PC_MAX_HP     = field("PC_MAX_HP",     Kind.PLAYER, 0x22, 2);
    public static final Field PC_MP         = field("PC_MP",         Kind.PLAYER, 0x24, 2);
    public static final Field PC_MAX_MP     = field("PC_MAX_MP",     Kind.PLAYER, 0x26, 2);
    public static final Field PC_XP         = field("PC_XP",         Kind.PLAYER, 0x28, 4);
    public static final Field PC_WEAPON     = field("PC_WEAPON",     Kind.PLAYER, 0x2C, 1);
    public static final Field PC_SHIELD     = field("PC_SHIELD",     Kind.PLAYER, 0x2D, 1);
    public static final Field PC_ARMOR      = field("PC_ARMOR",      Kind.PLAYER, 0x2E, 1);
    public static final Field PC_BOW        = field("PC_BOW",        Kind.PLAYER, 0x2F, 1);
    public static final Field PC_INVENTORY  = field("PC_INVENTORY",  Kind.PLAYER, 0x30, 10);
    public static final Field PC_RESERVED   = reserved("PC_RESERVED", Kind.PLAYER, 0x3A, 6);

    // Monster and spell payloads retain only current minimum factual fields.
    // Their larger free ranges are explicitly reserved for later design work.
    public static final Field MON_MAX_SPAWN = field("MON_MAX_SPAWN", Kind.MONSTER, 0x18, 1);
    public static final Field MON_FLAGS     = field("MON_FLAGS",     Kind.MONSTER, 0x19, 1);
    public static final Field MON_HP        = field("MON_HP",        Kind.MONSTER, 0x1A, 2);
    public static final Field MON_TREASURE  = field("MON_TREASURE",  Kind.MONSTER, 0x1C, 2);
    public static final Field MON_XP        = field("MON_XP",        Kind.MONSTER, 0x1E, 2);
    public static final Field MON_ITEM_1    = field("MON_ITEM_1",    Kind.MONSTER, 0x20, 1);
    public static final Field MON_ITEM_2    = field("MON_ITEM_2",    Kind.MONSTER, 0x21, 1);
    public static final Field MON_RESERVED  = reserved("MON_RESERVED",Kind.MONSTER,0x22,30);

    public static final Field SPELL_MP_COST = field("SPELL_MP_COST",Kind.SPELL, 0x18, 1);
    public static final Field SPELL_POWER   = field("SPELL_POWER",  Kind.SPELL, 0x19, 1);
    public static final Field SPELL_TARGET  = field("SPELL_TARGET", Kind.SPELL, 0x1A, 1);
    public static final Field SPELL_TYPE    = field("SPELL_TYPE",   Kind.SPELL, 0x1B, 1);
    public static final Field SPELL_LEVEL   = field("SPELL_LEVEL",  Kind.SPELL, 0x1C, 1);
    public static final Field SPELL_EFFECT  = field("SPELL_EFFECT", Kind.SPELL, 0x1D, 1);
    public static final Field SPELL_ID      = field("SPELL_ID",     Kind.SPELL, 0x1E, 1);
    public static final Field SPELL_RESERVED= reserved("SPELL_RESERVED",Kind.SPELL,0x1F,33);

    private static final List<Field> FIELDS = List.of(
        NAME, KIND, RECORD_ID, SHARED_FLAGS, HEADER_RESERVED,
        PC_STRENGTH,PC_AGILITY,PC_MIND,PC_JOB,PC_RACE,PC_LEVEL,PC_STATUS,
        PC_PORTRAIT,PC_HP,PC_MAX_HP,PC_MP,PC_MAX_MP,PC_XP,PC_WEAPON,
        PC_SHIELD,PC_ARMOR,PC_BOW,PC_INVENTORY,PC_RESERVED,
        MON_MAX_SPAWN,MON_FLAGS,MON_HP,MON_TREASURE,MON_XP,MON_ITEM_1,
        MON_ITEM_2,MON_RESERVED,
        SPELL_MP_COST,SPELL_POWER,SPELL_TARGET,SPELL_TYPE,SPELL_LEVEL,
        SPELL_EFFECT,SPELL_ID,SPELL_RESERVED
    );

    /** Includes shared and kind-specific fields, including reserved ranges. */
    public static List<Field> fieldsFor(Kind kind) {
        if (kind == null) throw new IllegalArgumentException("Missing entity kind");
        List<Field> fields = new ArrayList<>();
        for (Field f : FIELDS) if (f.kind == null || f.kind == kind) fields.add(f);
        return List.copyOf(fields);
    }

    /** Rejects overlapping fields, unassigned bytes, or out-of-bounds fields. */
    public static void verifyLayout(Kind kind) {
        boolean[] occupied = new boolean[RECORD_SIZE];
        for (Field f : fieldsFor(kind)) {
            for (int i = f.offset; i < f.offset + f.length; i++) {
                if (occupied[i]) throw new IllegalStateException(
                    "Overlapping v5 " + kind + " field at 0x" + Integer.toHexString(i) + ": " + f);
                occupied[i] = true;
            }
        }
        for (int i = 0; i < occupied.length; i++)
            if (!occupied[i]) throw new IllegalStateException(
                "Unassigned v5 " + kind + " byte at 0x" + Integer.toHexString(i));
    }

    static { for (Kind kind : Kind.values()) verifyLayout(kind); }
}
