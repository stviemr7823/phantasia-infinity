// phantasia-core/src/main/java/com/phantasia/core/model/DataCore.java
package com.phantasia.core.model;

import java.nio.charset.StandardCharsets;

/**
 * The 64-byte binary soul of every game entity.
 *
 * <p>A compact, fixed-size byte array whose interpretation depends on
 * the calling domain (PLAYER, MONSTER, or SPELL). All multi-byte values
 * are stored big-endian. Layout constants live in {@link DataLayout}.</p>
 *
 * <h3>Size change — v4</h3>
 * The record was expanded from 48 to 64 bytes. Bytes 0–47 are layout-
 * identical to the v3 format. Bytes 48–63 are the new expansion zone
 * (see {@link DataLayout} for field definitions). Records loaded from
 * older saves are zero-padded to 64 bytes by {@link SaveManager}.
 *
 * <h3>Thread safety</h3>
 * Not thread-safe — mutate only from the game loop thread.
 */
public class DataCore {

    private final byte[] bytes;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a DataCore from an existing byte array.
     * The array must be exactly {@link DataLayout#RECORD_SIZE} (64) bytes.
     *
     * @throws IllegalArgumentException if the array is not exactly 64 bytes
     */
    public DataCore(byte[] initialData) {
        if (initialData.length != DataLayout.RECORD_SIZE) {
            throw new IllegalArgumentException(
                    "DataCore must be exactly " + DataLayout.RECORD_SIZE
                            + " bytes (got " + initialData.length + ").");
        }
        this.bytes = initialData;
    }

    /**
     * Creates a blank (all-zero) DataCore.
     * Useful for constructing new characters from scratch.
     */
    public DataCore() {
        this.bytes = new byte[DataLayout.RECORD_SIZE];
    }

    // -------------------------------------------------------------------------
    // 8-bit stat accessors
    // -------------------------------------------------------------------------

    /** Returns the unsigned byte value at {@code offset}. */
    public int getStat(int offset) {
        return Byte.toUnsignedInt(bytes[offset]);
    }

    /** Writes {@code value} clamped to [0, 255] at {@code offset}. */
    public void setStat(int offset, int value) {
        bytes[offset] = (byte) Math.clamp(value, 0, 255);
    }

    // -------------------------------------------------------------------------
    // 16-bit (big-endian) accessors
    // -------------------------------------------------------------------------

    /** Returns the unsigned 16-bit big-endian value at {@code offset}. */
    public int getShort(int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    /** Writes {@code value} clamped to [0, 65535] as big-endian at {@code offset}. */
    public void setShort(int offset, int value) {
        int clamped = Math.clamp(value, 0, 65535);
        bytes[offset]     = (byte) (clamped >> 8);
        bytes[offset + 1] = (byte) (clamped & 0xFF);
    }

    // -------------------------------------------------------------------------
    // Named field accessors — SHARED
    // -------------------------------------------------------------------------

    /** Returns the entity name, trimmed of padding spaces. */
    public String getName() {
        return new String(bytes, DataLayout.NAME, DataLayout.NAME_LEN,
                StandardCharsets.US_ASCII).trim();
    }

    /** Writes the entity name, space-padded to {@link DataLayout#NAME_LEN}. */
    public void setName(String name) {
        String padded = String.format("%-" + DataLayout.NAME_LEN + "s", name)
                .substring(0, DataLayout.NAME_LEN);
        byte[] nameBytes = padded.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, bytes, DataLayout.NAME, DataLayout.NAME_LEN);
    }

    // -------------------------------------------------------------------------
    // Named field accessors — PLAYER domain
    // -------------------------------------------------------------------------

    /** Returns the player's experience points (16-bit). */
    public int getXP() {
        return getShort(DataLayout.PC_XP);
    }

    /** Sets the player's experience points (clamped to 0–65535). */
    public void setXP(int value) {
        setShort(DataLayout.PC_XP, value);
    }

    // ── Expansion zone (v4, 0x30–0x3F) ───────────────────────────────────────

    /**
     * Returns the portrait sprite index (0–31).
     * Returns 0 (default portrait) on records loaded from v1–v3 saves.
     */
    public int getPortraitId() {
        return getStat(DataLayout.PC_PORTRAIT_ID);
    }

    /** Sets the portrait sprite index (0–31). */
    public void setPortraitId(int id) {
        setStat(DataLayout.PC_PORTRAIT_ID, Math.clamp(id, 0, 31));
    }

    /**
     * Returns the personality trait bitfield.
     * Reads 0 on records from v1–v3 saves (safe default = no traits).
     */
    public int getTraits() {
        return getStat(DataLayout.PC_TRAITS);
    }

    /** Sets the personality trait bitfield. */
    public void setTraits(int traits) {
        setStat(DataLayout.PC_TRAITS, traits);
    }

    /**
     * Returns the resonance stat (16-bit).
     * Reads 0 on records from v1–v3 saves.
     */
    public int getResonance() {
        return getShort(DataLayout.PC_RESONANCE);
    }

    /** Sets the resonance stat (clamped to 0–65535). */
    public void setResonance(int value) {
        setShort(DataLayout.PC_RESONANCE, value);
    }

    /**
     * Returns the item ID in the extended inventory slot {@code slot}
     * (0 or 1, corresponding to the 9th and 10th item slots).
     * Returns 0 (empty) on records from v1–v3 saves.
     */
    public int getExtendedInventorySlot(int slot) {
        if (slot < 0 || slot >= DataLayout.PC_EXTENDED_INV_LEN)
            throw new IllegalArgumentException("Extended inventory slot out of range: " + slot);
        return getStat(DataLayout.PC_EXTENDED_INV + slot);
    }

    /** Sets the item ID in an extended inventory slot (0 or 1). */
    public void setExtendedInventorySlot(int slot, int itemId) {
        if (slot < 0 || slot >= DataLayout.PC_EXTENDED_INV_LEN)
            throw new IllegalArgumentException("Extended inventory slot out of range: " + slot);
        setStat(DataLayout.PC_EXTENDED_INV + slot, itemId);
    }

    // -------------------------------------------------------------------------
    // Limb status (packed bitfield)
    // -------------------------------------------------------------------------

    /**
     * Reads the 2-bit status for limb {@code limbIndex} (0–5).
     * Limbs: 0=Head, 1=LtArm, 2=RtArm, 3=Torso, 4=LtLeg, 5=RtLeg.
     * Packed across bytes 0x1D–0x1E.
     */
    public int getLimbBitStatus(int limbIndex) {
        int packed = ((bytes[0x1D] & 0xFF) | ((bytes[0x1E] & 0xFF) << 8));
        return (packed >> (limbIndex * 2)) & 0x03;
    }

    /**
     * Writes the 2-bit status for limb {@code limbIndex} (0–5).
     */
    public void setLimbBitStatus(int limbIndex, int status) {
        int packed = ((bytes[0x1D] & 0xFF) | ((bytes[0x1E] & 0xFF) << 8));
        int mask   = ~(0x03 << (limbIndex * 2));
        packed     = (packed & mask) | ((status & 0x03) << (limbIndex * 2));
        bytes[0x1D] = (byte)  (packed & 0xFF);
        bytes[0x1E] = (byte) ((packed >> 8) & 0xFF);
    }

    // -------------------------------------------------------------------------
    // Raw access
    // -------------------------------------------------------------------------

    /**
     * Returns a direct reference to the underlying byte array.
     * Treat as read-only unless you know exactly what you are doing.
     * Used by {@link SaveManager} for serialization.
     */
    public byte[] getRawBytes() {
        return bytes;
    }

    /**
     * Alias for {@link #getRawBytes()} — preserved for older callers.
     */
    public byte[] getRawData() {
        return bytes;
    }
}
