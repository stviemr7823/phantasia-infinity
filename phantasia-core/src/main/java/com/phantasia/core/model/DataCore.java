package com.phantasia.core.model;

import java.nio.charset.StandardCharsets;

public class DataCore {
    private final byte[] bytes;

    public DataCore(byte[] initialData) {
        if (initialData.length != 48) {
            throw new IllegalArgumentException("Soul must be exactly 48 bytes.");
        }
        this.bytes = initialData;
    }

    // --- 8-bit Stat Accessors ---
    public int getStat(int offset) {
        return Byte.toUnsignedInt(bytes[offset]);
    }

    public void setStat(int offset, int value) {
        bytes[offset] = (byte) Math.clamp(value, 0, 255);
    }

    public int getShort(int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    public void setShort(int offset, int value) {
        int clamped = Math.clamp(value, 0, 65535);
        bytes[offset]     = (byte) (clamped >> 8);
        bytes[offset + 1] = (byte) (clamped & 0xFF);
    }

    // --- 16-bit XP Accessors (Big Endian) ---
    // phantasia-core/src/main/java/com/phantasia/core/model/DataCore.java

    public String getName() {
        return new String(bytes, DataLayout.NAME, DataLayout.NAME_LEN,
                StandardCharsets.US_ASCII).trim();
    }

    public void setName(String name) {
        String padded = String.format("%-" + DataLayout.NAME_LEN + "s", name)
                .substring(0, DataLayout.NAME_LEN);
        byte[] nameBytes = padded.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, bytes, DataLayout.NAME, DataLayout.NAME_LEN);
    }

    public int getXP() {
        return getShort(DataLayout.PC_XP);
    }

    public void setXP(int value) {
        setShort(DataLayout.PC_XP, value);
    }

    public byte[] getRawData() {
        return bytes;
    }

    // phantasia-core/src/main/java/com/phantasia/core/model/DataCore.java

    /**
     * Reads a 2-bit status for a specific limb from a packed 16-bit block.
     * @param limbIndex 0-5 (Head, LtArm, RtArm, Torso, LtLeg, RtLeg)
     */
    public int getLimbBitStatus(int limbIndex) {
        // We store all 6 limbs in offsets 0x1D (bits 0-7) and 0x1E (bits 8-11)
        int packedData = ((bytes[0x1D] & 0xFF) | ((bytes[0x1E] & 0xFF) << 8));
        return (packedData >> (limbIndex * 2)) & 0x03; // Mask the 2 bits
    }

    public void setLimbBitStatus(int limbIndex, int status) {
        int packedData = ((bytes[0x1D] & 0xFF) | ((bytes[0x1E] & 0xFF) << 8));

        // Clear the old 2 bits and set the new ones
        int mask = ~(0x03 << (limbIndex * 2));
        packedData = (packedData & mask) | ((status & 0x03) << (limbIndex * 2));

        bytes[0x1D] = (byte) (packedData & 0xFF);
        bytes[0x1E] = (byte) ((packedData >> 8) & 0xFF);
    }

    public byte[] getRawBytes() {
        return bytes;
    }
}