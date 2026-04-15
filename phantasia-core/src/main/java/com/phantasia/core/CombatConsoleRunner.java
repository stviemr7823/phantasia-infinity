// phantasia-core/src/main/java/com/phantasia/core/CombatConsoleRunner.java
//
// Phantasia: Infinity — Gussied-Up Combat Demo
//
// A self-contained console battle simulator that exercises the engine
// exactly as-is: FormulaEngine, CombatManager, CombatNarrator,
// CombatEvent, RoundResult, EncounterCondition, Action, DataCore, etc.
//
// FIXES APPLIED:
//   1. Private generateParty() / generateEncounter() / buildCharacter()
//      methods have been removed. They were near-identical copies of
//      EncounterFactory. All generation now delegates to EncounterFactory
//      so there is exactly one bestiary and one character-build spec.
//
//   2. Private assignPartyActions() / assignMonsterActions() have been
//      removed. They are now in CombatAI (core) so every frontend — JME,
//      LibGDX, and this console runner — shares identical AI behaviour.
//
// CombatConsoleRunner is now a thin demo harness: it wires up core
// services and handles console I/O. No game logic lives here.

package com.phantasia.core;

import com.phantasia.core.data.EncounterFactory;
import com.phantasia.core.data.SpellFactory;
import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;

import java.io.IOException;
import java.util.*;

public class CombatConsoleRunner {

    // =========================================================================
    // Entry point
    // =========================================================================

    public static void main(String[] args) {
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

    // =========================================================================
    // Single battle
    // =========================================================================

    private static void runBattle() {
        System.out.println();
        rule('=', 60);
        System.out.println("  GENERATING ENCOUNTER...");
        rule('=', 60);

        // Delegate entirely to EncounterFactory — no duplication here
        List<PlayerCharacter> party   = EncounterFactory.generateParty();
        List<Monster>         enemies = EncounterFactory.generateEncounter();

        printPartyRoster(party);
        printEnemyRoster(enemies);

        EncounterCondition condition = FormulaEngine.rollEncounterCondition();

        System.out.println();
        rule('-', 60);
        System.out.println("  " + condition.announcement());
        rule('-', 60);

        int xpPool = enemies.stream().mapToInt(Entity::getExperience).sum();

        SpellFactory spellFactory = new SpellFactory();
        try {
            spellFactory.loadSpells();
        } catch (IOException e) {
            System.err.println("  [WARN] Could not load spells.dat — spells will fail gracefully. "
                    + e.getMessage());
        }

        CombatNarrator narrator     = new CombatNarrator();
        CombatManager  manager      = new CombatManager(party, enemies, spellFactory, condition);

        final int ROUND_CAP = 50;

        while (!manager.isCombatOver()) {
            // Delegate action assignment to CombatAI — same logic used by all frontends
            CombatAI.assignPartyActions(party, enemies);
            CombatAI.assignMonsterActions(enemies, party);

            RoundResult result = manager.runRound();

            printRoundHeader(result);
            printNarratorLines(narrator, result);

            if (result.isCombatOver()) break;

            printStatusBar(party, enemies);

            if (result.roundNumber() >= ROUND_CAP) {
                System.out.println();
                System.out.println("  !! Battle has raged for " + ROUND_CAP
                        + " rounds with no conclusion — the combatants disengage !!");
                break;
            }
        }

        printBattleSummary(party, enemies, xpPool);
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
                    pc.getName(),
                    job,
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
    // Banner / replay prompt
    // =========================================================================

    private static void printBanner() {
        rule('=', 60);
        System.out.println("  PHANTASIA: INFINITY — Console Combat Demo");
        rule('=', 60);
    }

    private static boolean promptReplay(Scanner scanner) {
        System.out.println();
        System.out.print("  Fight again? (y/n): ");
        String input = scanner.nextLine().trim().toLowerCase();
        return input.startsWith("y");
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private static void rule(char ch, int len) {
        System.out.println(String.valueOf(ch).repeat(len));
    }
}
