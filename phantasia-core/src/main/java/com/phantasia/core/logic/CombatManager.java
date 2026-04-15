// phantasia-core/src/main/java/com/phantasia/core/logic/CombatManager.java
package com.phantasia.core.logic;

import com.phantasia.core.model.*;
import com.phantasia.core.data.SpellFactory;
import com.phantasia.core.data.PartyLedger;

import java.util.*;

public class CombatManager
{

    private final List<PlayerCharacter> party;
    private final List<Monster> enemies;
    private final List<Entity> allCombatants;
    private final AttackResolver resolver;
    private static final Random RNG = new Random();
    private boolean partyInvincible = false;
    public void setPartyInvincible(boolean v) { this.partyInvincible = v; }

    private int roundNumber = 0;

    public CombatManager(List<PlayerCharacter> party, List<Monster> enemies,
                         SpellFactory spellFactory, EncounterCondition condition)
    {
        this.party = party;
        this.enemies = enemies;
        this.allCombatants = new ArrayList<>();
        this.allCombatants.addAll(party);
        this.allCombatants.addAll(enemies);
        this.resolver = new AttackResolver(spellFactory);
        applyEncounterCondition(condition);
    }

    // ------------------------------------------------------------------
    // Main entry point — call once per round from the game loop
    // ------------------------------------------------------------------

    public RoundResult runRound()
    {
        roundNumber++;
        List<CombatEvent> events = new ArrayList<>();

        // 1. Roll initiative for every living combatant
        Map<Entity, Integer> initiative = new HashMap<>();
        allCombatants.stream()
                .filter(Entity::isAlive)
                .forEach(e -> initiative.put(e, FormulaEngine.rollInitiative()));

        // 2. Sort — highest initiative acts first
        List<Entity> order = new ArrayList<>(initiative.keySet());
        order.sort((a, b) -> Integer.compare(initiative.get(b), initiative.get(a)));

        // 3. Record the round header — who acts and in what order
        List<String> initiativeNames = order.stream()
                .map(Entity::getName)
                .toList();
        events.add(new CombatEvent.RoundHeader(roundNumber, initiativeNames));

        // 4. Every combatant takes their turn
        for (Entity actor : order)
        {
            if (!actor.isAlive()) continue;
            resolveActorTurn(actor, events);
        }

        // 5. End of round cleanup
        endOfRoundCleanup();

        // 6. Assess outcome — check after all actions are resolved
        CombatOutcome outcome = assessOutcome();

        return new RoundResult(roundNumber, Collections.unmodifiableList(events), outcome);
    }

    // ------------------------------------------------------------------
    // Turn resolution — produces events, never prints anything
    // ------------------------------------------------------------------

    private void resolveActorTurn(Entity actor, List<CombatEvent> events)
    {

        // Sleeping combatants skip their turn unless casting AWAKEN
        if (actor.isAsleep())
        {
            if (!(actor.getCurrentAction() == Action.CAST &&
                    actor.getSelectedSpellId() == 51))
            {
                events.add(new CombatEvent.StatusChange(
                        actor.getName(), "Asleep", true));
                return;
            }
        }

        switch (actor.getCurrentAction())
        {

            case ATTACK, SLASH, THRUST, LUNGE ->
            {
                Entity target = actor.getPrimaryTarget();
                if (target == null || !target.isAlive())
                {
                    target = pickLivingTarget(actor);
                }
                if (target == null) return; // No valid targets

                resolveAttack(actor, target, actor.getCurrentAction(), events);
            }

            case CAST ->
            {
                Spell spell = resolver.getSpell(actor.getSelectedSpellId());
                if (spell != null
                        && spell.getTargetType() == Spell.TargetType.ALL_ENEMIES
                        && actor instanceof PlayerCharacter)
                {
                    List<Entity> targets = enemies.stream()
                            .filter(Entity::isAlive)
                            .collect(java.util.stream.Collectors.toList());
                    for (Entity target : targets)
                    {
                        events.addAll(resolver.resolveSpell(actor, actor.getSelectedSpellId(), target));
                    }
                } else
                {
                    events.addAll(resolver.resolveSpell(actor, actor.getSelectedSpellId(),
                            actor.getPrimaryTarget()));
                }
                recordDeaths(events);
            }

            case PARRY ->
            {
                actor.setParrying(true);
                events.add(new CombatEvent.StatusChange(
                        actor.getName(), "Parrying", true));
            }

            case RUN ->
            {
                boolean escaped = FormulaEngine.rollEscape(actor);
                events.add(new CombatEvent.FleeAttempt(actor.getName(), escaped));
                if (escaped && actor instanceof PlayerCharacter pc)
                {
                    party.remove(pc);
                    allCombatants.remove(pc);
                }
            }

            default ->
            {
            } // NONE — idle
        }
    }

