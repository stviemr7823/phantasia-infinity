// phantasia-core/src/main/java/com/phantasia/core/logic/CombatAI.java
package com.phantasia.core.logic;

import com.phantasia.core.model.*;

import java.util.List;
import java.util.Random;

/**
 * Engine-blind AI for assigning combat actions to all combatants.
 *
 * Previously this logic lived only in CombatConsoleRunner, forcing each
 * frontend to either duplicate it or leave actions unassigned. By moving
 * it here, every frontend (console, JME, LibGDX) calls the same methods
 * and gets consistent, job-aware behaviour.
 *
 * DESIGN:
 *   - assignPartyActions()   — job-aware AI for player characters
 *   - assignMonsterActions() — simple random-target AI for monsters
 *
 * Both methods only set actions on living combatants and are safe to
 * call at the start of every round before CombatManager.runRound().
 *
 * USAGE (any frontend):
 *   CombatAI.assignPartyActions(party, enemies);
 *   CombatAI.assignMonsterActions(enemies, party);
 *   RoundResult result = combatManager.runRound();
 */
public final class CombatAI {

    private static final Random RNG = new Random();

    private CombatAI() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Assigns actions to every living party member using job-aware heuristics:
     *
     *   WIZARD  — casts a damage spell if MP >= 3, otherwise attacks
     *   PRIEST  — heals the most-wounded ally below 50% HP if MP >= 3,
     *             otherwise attacks
     *   FIGHTER — always SLASH on the first living enemy
     *   THIEF   — THRUST on a random living enemy
     *   MONK    — ATTACK on a random living enemy
     *   RANGER  — ATTACK on a random living enemy
     *
     * Does nothing if there are no living enemies.
     */
    public static void assignPartyActions(List<PlayerCharacter> party,
                                          List<Monster>         enemies) {
        List<Monster> alive = enemies.stream()
                .filter(Entity::isAlive)
                .toList();
        if (alive.isEmpty()) return;

        for (PlayerCharacter pc : party) {
            if (!pc.isAlive()) continue;

            Job job = Job.fromValue(pc.getJob());

            switch (job) {
                case WIZARD -> {
                    if (pc.getStat(Stat.MAGIC_POWER) >= 3) {
                        pc.setAction(Action.CAST);
                        pc.setSelectedSpellId(1);
                        pc.setPrimaryTarget(alive.get(RNG.nextInt(alive.size())));
                    } else {
                        pc.setAction(Action.ATTACK);
                        pc.setPrimaryTarget(alive.get(RNG.nextInt(alive.size())));
                    }
                }
                case PRIEST -> {
                    PlayerCharacter wounded = mostWounded(party);
                    if (wounded != null
                            && pc.getStat(Stat.MAGIC_POWER) >= 3
                            && wounded.getStat(Stat.HP) * 2
                               < wounded.getStat(Stat.MAX_HP)) {
                        pc.setAction(Action.CAST);
                        pc.setSelectedSpellId(2);
                        pc.setPrimaryTarget(wounded);
                    } else {
                        pc.setAction(Action.ATTACK);
                        pc.setPrimaryTarget(alive.get(RNG.nextInt(alive.size())));
                    }
                }
                case FIGHTER -> {
                    pc.setAction(Action.SLASH);
                    pc.setPrimaryTarget(alive.get(0));
                }
                case THIEF -> {
                    pc.setAction(Action.THRUST);
                    pc.setPrimaryTarget(alive.get(RNG.nextInt(alive.size())));
                }
                default -> {
                    // MONK, RANGER, and any future jobs fall back to a basic attack
                    pc.setAction(Action.ATTACK);
                    pc.setPrimaryTarget(alive.get(RNG.nextInt(alive.size())));
                }
            }
        }
    }

    /**
     * Assigns a basic ATTACK action to every living monster, targeting a
     * random living party member.
     *
     * Does nothing if there are no living party members.
     */
    public static void assignMonsterActions(List<Monster>         enemies,
                                            List<PlayerCharacter> party) {
        List<PlayerCharacter> alive = party.stream()
                .filter(Entity::isAlive)
                .toList();
        if (alive.isEmpty()) return;

        for (Monster m : enemies) {
            if (!m.isAlive()) continue;
            m.setAction(Action.ATTACK);
            m.setPrimaryTarget(alive.get(RNG.nextInt(alive.size())));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the living party member with the lowest HP percentage,
     * or null if everyone is dead.
     */
    private static PlayerCharacter mostWounded(List<PlayerCharacter> party) {
        PlayerCharacter worst    = null;
        int             lowestPct = 101;

        for (PlayerCharacter pc : party) {
            if (!pc.isAlive()) continue;
            int maxHp = pc.getStat(Stat.MAX_HP);
            if (maxHp == 0) continue;
            int pct = pc.getStat(Stat.HP) * 100 / maxHp;
            if (pct < lowestPct) {
                lowestPct = pct;
                worst     = pc;
            }
        }
        return worst;
    }
}
