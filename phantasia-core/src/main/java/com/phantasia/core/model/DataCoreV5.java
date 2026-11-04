package com.phantasia.core.model;

import com.phantasia.core.model.DataLayoutV5.Field;
import com.phantasia.core.model.DataLayoutV5.Kind;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Typed v5 record, separate from the live v4 DataCore until migration.
 * Owns its 64 bytes; callers only receive defensive byte copies.
 */
public final class DataCoreV5 {
    private final byte[] bytes;

    public DataCoreV5(Kind kind) {
        Objects.requireNonNull(kind, "kind");
        bytes = new byte[DataLayoutV5.RECORD_SIZE];
        bytes[DataLayoutV5.KIND.offset()] = (byte) kind.code;
    }

    private DataCoreV5(byte[] data) { bytes = data.clone(); }

    public static DataCoreV5 fromBytes(byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length != DataLayoutV5.RECORD_SIZE)
            throw new IllegalArgumentException("v5 DataCore requires 64 bytes, got " + data.length);
        DataCoreV5 out = new DataCoreV5(data);
        Kind kind = out.kind();
        for (int i = 0; i < DataLayoutV5.NAME.length(); i++) {
            int c = out.bytes[i] & 0xFF;
            if (c != 0 && (c < 0x20 || c > 0x7E))
                throw new IllegalArgumentException("Invalid v5 ASCII name byte at " + i);
        }
        for (Field f : DataLayoutV5.fieldsFor(kind)) {
            if (f.reserved()) {
                for (int i = f.offset(); i < f.offset() + f.length(); i++)
                    if (out.bytes[i] != 0)
                        throw new IllegalArgumentException("Nonzero reserved byte in " + kind
                            + " at 0x" + Integer.toHexString(i));
            }
        }
        return out;
    }

    public Kind kind() { return Kind.fromCode(bytes[DataLayoutV5.KIND.offset()] & 0xFF); }

    public byte[] toBytes() { return bytes.clone(); }

    public String name() {
        byte[] value = Arrays.copyOfRange(bytes, 0, DataLayoutV5.NAME.length());
        int end = value.length;
        while (end > 0 && (value[end-1] == 0 || value[end-1] == 0x20)) end--;
        return new String(value, 0, end, StandardCharsets.US_ASCII);
    }

    public void setName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.length() > DataLayoutV5.NAME.length())
            throw new IllegalArgumentException("v5 name exceeds 15 characters");
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch < 0x20 || ch > 0x7E)
                throw new IllegalArgumentException("v5 name requires printable ASCII");
        }
        Arrays.fill(bytes, DataLayoutV5.NAME.offset(), DataLayoutV5.NAME.length(), (byte) ' ');
        byte[] src = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, bytes, 0, src.length);
    }

    public long recordId() { return readU32(DataLayoutV5.RECORD_ID); }
    public void setRecordId(long value) { writeU32(DataLayoutV5.RECORD_ID, value); }
    public int sharedFlags() { return readU16(DataLayoutV5.SHARED_FLAGS); }
    public void setSharedFlags(int value) { writeU16(DataLayoutV5.SHARED_FLAGS, value); }

    public int readU8(Field field) {
        check(field, 1);
        return bytes[field.offset()] & 0xFF;
    }
    public void writeU8(Field field, int value) {
        check(field, 1);
        if (value < 0 || value > 255) throw new IllegalArgumentException("u8 out of range");
        bytes[field.offset()] = (byte) value;
    }
    public int readU16(Field field) {
        check(field, 2);
        return ((bytes[field.offset()] & 0xFF) << 8) | (bytes[field.offset()+1] & 0xFF);
    }
    public void writeU16(Field field, int value) {
        check(field, 2);
        if (value < 0 || value > 0xFFFF) throw new IllegalArgumentException("u16 out of range");
        bytes[field.offset()] = (byte) (value >>> 8);
        bytes[field.offset()+1] = (byte) value;
    }
    public long readU32(Field field) {
        check(field, 4);
        int o = field.offset();
        return ((long)(bytes[o] & 0xFF) << 24) | ((long)(bytes[o+1] & 0xFF) << 16)
            | ((long)(bytes[o+2] & 0xFF) << 8) | (bytes[o+3] & 0xFFL);
    }
    public void writeU32(Field field, long value) {
        check(field, 4);
        if (value < 0 || value > 0xFFFFFFFFL) throw new IllegalArgumentException("u32 out of range");
        int o = field.offset();
        bytes[o]   = (byte) (value >>> 24);
        bytes[o+1] = (byte) (value >>> 16);
        bytes[o+2] = (byte) (value >>> 8);
        bytes[o+3] = (byte) value;
    }
    public int readIndexedU8(Field field, int slot) {
        checkIndexed(field, slot);
        return bytes[field.offset()+slot] & 0xFF;
    }
    public void writeIndexedU8(Field field, int slot, int value) {
        checkIndexed(field, slot);
        if (value < 0 || value > 255) throw new IllegalArgumentException("u8 out of range");
        bytes[field.offset()+slot] = (byte) value;
    }

    private void checkIndexed(Field field, int slot) {
        checkDomain(field);
        if (field != DataLayoutV5.PC_INVENTORY || slot < 0 || slot >= field.length())
            throw new IllegalArgumentException("Invalid indexed field or slot: " + field);
    }
    private void check(Field field, int width) {
        checkDomain(field);
        if (field.length() != width)
            throw new IllegalArgumentException("Expected width " + width + " for " + field);
    }
    private void checkDomain(Field field) {
        Objects.requireNonNull(field, "field");
        if (field.reserved() || field == DataLayoutV5.NAME || field == DataLayoutV5.KIND)
            throw new IllegalArgumentException("Field not writable/readable through numeric accessors: " + field);
        if (field.kind() != null && field.kind() != kind())
            throw new IllegalArgumentException("Cannot access " + field.kind() + " field on " + kind() + " record: " + field);
    }
}
