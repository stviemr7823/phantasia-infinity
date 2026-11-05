package com.phantasia.core.logic;

import com.phantasia.core.model.Action;
import com.phantasia.core.model.CombatRank;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerRuntimeV5;
import com.phantasia.core.model.Spell;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Headless, incremental v5 round coordinator. Players use typed v5 storage,
 * while monsters temporarily retain their existing typed v4 wrapper.
 * This does not replace the production v4 CombatManager.
 *
 * Only ATTACK, CAST and NONE are currently supported for player orders.
 * Monster AI is a simple attack against the first living party member.
 */
public final class CombatRoundV5 {
    public record PlayerOrder(
            PlayerRuntimeV5 actor, Action action, Monster enemyTarget,
            PlayerRuntimeV5 allyTarget, Spell spell, CombatRank rank) {
        public PlayerOrder {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(action, "action");
            if (action != Action.ATTACK && action != Action.CAST && action != Action.NONE)
                throw new IllegalArgumentException("Unsupported v5 order: " + action);
        }

        public static PlayerOrder attack(PlayerRuntimeV5 actor, Monster target, CombatRank rank) {
            return new PlayerOrder(actor, Action.ATTACK, target, null, null, rank);
        }
        public static PlayerOrder cast(PlayerRuntimeV5 actor, Spell spell,
                                       Monster enemy, PlayerRuntimeV5 ally) {
            return new PlayerOrder(actor, Action.CAST, enemy, ally, spell, CombatRank.BACK);
        }
        public static PlayerOrder idle(PlayerRuntimeV5 actor) {
            return new PlayerOrder(actor, Action.NONE, null, null, null, CombatRank.FRONT);
        }
    }

    private final List<PlayerRuntimeV5> party;
    private final List<Monster> enemies;
    private int roundNumber;

    public CombatRoundV5(List<PlayerRuntimeV5> party, List<Monster> enemies) {
        this.party = List.copyOf(Objects.requireNonNull(party, "party"));
        this.enemies = List.copyOf(Objects.requireNonNull(enemies, "enemies"));
        requireUnique(this.party);
        requireUnique(this.enemies);
        if (this.party.isEmpty() || this.party.size() > 6)
            throw new IllegalArgumentException("V5 combat requires one to six party members");
        if (this.enemies.isEmpty())
            throw new IllegalArgumentException("V5 combat requires enemies");
    }

    private static <T> void requireUnique(List<T> elements) {
        Set<T> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        for (T value : elements)
            if (!unique.add(Objects.requireNonNull(value, "combatant")))
                throw new IllegalArgumentException("Duplicate combatant");
    }

    public RoundResult runRound(List<PlayerOrder> orders) {
        Objects.requireNonNull(orders, "orders");
        if (outcome() != CombatOutcome.ONGOING)
            throw new IllegalStateException("Cannot run another round after combat has ended");
        Map<PlayerRuntimeV5, PlayerOrder> planned = new IdentityHashMap<>();
        for (PlayerOrder order : orders) {
            Objects.requireNonNull(order, "order");
            if (!containsIdentity(party, order.actor()) || planned.put(order.actor(), order) != null)
                throw new IllegalArgumentException("Unknown or duplicate player order");
            validateOrder(order);
        }

        // Validate every order before incrementing the round or spending resources.
        roundNumber++;
        List<CombatEvent> events = new ArrayList<>();
        List<Object> actors = new ArrayList<>();
        for (PlayerRuntimeV5 player : party) if (player.isAlive()) actors.add(player);
        for (Monster enemy : enemies) if (enemy.isAlive()) actors.add(enemy);
        Map<Object, Integer> initiative = new IdentityHashMap<>();
        for (Object actor : actors) initiative.put(actor, FormulaEngine.rollInitiative());
        actors.sort((left, right) -> Integer.compare(
                initiative.get(right), initiative.get(left)));
        events.add(new CombatEvent.RoundHeader(roundNumber,
                actors.stream().map(this::actorName).toList()));

        for (Object actor : actors) {
            // Stop at the first terminal outcome. Remaining actors must not
            // spend MP, attack or produce events after victory or defeat.
            if (outcome() != CombatOutcome.ONGOING) break;
            if (actor instanceof PlayerRuntimeV5 player) {
                if (!player.isAlive() || player.isAsleep()) continue;
                PlayerOrder order = planned.get(player);
                if (order == null || order.action() == Action.NONE) continue;
                if (order.action() == Action.CAST) resolveCast(order, events);
                else resolveAttack(order, events);
            } else if (actor instanceof Monster monster) {
                if (!monster.isAlive() || monster.isAsleep()) continue;
                PlayerRuntimeV5 defender = party.stream()
                        .filter(PlayerRuntimeV5::isAlive).findFirst().orElse(null);
                if (defender == null) continue;
                if (!FormulaEngine.monsterRollToHit(monster, defender)) {
                    events.add(new CombatEvent.Miss(monster.getName(), defender.name()));
                    continue;
                }
                int damage = FormulaEngine.calculateMonsterDamage(monster);
                defender.damage(damage);
                events.add(new CombatEvent.Hit(monster.getName(), defender.name(), damage,
                        "Body", false));
                if (!defender.isAlive()) events.add(new CombatEvent.Death(defender.name(), true));
            }
        }
        return new RoundResult(roundNumber, List.copyOf(events), outcome());
    }

