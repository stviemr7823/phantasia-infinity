package com.phantasia.core.model;

import com.phantasia.core.model.DataLayoutV5.Kind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataCoreV5Test {
    @Test void completeNonoverlappingLayouts() {
        for (Kind kind : Kind.values()) {
            DataLayoutV5.verifyLayout(kind);
            assertEquals(64, DataLayoutV5.fieldsFor(kind).stream()
                .mapToInt(DataLayoutV5.Field::length).sum());
        }
    }

    @Test void recordRoundTripAndIsolation() {
        DataCoreV5 p = new DataCoreV5(Kind.PLAYER);
        p.setName("Alaric");
        p.setRecordId(0xFEDCBA98L);
        p.writeU8(DataLayoutV5.PC_STRENGTH, 12);
        p.writeU16(DataLayoutV5.PC_MP, 0x1234);
        p.writeU16(DataLayoutV5.PC_MAX_MP, 0xFEDC);
        p.writeU32(DataLayoutV5.PC_XP, 0xAABBCCDDL);
        p.writeU8(DataLayoutV5.PC_WEAPON, 77);
        p.writeIndexedU8(DataLayoutV5.PC_INVENTORY, 9, 251);
        byte[] b = p.toBytes();
        assertEquals(0xFE, b[0x10] & 0xFF);
        assertEquals(0x98, b[0x13] & 0xFF);
        DataCoreV5 q = DataCoreV5.fromBytes(b);
        assertEquals(Kind.PLAYER, q.kind());
        assertEquals("Alaric", q.name());
        assertEquals(0xFEDCBA98L, q.recordId());
        assertEquals(0x1234, q.readU16(DataLayoutV5.PC_MP));
        assertEquals(0xFEDC, q.readU16(DataLayoutV5.PC_MAX_MP));
        assertEquals(0xAABBCCDDL, q.readU32(DataLayoutV5.PC_XP));
        assertEquals(77, q.readU8(DataLayoutV5.PC_WEAPON));
        assertEquals(251, q.readIndexedU8(DataLayoutV5.PC_INVENTORY, 9));
        b[0x2C] = 0;
        assertEquals(77, q.readU8(DataLayoutV5.PC_WEAPON));
    }

    @Test void rejectsIllegalAccessAndCorruptRecords() {
        DataCoreV5 p = new DataCoreV5(Kind.PLAYER);
        assertThrows(IllegalArgumentException.class,
            () -> p.readU16(DataLayoutV5.MON_HP));
        assertThrows(IllegalArgumentException.class,
            () -> p.writeU16(DataLayoutV5.PC_XP, 1));
        assertThrows(IllegalArgumentException.class,
            () -> p.writeU8(DataLayoutV5.PC_STRENGTH, -1));
        assertThrows(IllegalArgumentException.class,
            () -> p.writeIndexedU8(DataLayoutV5.PC_INVENTORY, 10, 1));
        assertThrows(IllegalArgumentException.class,
            () -> p.setName("sixteen-char-name"));
        assertThrows(IllegalArgumentException.class,
            () -> p.setName("Caf\u00e9"));
        assertThrows(IllegalArgumentException.class,
            () -> DataCoreV5.fromBytes(new byte[48]));
        byte[] b = p.toBytes();
        b[0x16] = 1;
        assertThrows(IllegalArgumentException.class, () -> DataCoreV5.fromBytes(b));
        b[0x16] = 0;
        b[0x3A] = 1;
        assertThrows(IllegalArgumentException.class, () -> DataCoreV5.fromBytes(b));
        b[0x3A] = 0;
        b[0x0F] = (byte) 0xFF;
        assertThrows(IllegalArgumentException.class, () -> DataCoreV5.fromBytes(b));
    }
}
