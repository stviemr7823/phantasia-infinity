package com.phantasia.core.model;

import com.phantasia.core.model.PlayerStateV5.Attribute;
import java.util.Objects;

/**
 * Deterministic native-v5 character creation. The caller supplies starting
 * attributes and resources; no legacy DataCore or random stat generation is
 * involved. Story-specific creation rules belong in a later game workflow.
 */
public final class PlayerCreationV5 {
    private PlayerCreationV5() {}

    /**
     * Creates a level-one character with full HP/MP, zero experience, and
     * initially empty equipment and inventory.
     *
     * @param recordId 0 is permitted until persistent storage assigns an ID
     */
    public static PlayerStateV5 create(
            String name, long recordId, Job job, int raceId,
            int strength, int agility, int mind, int maxHp, int maxMp) {
        Objects.requireNonNull(job, "job");
        if (maxHp < 1 || maxHp > 65535)
            throw new IllegalArgumentException("Starting max HP must be 1..65535");
        if (maxMp < 0 || maxMp > 65535)
            throw new IllegalArgumentException("Starting max MP must be 0..65535");

        PlayerStateV5 player = PlayerStateV5.create(name, recordId);
        player.setJobId(job.getBitValue());
        player.setRaceId(raceId);
        player.setLevel(1);
        player.setAttribute(Attribute.STRENGTH, strength);
        player.setAttribute(Attribute.AGILITY, agility);
        player.setAttribute(Attribute.MIND, mind);
        player.setMaxHp(maxHp);
        player.setHp(maxHp);
        player.setMaxMp(maxMp);
        player.setMp(maxMp);
        // XP, status flags, equipment and inventory retain their zero defaults.
        return player;
    }
}
