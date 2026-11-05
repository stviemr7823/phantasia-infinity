// phantasia-core/src/main/java/com/phantasia/core/data/EncounterFactory.java
package com.phantasia.core.data;

import com.phantasia.core.model.*;
import com.phantasia.core.model.CombatRank;
import com.phantasia.core.logic.FormulaEngine;
import com.phantasia.core.logic.EncounterCondition;

import java.util.*;

/**
 * Single source of truth for generating randomised parties and enemy encounters.
 *
 * <h3>Party-size scaling (design doc §III)</h3>
 * {@link #generateEncounter(int)} scales both monster count and type variety
 * to the current party size using the formula:
 * <pre>
 *   targetCount = ceil(partySize × 1.5)
 *   typeCount   = max(1, min(3, partySize / 2))
 * </pre>
 * A solo adventurer fights 2 monsters of 1 type.
 * A full party of 6 fights 9 monsters spread across up to 3 types.
 * The no-arg {@link #generateEncounter()} defaults to party size 4
 * (a 6-monster encounter with 2 types) for callers that don't have
 * access to the current party.
 *
 * <h3>Usage</h3>
 * <pre>
 *   // Preferred — party-aware:
 *   List&lt;Monster&gt; enemies = EncounterFactory.generateEncounter(session.getParty());
 *
 *   // Legacy / fallback:
 *   List&lt;Monster&gt; enemies = EncounterFactory.generateEncounter();
 * </pre>
 */
public final class EncounterFactory {

    private EncounterFactory() {}

    private static final Random RNG = new Random();

    // =========================================================================
    // Name pool
    // =========================================================================

    private static final String[] NAME_POOL = {
            "Aldric", "Seraphine", "Korg",   "Bonzo",
            "Elenora","Zyla",      "Theron", "Mira",
            "Galen",  "Voss",      "Idra",   "Caelan"
    };

    // =========================================================================
    // Randomised party — one character per Job class
    // =========================================================================

    /**
     * Builds a randomised 6-member party, one character per Job.
     * Stats are job-themed with small random variance.
     */
    public static List<PlayerCharacter> generateParty() {
        List<String> names = new ArrayList<>(Arrays.asList(NAME_POOL));
        Collections.shuffle(names, RNG);

        Job[] jobs = { Job.FIGHTER, Job.WIZARD, Job.PRIEST,
                Job.MONK,    Job.RANGER,  Job.THIEF };

        List<PlayerCharacter> party = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            party.add(buildCharacter(names.get(i), jobs[i]));
        }