    private void validateOrder(PlayerOrder order) {
        if (order.action() == Action.ATTACK) {
            if (order.rank() == null || order.enemyTarget() == null
                    || !containsIdentity(enemies, order.enemyTarget())
                    || order.allyTarget() != null || order.spell() != null)
                throw new IllegalArgumentException("Invalid player attack order");
        } else if (order.action() == Action.CAST) {
            Spell spell = Objects.requireNonNull(order.spell(), "spell");
            switch (spell.getEffectType()) {
                case DAMAGE, SLEEP -> {
                    if (spell.getTargetType() != Spell.TargetType.SINGLE_ENEMY
                            && spell.getTargetType() != Spell.TargetType.ALL_ENEMIES)
                        throw new IllegalArgumentException("Invalid enemy spell target type");
                    if (order.allyTarget() != null
                            || (spell.getTargetType() == Spell.TargetType.SINGLE_ENEMY
                            && (order.enemyTarget() == null
                            || !containsIdentity(enemies, order.enemyTarget())))
                            || (spell.getTargetType() == Spell.TargetType.ALL_ENEMIES
                            && order.enemyTarget() != null))
                        throw new IllegalArgumentException("Invalid enemy spell target");
                }
                case HEAL, AWAKEN -> {
                    if (spell.getTargetType() != Spell.TargetType.SINGLE_ALLY
                            && spell.getTargetType() != Spell.TargetType.ALL_ALLIES)
                        throw new IllegalArgumentException("Invalid ally spell target type");
                    if (order.enemyTarget() != null
                            || (spell.getTargetType() == Spell.TargetType.SINGLE_ALLY
                            && (order.allyTarget() == null
                            || !containsIdentity(party, order.allyTarget())))
                            || (spell.getTargetType() == Spell.TargetType.ALL_ALLIES
                            && order.allyTarget() != null))
                        throw new IllegalArgumentException("Invalid ally spell target");
                }
                default -> throw new IllegalArgumentException(
                        "Spell effect not implemented in v5 round: " + spell.getEffectType());
            }
        } else if (order.enemyTarget() != null || order.allyTarget() != null || order.spell() != null) {
            throw new IllegalArgumentException("Idle order has unexpected target or spell");
        }
    }

    private void resolveAttack(PlayerOrder order, List<CombatEvent> events) {
        Monster target = order.enemyTarget();
        PlayerRuntimeV5 attacker = order.actor();
        if (!target.isAlive()) return;
        if (!FormulaEngine.playerRollToHit(attacker, target)) {
            events.add(new CombatEvent.Miss(attacker.name(), target.getName()));
            return;
        }
        int damage = FormulaEngine.calculatePlayerDamage(attacker, order.rank());
        boolean critical = FormulaEngine.rollCritical();
        if (critical) damage = FormulaEngine.applyCritical(damage);
        target.applyDamage(damage);
        events.add(new CombatEvent.Hit(attacker.name(), target.getName(), damage, "Body", critical));
        if (!target.isAlive()) events.add(new CombatEvent.Death(target.getName(), false));
    }

    private void resolveCast(PlayerOrder order, List<CombatEvent> events) {
        Spell spell = order.spell();
        PlayerRuntimeV5 caster = order.actor();
        SpellCastingV5.Result result = switch (spell.getEffectType()) {
            case DAMAGE -> SpellCastingV5.castDamage(caster, spell, enemyTargets(order));
            case SLEEP -> SpellCastingV5.castSleep(caster, spell, enemyTargets(order));
            case HEAL -> SpellCastingV5.castHeal(caster, spell, allyTargets(order));
            case AWAKEN -> SpellCastingV5.castAwaken(caster, spell, allyTargets(order));
            default -> throw new IllegalStateException("Order validation missed effect");
        };
        if (!result.cast()) {
            events.add(new CombatEvent.SpellCast(caster.name(), spell.getName(),
                    "No valid target or insufficient MP", 0, false));
            return;
        }
        for (SpellCastingV5.Impact impact : result.impacts()) {
            if (spell.getEffectType() == Spell.EffectType.SLEEP
                    || spell.getEffectType() == Spell.EffectType.AWAKEN) {
                events.add(new CombatEvent.StatusChange(impact.target(), "Asleep",
                        spell.getEffectType() == Spell.EffectType.SLEEP));
            } else {
                events.add(new CombatEvent.SpellCast(caster.name(), spell.getName(),
                        impact.target(), impact.damage(), true));
            }
        }
        if (result.impacts().isEmpty())
            events.add(new CombatEvent.SpellCast(caster.name(), spell.getName(),
                    "No effect", 0, true));
        if (spell.getEffectType() == Spell.EffectType.DAMAGE) {
            for (Monster enemy : enemyTargets(order))
                if (!enemy.isAlive() && result.impacts().stream()
                        .anyMatch(impact -> impact.target().equals(enemy.getName())))
                    events.add(new CombatEvent.Death(enemy.getName(), false));
        }
    }

    private List<Monster> enemyTargets(PlayerOrder order) {
        return order.spell().getTargetType() == Spell.TargetType.ALL_ENEMIES
                ? enemies : List.of(order.enemyTarget());
    }

    private List<PlayerRuntimeV5> allyTargets(PlayerOrder order) {
        return order.spell().getTargetType() == Spell.TargetType.ALL_ALLIES
                ? party : List.of(order.allyTarget());
    }

    private static <T> boolean containsIdentity(List<T> list, T candidate) {
        for (T value : list) if (value == candidate) return true;
        return false;
    }

    private String actorName(Object actor) {
        return actor instanceof PlayerRuntimeV5 player ? player.name() : ((Monster) actor).getName();
    }

    public CombatOutcome outcome() {
        if (party.stream().noneMatch(PlayerRuntimeV5::isAlive)) return CombatOutcome.DEFEAT;
        if (enemies.stream().noneMatch(Monster::isAlive)) return CombatOutcome.VICTORY;
        return CombatOutcome.ONGOING;
    }
}
