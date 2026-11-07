package com.phantasia.core.data;

import com.phantasia.core.logic.ExperienceTable;
import com.phantasia.core.model.*;
import java.util.*;

/** One reward receipt per encounter. Re-entering the victory UI cannot award twice. */
public final class CombatRewards {
    public record Receipt(int experience, int gold, boolean goldClaimed) {}
    private final GameSession session;
    private final List<PlayerCharacter> party;
    private final List<Monster> enemies;
    private final int xp, gold;
    private boolean confirmed, goldClaimed;
    public CombatRewards(GameSession session, List<Monster> enemies) {
        this.session = Objects.requireNonNull(session); this.enemies = List.copyOf(enemies);
        if (enemies.isEmpty() || new HashSet<>(enemies).size() != enemies.size()) throw new IllegalArgumentException("Encounter needs distinct enemies");
        Set<PlayerCharacter> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        party = session.getParty().stream().filter(seen::add).toList();
        long experience = 0, treasure = 0;
        for (var enemy : enemies) { experience += enemy.getExperience(); treasure += enemy.getTreasure(); }
        if (experience < 0 || experience > Integer.MAX_VALUE - 65535L || treasure < 0 || treasure > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Encounter reward totals exceed supported range");
        xp = (int) experience; gold = (int) treasure;
    }
    public void validateVictory() {
        if (!confirmed && (enemies.stream().anyMatch(Monster::isAlive) || party.stream().noneMatch(PlayerCharacter::isActiveInParty)))
            throw new IllegalStateException("Rewards require a surviving party and defeated enemies");
    }
    public Receipt victory() {
        validateVictory();
        if (!confirmed) {
            confirmed = true;
            ExperienceTable.awardCombatXp(party, xp); // existing sharing/cap rules; no level-up
        }
        retryGold();
        return new Receipt(xp, gold, goldClaimed);
    }
    /** XP is already awarded on victory; a full purse defers only gold. */
    public boolean retryGold() {
        if (!confirmed || goldClaimed) return false;
        if (session.getLedger().getPartyGold() < 0 || (long) session.getLedger().getPartyGold() + gold > Integer.MAX_VALUE) {
            session.queueCombatReward(this); return false;
        }
        goldClaimed = true; session.removeCombatReward(this);
        session.getLedger().addGold(gold); // claim marked before reentrant notifications
        return true;
    }
}