        for (PlayerCharacter pc : party) {
            Job job = Job.fromValue(pc.getJob());
            CombatRank rank = switch (job) {
                case FIGHTER, MONK  -> CombatRank.FRONT;
                case RANGER, THIEF  -> CombatRank.MIDDLE;
                case WIZARD, PRIEST -> CombatRank.BACK;
            };
            pc.setCombatRank(rank);
        }
        return party;
    }

    /**
     * Builds a single PlayerCharacter with job-appropriate stats.
     */
    public static PlayerCharacter buildCharacter(String name, Job job) {
        int hp, mp, str, intel, dex, luck, level;

        switch (job) {
            case FIGHTER -> {
                hp = 50 + dice(20); mp = 0;
                str = 16 + vary(4); intel = 4  + vary(3);
                dex = 12 + vary(4); luck  = 8  + vary(5);
                level = 5 + dice(5);
            }
            case WIZARD -> {
                hp = 18 + dice(10); mp = 28 + dice(14);
                str = 5  + vary(2); intel = 18 + vary(4);
                dex = 10 + vary(4); luck  = 10 + vary(5);
                level = 5 + dice(5);
            }
            case PRIEST -> {
                hp = 28 + dice(14); mp = 22 + dice(10);
                str = 8  + vary(3); intel = 14 + vary(4);
                dex = 10 + vary(3); luck  = 12 + vary(5);
                level = 5 + dice(5);
            }
            case MONK -> {
                hp = 38 + dice(14); mp = 8 + dice(8);
                str = 13 + vary(4); intel = 10 + vary(4);
                dex = 14 + vary(4); luck  = 12 + vary(5);
                level = 5 + dice(5);
            }
            case RANGER -> {
                hp = 32 + dice(14); mp = 10 + dice(8);
                str = 12 + vary(4); intel = 10 + vary(4);
                dex = 16 + vary(4); luck  = 14 + vary(5);
                level = 5 + dice(5);
            }
            case THIEF -> {
                hp = 25 + dice(12); mp = 4 + dice(5);
                str = 10 + vary(4); intel = 8  + vary(4);
                dex = 18 + vary(4); luck  = 18 + vary(5);
                level = 5 + dice(5);
            }
            default -> {
                hp = 30; mp = 10; str = 10; intel = 10;
                dex = 10; luck = 10; level = 5;
            }
        }

        return PlayerCharacter.fromLegacyCreation(
                name, job, hp, mp, str, intel, dex, luck, level);
    }

    // =========================================================================
    // Randomised encounter — party-size scaled
    // =========================================================================

    // { name, baseHp, maxGroupSize, xpReward }
    private static final Object[][] BESTIARY = {
            { "Giant Rat",    4,  3,   8 },
            { "Kobold",       5,  3,  10 },
            { "Skeleton",     7,  2,  18 },
            { "Orc Warrior", 10,  2,  28 },
            { "Dark Mage",    8,  2,  35 },
            { "Troll",       18,  1,  55 },
            { "Wyvern",      25,  1,  90 },
            { "Lich",        22,  1, 120 },
            { "Dragon King", 55,  1, 300 },
    };

    /**
     * Generates a party-scaled encounter from a live party list.
     * Counts only alive members for the scaling calculation —
     * a weakened party fights fewer monsters.
     *
     * @param party the current party (may be 1–6 members)
     */
    public static List<Monster> generateEncounter(List<PlayerCharacter> party) {
        int aliveCount = (int) party.stream()
                .filter(PlayerCharacter::isAlive)
                .count();
        return generateEncounter(Math.max(1, aliveCount));
    }

    /**
     * Generates a party-scaled encounter for the given party size.
     *
     * <h4>Scaling formula</h4>
     * <pre>
     *   targetCount = ceil(partySize × 1.5)
     *   typeCount   = max(1, min(3, partySize / 2))
     * </pre>
     *
     * <table border="1">
     *   <tr><th>Party</th><th>Monsters</th><th>Types</th></tr>
     *   <tr><td>1</td><td>2</td><td>1</td></tr>
     *   <tr><td>2</td><td>3</td><td>1</td></tr>
     *   <tr><td>3</td><td>5</td><td>1</td></tr>
     *   <tr><td>4</td><td>6</td><td>2</td></tr>
     *   <tr><td>5</td><td>8</td><td>2</td></tr>
     *   <tr><td>6</td><td>9</td><td>3</td></tr>
     * </table>
     *
     * @param partySize number of active party members (1–6)
     */
    public static List<Monster> generateEncounter(int partySize) {
        int clampedSize  = Math.max(1, Math.min(6, partySize));
        int targetCount  = (int) Math.ceil(clampedSize * 1.5);
        int typeCount    = Math.max(1, Math.min(3, clampedSize / 2));

        // Pick random distinct enemy types
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < BESTIARY.length; i++) indices.add(i);
        Collections.shuffle(indices, RNG);

        List<Monster> all = new ArrayList<>();
        int remaining = targetCount;

        for (int t = 0; t < typeCount && remaining > 0; t++) {
            Object[] row      = BESTIARY[indices.get(t)];
            String   name     = (String) row[0];
            int      baseHp   = (int)    row[1];
            int      maxGroup = (int)    row[2];
            int      xp       = (int)    row[3];

            // Last type takes all remaining slots; others take an even share
            // but never exceed the type's natural maxGroup per spawn
            int share = (t == typeCount - 1)
                    ? remaining
                    : Math.max(1, targetCount / typeCount);
            int count = Math.min(share, Math.min(remaining, maxGroup));

            for (int i = 0; i < count; i++) {
                String label = count > 1 ? name + " " + (char)('A' + i) : name;
                all.add(buildMonster(label, Math.max(1, baseHp + vary(3)), count, xp));
            }
            remaining -= count;
        }

        return all;
    }

    /**
     * Generates an encounter with a default party size of 4.
     * Use {@link #generateEncounter(List)} or {@link #generateEncounter(int)}
     * when the actual party is available.
     */
    public static List<Monster> generateEncounter() {
        return generateEncounter(4);
    }

    /**
     * Builds a single monster DataCore.
     */
    public static Monster buildMonster(String name, int hp, int groupSize, int xp) {
        DataCore core = new DataCore(new byte[DataLayout.RECORD_SIZE]);
        core.setName(name);
        core.setStat (DataLayout.MON_HP,        hp);
        core.setStat (DataLayout.MON_MAX_SPAWN, groupSize);
        core.setShort(DataLayout.MON_XP,        xp);
        core.setStat (DataLayout.BODY_STATUS,   1);
        core.setShort(DataLayout.MON_TREASURE,  Math.max(1, xp / 2));
        return new Monster(core);
    }

    // =========================================================================
    // Fixed test fixtures
    // =========================================================================

    /**
     * Deterministic 2-member test party. Useful for JME demo and unit tests.
     */
    public static List<PlayerCharacter> buildTestParty() {
        List<PlayerCharacter> party = new ArrayList<>();
        party.add(buildCharacter("Stephen", Job.FIGHTER));
        party.add(buildCharacter("Matt",    Job.WIZARD));
        for (PlayerCharacter pc : party) {
            Job job = Job.fromValue(pc.getJob());
            CombatRank rank = switch (job) {
                case FIGHTER, MONK  -> CombatRank.FRONT;
                case RANGER, THIEF  -> CombatRank.MIDDLE;
                case WIZARD, PRIEST -> CombatRank.BACK;
            };
            pc.setCombatRank(rank);
        }
        return party;
    }

    /**
     * Deterministic 2-monster test encounter.
     */
    public static List<Monster> buildTestEncounter() {
        List<Monster> monsters = new ArrayList<>();
        monsters.add(buildMonster("Orc A", 50, 2, 40));
        monsters.add(buildMonster("Orc B", 50, 2, 40));
        return monsters;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static int dice(int sides) { return 1 + RNG.nextInt(sides); }
    private static int vary(int range)  { return RNG.nextInt(range * 2 + 1) - range; }
}
