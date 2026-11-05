package com.phantasia.core.model;

import com.phantasia.core.model.PlayerStateV5.Attribute;
import java.util.Objects;

/**
 * Explicit, partial legacy-v4 to v5 player snapshot for migration testing.
 *
 * This is NOT the production save migration. Legacy equipment slots conflict
 * with XP (weapon) and other legacy meanings; they are intentionally NOT
 * transferred. Conversion of living gameplay state will require a separate
 * audited policy before SaveManager is switched to v5.
 */
public final class LegacyPlayerV5Adapter {
    private LegacyPlayerV5Adapter() {}

    /**
     * Copies only unambiguous fields. Callers must retain the original v4
     * record until equipment and status migration policies are settled.
     */
    public static PlayerStateV5 snapshotUnambiguousFields(
            PlayerCharacter legacy, long recordId) {
        Objects.requireNonNull(legacy, "legacy");
        if (legacy.isUndead())
            throw new IllegalArgumentException(
                    "Legacy undead state has no approved v5 status mapping");

        PlayerStateV5 result = PlayerStateV5.create(legacy.getName(), recordId);
        result.setAttribute(Attribute.STRENGTH, legacy.getStat(Stat.STRENGTH));
        result.setAttribute(Attribute.AGILITY, legacy.getStat(Stat.DEXTERITY));
        result.setAttribute(Attribute.MIND, legacy.getStat(Stat.INTELLIGENCE));
        result.setJobId(legacy.getJob());
        result.setRaceId(legacy.getRace());
        result.setLevel(legacy.getLevel());
        result.setHp(legacy.getStat(Stat.HP));
        result.setMaxHp(legacy.getStat(Stat.MAX_HP));
        result.setMp(legacy.getStat(Stat.MAGIC_POWER));
        result.setMaxMp(legacy.getStat(Stat.MAX_MAGIC));
        result.setExperience(legacy.getExperience());

        DataCore record = new DataCore(legacy.toLegacyRecordBytes());
        result.setPortraitId(record.getPortraitId());
        for (int slot = 0; slot < DataLayout.PC_INVENTORY_LEN; slot++)
            result.setInventory(slot, legacy.getInventoryItemId(slot));
        for (int slot = 0; slot < DataLayout.PC_EXTENDED_INV_LEN; slot++)
            result.setInventory(DataLayout.PC_INVENTORY_LEN + slot,
                    record.getExtendedInventorySlot(slot));
        // Equipment, shared flags and status intentionally remain unset.
        return result;
    }
}