    private void resolveAttack(Entity attacker, Entity target,
                               Action action, List<CombatEvent> events)
    {
        int swings = (action == Action.SLASH) ? 3 : 1;

        for (int i = 0; i < swings; i++)
        {
            if (!target.isAlive()) break;

            AttackResult result = new AttackResult(attacker, target);

            // 1. Hit check
            if (attacker instanceof PlayerCharacter pc && target instanceof Monster m)
                result.setHit(FormulaEngine.playerRollToHit(pc, m));
            else if (attacker instanceof Monster m && target instanceof PlayerCharacter pc)
                result.setHit(FormulaEngine.monsterRollToHit(m, pc));
            else
                result.setHit(true);

            if (!result.isHit())
            {
                events.add(new CombatEvent.Miss(
                        attacker.getName(), target.getName()));
                continue;
            }

            // 2. Base damage
            if (attacker instanceof PlayerCharacter pc)
                result.setBaseDamage(FormulaEngine.calculatePlayerDamage(pc));
            else if (attacker instanceof Monster m)
                result.setBaseDamage(FormulaEngine.calculateMonsterDamage(m));
            else
                result.setBaseDamage(1);
            result.setFinalDamage(result.getBaseDamage());

            // 3. Critical check
            result.setCritical(FormulaEngine.rollCritical());
            if (result.isCritical())
                result.setFinalDamage(FormulaEngine.applyCritical(result.getFinalDamage()));

            // 4. Hit location
            result.setLimb(FormulaEngine.rollHitLocation());

            // 5. Apply and emit
            target.applyDamage(result.getFinalDamage());
            events.add(new CombatEvent.Hit(
                    attacker.getName(), target.getName(),
                    result.getFinalDamage(), result.getLimb(), result.isCritical()));

            if (!target.isAlive())
            {
                events.add(new CombatEvent.Death(
                        target.getName(),
                        target instanceof PlayerCharacter));
            }
        }
    }

    // ------------------------------------------------------------------
    // Outcome assessment
    // ------------------------------------------------------------------

    private CombatOutcome assessOutcome()
    {
        boolean partyAlive = party.stream().anyMatch(Entity::isAlive);
        boolean enemiesAlive = enemies.stream().anyMatch(Entity::isAlive);

        if (!partyAlive) return CombatOutcome.DEFEAT;
        if (!enemiesAlive) return CombatOutcome.VICTORY;
        return CombatOutcome.ONGOING;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Entity pickLivingTarget(Entity actor)
    {
        if (actor instanceof PlayerCharacter)
        {
            return enemies.stream()
                    .filter(Entity::isAlive)
                    .findFirst().orElse(null);
        } else
        {
            return party.stream()
                    .filter(Entity::isAlive)
                    .findFirst().orElse(null);
        }
    }

    private void recordDeaths(List<CombatEvent> events)
    {
        allCombatants.stream()
                .filter(e -> !e.isAlive())
                .filter(e -> events.stream()
                        .noneMatch(ev -> ev instanceof CombatEvent.Death d &&
                                d.entityName().equals(e.getName())))
                .forEach(e -> events.add(new CombatEvent.Death(
                        e.getName(), e instanceof PlayerCharacter)));
    }

    private void endOfRoundCleanup()
    {
        allCombatants.forEach(e -> e.setParrying(false));
    }

    public boolean isCombatOver()
    {
        return assessOutcome() != CombatOutcome.ONGOING;
    }

    private void applyEncounterCondition(EncounterCondition condition)
    {
        switch (condition)
        {
            case PARTY_ASLEEP -> party.forEach(pc -> pc.setAsleep(true));
            case MONSTERS_SURPRISE -> party.forEach(pc -> pc.setAction(Action.NONE));
            case NORMAL ->
            {
            }
        }
    }
    public CombatOutcome getOutcome() {
        return assessOutcome();
    }

    public BattleResult conclude(List<PlayerCharacter> party, PartyLedger ledger) {
        CombatOutcome outcome = assessOutcome();
        if (outcome == CombatOutcome.VICTORY) {
            LootManager lootManager = new LootManager(party, ledger);
            LootManager.LootResult loot = lootManager.distributeFrom(enemies);
            return new BattleResult(outcome, loot);
        }
        return new BattleResult(outcome, null);
    }

}

