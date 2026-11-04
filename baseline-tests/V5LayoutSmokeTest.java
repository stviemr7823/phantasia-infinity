package com.phantasia.baseline;

import com.phantasia.core.model.DataCoreV5;
import com.phantasia.core.model.DataLayoutV5;
import com.phantasia.core.model.DataLayoutV5.Kind;
import java.util.Arrays;

/** Run without any test framework, alongside the current April smoke test. */
public final class V5LayoutSmokeTest {
    private static int passed;
    private static void ok(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        passed++;
    }
    private static void rejects(Runnable action, String description) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        ok(rejected, description);
    }
    public static void main(String[] args) {
        for (Kind kind : Kind.values()) {
            DataLayoutV5.verifyLayout(kind);
            ok(DataLayoutV5.fieldsFor(kind).stream().mapToInt(DataLayoutV5.Field::length).sum() == 64,
                kind + " occupies exactly 64 bytes");
            DataCoreV5 blank = new DataCoreV5(kind);
            byte[] serialized = blank.toBytes();
            ok(serialized.length == 64 && serialized[0x0f] == kind.code, kind + " has tagged zero-initialized record");
            ok(Arrays.equals(serialized, DataCoreV5.fromBytes(serialized).toBytes()), kind + " round trip");
        }
        DataCoreV5 pc = new DataCoreV5(Kind.PLAYER);
        pc.setName("Alaric");
        ok("Alaric".equals(pc.name()), "ASCII name round trip");
        pc.setRecordId(0x12345678L);
        byte[] first = pc.toBytes();
        ok((first[0x10] & 0xff) == 0x12 && (first[0x13] & 0xff) == 0x78,
            "u32 big-endian record ID");
        pc.writeU16(DataLayoutV5.PC_MP, 0x1234);
        pc.writeU16(DataLayoutV5.PC_MAX_MP, 0xBEEF);
        ok(pc.readU16(DataLayoutV5.PC_MP) == 0x1234 && pc.readU16(DataLayoutV5.PC_MAX_MP) == 0xBEEF,
            "MP fields independent");
        pc.writeU32(DataLayoutV5.PC_XP, 0xBADC0FFEL);
        pc.writeU8(DataLayoutV5.PC_WEAPON, 217);
        pc.writeIndexedU8(DataLayoutV5.PC_INVENTORY, 9, 172);
        ok(pc.readU32(DataLayoutV5.PC_XP) == 0xBADC0FFEL && pc.readU8(DataLayoutV5.PC_WEAPON) == 217,
            "XP does not collide with weapon");
        ok(pc.readIndexedU8(DataLayoutV5.PC_INVENTORY, 9) == 172, "inventory slot ten");
        pc.toBytes()[DataLayoutV5.PC_WEAPON.offset()] = 0;
        ok(pc.readU8(DataLayoutV5.PC_WEAPON) == 217, "defensive byte copy");
        rejects(() -> pc.readU16(DataLayoutV5.MON_HP), "wrong kind rejected");
        rejects(() -> pc.writeU16(DataLayoutV5.PC_XP, 4), "wrong width rejected");
        rejects(() -> pc.writeU8(DataLayoutV5.PC_STRENGTH, 256), "overflow rejected");
        rejects(() -> pc.writeIndexedU8(DataLayoutV5.PC_INVENTORY, 10, 5), "out of range slot rejected");
        rejects(() -> pc.setName("sixteen-char-name"), "long name rejected");
        rejects(() -> pc.setName("Caf\u00e9"), "non-ASCII name rejected");
        rejects(() -> DataCoreV5.fromBytes(new byte[48]), "old record rejected");
        byte[] invalid = pc.toBytes();
        invalid[0x16] = 1;
        byte[] invalidHeader = invalid;
        rejects(() -> DataCoreV5.fromBytes(invalidHeader), "nonzero reserved header rejected");
        invalid = pc.toBytes();
        invalid[0x3A] = 1;
        byte[] invalidTail = invalid;
        rejects(() -> DataCoreV5.fromBytes(invalidTail), "nonzero player reserved byte rejected");
        invalid = pc.toBytes();
        invalid[0x0F] = 99;
        byte[] invalidType = invalid;
        rejects(() -> DataCoreV5.fromBytes(invalidType), "unknown kind rejected");
        ok(Arrays.equals(pc.toBytes(), DataCoreV5.fromBytes(pc.toBytes()).toBytes()), "populated round trip");
        System.out.println("PASS v5 layout checks=" + passed + " (additive schema; runtime remains v4)");
    }
}
