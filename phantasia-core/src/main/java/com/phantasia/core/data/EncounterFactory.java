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
 * Previously this logic lived in CombatConsoleRunner (a console demo) and was
 * duplicated in a stripped-down form in CombatDemoInitializer (the JME module).
 * It now lives here so any frontend — console, JME, future GUI — can call it
 * without re-implementing or copy-pasting character construction.
 *
 * USAGE:
 *   // In a console runner or JME initializer:
 *   List<PlayerCharacter> party   = EncounterFactory.generateParty();
 *   List<Monster>         enemies = EncounterFactory.generateEncounter();
 *   EncounterCondition    cond    = FormulaEngine.rollEncounterCondition();
 *
 *   // For a fixed test battle (deterministic, no RNG):
 *   List<PlayerCharacter> party   = EncounterFactory.buildTestParty();
 *   List<Monster>         enemies = EncounterFactory.buildTestEncounter();
 */
public final class EncounterFactory {

    private EncounterFactory() {}

    private static final Random RNG = new Random();

    // =========================================================================
    // Name pool (shared across all generation modes)
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
     * Stats are job-themed with small random variance so each run feels distinct.
     * Combat ranks are assigned automatically by job role.
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
     * Intelligence and Luck are populated so FormulaEngine formulas
     * have meaningful data to work with.
     */
    public static PlayerCharacter buildCharacter(String name, Job job) {
        DataCore core = new DataCore(new byte[DataLayout.RECORD_SIZE]);
        core.setName(name);

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

        core.setShort(DataLayout.PC_HP,          hp);
        core.setShort(DataLayout.PC_MAX_HP,       hp);
        core.setShort(DataLayout.PC_MAGIC_POWER,  mp);
        core.setShort(DataLayout.PC_MAX_MAGIC,    mp);
        core.setStat (DataLayout.PC_STRENGTH,     str);
        core.setStat (DataLayout.PC_INTELLIGENCE, intel);
        core.setStat (DataLayout.PC_DEXTERITY,    dex);
        core.setStat (DataLayout.PC_LUCK,         luck);
        core.setStat (DataLayout.PC_JOB,          job.getBitValue());
        core.setStat (DataLayout.PC_LEVEL,        level);
        core.setStat (DataLayout.BODY_STATUS,     1);

        return new PlayerCharacter(core);
    }

    // =========================================================================
    // Randomised encounter — 1–3 monster types from the bestiary
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
     * Generates a randomised enemy formation (1–3 monster types,
     * variable group size per type). XP values are stored on each monster.
     */
    public static List<Monster> generateEncounter() {
        int typeCount = 1;

        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < BESTIARY.length; i++) idx.add(i);
        Collections.shuffle(idx, RNG);

        List<Monster> all = new ArrayList<>();
        for (int t = 0; t < typeCount; t++) {
            Object[] row      = BESTIARY[idx.get(t)];
            String   name     = (String) row[0];
            int      hp       = (int)    row[1];
            int      maxGroup = (int)    row[2];
            int      xp       = (int)    row[3];
            int      count    = 1 + RNG.nextInt(maxGroup);

            for (int i = 0; i < count; i++) {
                String label = count > 1 ? name + " " + (char)('A' + i) : name;
                all.add(buildMonster(label, Math.max(1, hp + vary(3)), count, xp));
            }
        }
        return all;
    }

    /**
     * Builds a single monster DataCore. Public so callers (including tests)
     * can construct one-off monsters without going through generateEncounter().
     */
    public static Monster buildMonster(String name, int hp, int groupSize, int xp) {
        DataCore core = new DataCore(new byte[DataLayout.RECORD_SIZE]);
        core.setName(name);
        core.setStat (DataLayout.MON_HP,        hp);
        core.setStat (DataLayout.MON_MAX_SPAWN, groupSize);
        core.setShort(DataLayout.MON_XP,        xp);
        core.setStat (DataLayout.BODY_STATUS,   1);
        core.setShort(DataLayout.MON_TREASURE, Math.max(1, xp / 2));
        return new Monster(core);
    }

    // =========================================================================
    // Fixed test fixtures — deterministic, no RNG, good for demo and unit tests
    // =========================================================================

    /**
     * Returns a deterministic 2-member test party — useful for the JME demo
     * initializer and unit tests that need predictable starting state.
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
     * Returns a deterministic 2-monster test encounter.
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


    private static int dice(int sides) {
        return 1 + RNG.nextInt(sides);
    }

    private static int vary(int range) {
        return RNG.nextInt(range * 2 + 1) - range;
    }
}