// phantasia-jme/src/main/java/com/phantasia/jme/CombatDemoInitializer.java
package com.phantasia.jme;

import com.phantasia.core.data.EncounterFactory;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.SpellFactory;
import com.phantasia.core.logic.EncounterCondition;
import com.phantasia.core.logic.FormulaEngine;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.jme.states.CombatState;

import java.util.ArrayList;
import java.util.List;

/**
 * Bootstraps a test battle for the JME demo.
 *
 * Session-aware overloads (preferred) use the live party from GameSession
 * so the characters you see in combat match the ones you built or loaded.
 *
 * The original no-session overloads are kept for standalone unit tests
 * and any context where a session isn't available.
 */
public class CombatDemoInitializer {

    // -------------------------------------------------------------------------
    // Session-aware overloads (preferred)
    // -------------------------------------------------------------------------

    /**
     * Starts a deterministic test battle using the live session party.
     * Called by CombatState.onEnable() in normal gameplay.
     */
    public static void startTestBattle(CombatState combatState,
                                       GameSession session) {
        List<PlayerCharacter> party    = new ArrayList<>(session.getParty());
        List<Monster>         monsters = EncounterFactory.buildTestEncounter();
        SpellFactory          spells   = new SpellFactory();
        EncounterCondition    cond     = EncounterCondition.NORMAL;
        combatState.startCombat(party, monsters, spells, cond);
    }

    /**
     * Starts a fully randomised battle using the live session party.
     */
    public static void startRandomBattle(CombatState combatState,
                                         GameSession session) {
        List<PlayerCharacter> party    = new ArrayList<>(session.getParty());
        List<Monster>         monsters = EncounterFactory.generateEncounter();
        SpellFactory          spells   = new SpellFactory();
        EncounterCondition    cond     = FormulaEngine.rollEncounterCondition();
        combatState.startCombat(party, monsters, spells, cond);
    }

    // -------------------------------------------------------------------------
    // Original overloads — no session, generates a fresh party
    // -------------------------------------------------------------------------

    /**
     * Starts a deterministic test battle with a freshly generated party.
     * Use startTestBattle(combatState, session) in normal gameplay.
     */
    public static void startTestBattle(CombatState combatState) {
        List<PlayerCharacter> party    = EncounterFactory.buildTestParty();
        List<Monster>         monsters = EncounterFactory.buildTestEncounter();
        SpellFactory          spells   = new SpellFactory();
        EncounterCondition    cond     = EncounterCondition.NORMAL;
        combatState.startCombat(party, monsters, spells, cond);
    }

    /**
     * Starts a fully randomised battle with a freshly generated party.
     */
    public static void startRandomBattle(CombatState combatState) {
        List<PlayerCharacter> party    = EncounterFactory.generateParty();
        List<Monster>         monsters = EncounterFactory.generateEncounter();
        SpellFactory          spells   = new SpellFactory();
        EncounterCondition    cond     = FormulaEngine.rollEncounterCondition();
        combatState.startCombat(party, monsters, spells, cond);
    }
}