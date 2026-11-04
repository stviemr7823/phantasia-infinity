// phantasia-core/src/main/java/com/phantasia/core/CombatConsoleRunner.java
package com.phantasia.core;

import com.phantasia.core.data.EncounterFactory;
import com.phantasia.core.data.SpellFactory;
import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;

import java.io.IOException;
import java.util.*;

/**
 * Console combat simulator — exercises the engine end-to-end.
 *
 * <h3>Run modes (command-line args)</h3>
 * <pre>
 *   (no args)   — full 6-member party vs. scaled encounter (interactive, replay prompt)
 *   solo        — 1 Fighter vs. 1 Kobold (validates solo/1v1 encounter scaling)
 *   duo         — 2-member test party vs. scaled encounter
 * </pre>
 *
 * <h3>Design doc requirement</h3>
 * The "Variable Party Configuration" design pivot requires verifying that
 * {@link EncounterFactory#generateEncounter(int)} scales correctly for
 * party sizes 1–6. The {@code solo} and {@code duo} modes provide
 * deterministic, readable output for this verification.
 */
public class CombatConsoleRunner {

    // =========================================================================
    // Entry point
    // =========================================================================

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0].toLowerCase() : "full";

        switch (mode) {
            case "solo" -> {
                rule('=', 60);
                System.out.println("  SOLO ENCOUNTER TEST  (party=1 → expected 2 monsters, 1 type)");
                rule('=', 60);
                runScaledBattle(1, false);
            }
            case "duo" -> {
                rule('=', 60);
                System.out.println("  DUO ENCOUNTER TEST  (party=2 → expected 3 monsters, 1 type)");
                rule('=', 60);
                runScaledBattle(2, false);
            }
            default -> {
                // Interactive full-party mode
                Scanner scanner = new Scanner(System.in);
                printBanner();
                boolean keepPlaying = true;
                while (keepPlaying) {
                    runBattle();
                    keepPlaying = promptReplay(scanner);
                }
                rule('=', 60);
                System.out.println("  Farewell, adventurer. May your deeds be remembered.");
                rule('=', 60);
                scanner.close();
            }
        }
    }

    // =========================================================================
    // Scaled-party battle — fixed party size for testing
    // =========================================================================

    /**
     * Runs a battle with a party of exactly {@code partySize} members.
     * Uses a deterministic test party so the output is repeatable.
     *
     * @param partySize  number of characters (1–6)
     * @param interactive  if true, prints a replay prompt after the battle
     */
    private static void runScaledBattle(int partySize, boolean interactive) {
        List<PlayerCharacter> party = buildSizedParty(partySize);
        List<Monster> enemies = EncounterFactory.generateEncounter(party);

        System.out.println();
        System.out.printf("  Party size: %d  →  Encounter size: %d monster(s)%n",
                partySize, enemies.size());

        printPartyRoster(party);
        printEnemyRoster(enemies);

        fightToConclusion(party, enemies);
    }

    // =========================================================================
    // Full random battle
    // =========================================================================

    private static void runBattle() {
        System.out.println();
        rule('=', 60);
        System.out.println("  GENERATING ENCOUNTER...");
        rule('=', 60);

        List<PlayerCharacter> party   = EncounterFactory.generateParty();
        // Party-aware encounter scaling — monsters scale to the party
        List<Monster>         enemies = EncounterFactory.generateEncounter(party);

        printPartyRoster(party);
        printEnemyRoster(enemies);

        EncounterCondition condition = FormulaEngine.rollEncounterCondition();
        System.out.println();
        rule('-', 60);
        System.out.println("  " + condition.announcement());
        rule('-', 60);

        fightToConclusion(party, enemies);
    }

    // =========================================================================
    // Combat loop (shared by all modes)
    // =========================================================================

    private static void fightToConclusion(List<PlayerCharacter> party,
                                          List<Monster> enemies) {
        EncounterCondition condition = FormulaEngine.rollEncounterCondition();

        SpellFactory spellFactory = new SpellFactory();
        try {
            spellFactory.loadSpells();
        } catch (IOException e) {
            System.err.println("  [WARN] Could not load spells.dat — "
                    + "spells will fail gracefully. " + e.getMessage());
        }

        int xpPool = enemies.stream().mapToInt(Entity::getExperience).sum();

        CombatNarrator narrator = new CombatNarrator();
        CombatManager  manager  = new CombatManager(party, enemies, spellFactory, condition);

        final int ROUND_CAP = 50;

        while (!manager.isCombatOver()) {
            CombatAI.assignPartyActions(party, enemies);
            CombatAI.assignMonsterActions(enemies, party);

            RoundResult result = manager.runRound();

            printRoundHeader(result);
            printNarratorLines(narrator, result);

            if (result.isCombatOver()) break;

            printStatusBar(party, enemies);

            if (result.roundNumber() >= ROUND_CAP) {
                System.out.println();
                System.out.println("  !! Battle raged " + ROUND_CAP
                        + " rounds — combatants disengage !!");
                break;
            }
        }

        printBattleSummary(party, enemies, xpPool);
    }

    // =========================================================================
    // Party builders
    // =========================================================================

    /**
     * Builds a deterministic party of exactly {@code size} members.
     * Uses canonical job order (Fighter → Wizard → Priest → Monk → Ranger → Thief)
     * so the output is repeatable regardless of RNG.
     */
    private static List<PlayerCharacter> buildSizedParty(int size) {
        Job[] order = { Job.FIGHTER, Job.WIZARD, Job.PRIEST,
                Job.MONK,    Job.RANGER,  Job.THIEF };
        String[] names = { "Stephen", "Seraphine", "Galen",
                "Korg",    "Mira",       "Zyla"  };

        int count = Math.max(1, Math.min(6, size));
        List<PlayerCharacter> party = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            PlayerCharacter pc = EncounterFactory.buildCharacter(names[i], order[i]);
            CombatRank rank = switch (order[i]) {
                case FIGHTER, MONK  -> CombatRank.FRONT;
                case RANGER, THIEF  -> CombatRank.MIDDLE;
                case WIZARD, PRIEST -> CombatRank.BACK;
            };
            pc.setCombatRank(rank);
            party.add(pc);
        }
        return party;
    }

    // =========================================================================
    // Round display
    // =========================================================================

    private static void printRoundHeader(RoundResult result) {
        String order = result.events().stream()
                .filter(e -> e instanceof CombatEvent.RoundHeader)
                .map(e -> String.join(" > ",
                        ((CombatEvent.RoundHeader) e).initiativeOrder()))
                .findFirst()
                .orElse("—");
        System.out.println();
        rule('-', 60);
        System.out.printf("  ROUND %-3d   Initiative: %s%n",
                result.roundNumber(), order);
        rule('-', 60);
    }

    private static void printNarratorLines(CombatNarrator narrator,
                                           RoundResult    result) {
        for (String line : narrator.narrateRound(result)) {
            if (line != null) System.out.println("    " + line);
        }
    }

    private static void printStatusBar(List<PlayerCharacter> party,
                                       List<Monster>         enemies) {
        System.out.println();
        System.out.print("  PARTY  ");
        for (PlayerCharacter pc : party) {
            if (!pc.isAlive())
                System.out.printf("| %s:DEAD ", pc.getName());
            else
                System.out.printf("| %s:%d/%dHP ",
                        pc.getName(),
                        pc.getStat(Stat.HP),
                        pc.getStat(Stat.MAX_HP));
        }
        System.out.println("|");

        System.out.print("  FOES   ");
        for (Monster m : enemies) {
            if (!m.isAlive())
                System.out.printf("| %s:SLAIN ", m.getName());
            else
                System.out.printf("| %s:%dHP ", m.getName(), m.getHp());
        }
        System.out.println("|");
    }

    // =========================================================================
    // Rosters
    // =========================================================================

    private static void printPartyRoster(List<PlayerCharacter> party) {
        System.out.println();
        System.out.println("  ---- PARTY --------------------------------------------------");
        System.out.printf("  %-10s  %-7s  %4s  %4s  %4s  %4s  %4s  %4s  %3s%n",
                "Name", "Job", "HP", "MP", "STR", "INT", "DEX", "LUC", "LVL");
        rule('-', 60);
        for (PlayerCharacter pc : party) {
            Job job = Job.fromValue(pc.getJob());
            System.out.printf("  %-10s  %-7s  %4d  %4d  %4d  %4d  %4d  %4d  %3d%n",
                    pc.getName(), job,
                    pc.getStat(Stat.HP),
                    pc.getStat(Stat.MAGIC_POWER),
                    pc.getStat(Stat.STRENGTH),
                    pc.getStat(Stat.INTELLIGENCE),
                    pc.getStat(Stat.DEXTERITY),
                    pc.getStat(Stat.LUCK),
                    pc.getStat(Stat.LEVEL));
        }
    }

    private static void printEnemyRoster(List<Monster> enemies) {
        System.out.println();
        System.out.println("  ---- ENEMIES ------------------------------------------------");
        System.out.printf("  %-18s  %4s  %4s%n", "Name", "HP", "XP");
        rule('-', 60);
        for (Monster m : enemies) {
            System.out.printf("  %-18s  %4d  %4d%n",
                    m.getName(), m.getHp(), m.getExperience());
        }
    }

    // =========================================================================
    // Battle summary
    // =========================================================================

    private static void printBattleSummary(List<PlayerCharacter> party,
                                           List<Monster>         enemies,
                                           int                   xpPool) {
        System.out.println();
        rule('=', 60);
        long survivors = party.stream().filter(Entity::isAlive).count();
        boolean victory = enemies.stream().noneMatch(Entity::isAlive);

        if (victory) {
            System.out.println("  VICTORY!  " + survivors + " party member(s) survived.");
            System.out.println("  XP available: " + xpPool);
        } else if (survivors == 0) {
            System.out.println("  DEFEAT.  The party has been slain.");
        } else {
            System.out.println("  BATTLE ENDED.  " + survivors + " survivor(s).");
        }
        rule('=', 60);
    }

    // =========================================================================
    // Banner / replay
    // =========================================================================

    private static void printBanner() {
        rule('=', 60);
        System.out.println("  PHANTASIA: INFINITY — Console Combat Demo");
        System.out.println("  Args: solo | duo | (none = full party)");
        rule('=', 60);
    }

    private static boolean promptReplay(Scanner scanner) {
        System.out.println();
        System.out.print("  Fight again? (y/n): ");
        return scanner.nextLine().trim().toLowerCase().startsWith("y");
    }

    private static void rule(char ch, int len) {
        System.out.println(String.valueOf(ch).repeat(len));
    }
}
