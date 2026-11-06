package com.phantasia.core.logic;

import com.phantasia.core.model.Action;
import com.phantasia.core.model.CombatRank;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerRuntimeV5;
import com.phantasia.core.model.Spell;
import com.phantasia.core.model.PlayerStateV5.Attribute;
import com.phantasia.core.model.item.EquipSlot;
import com.phantasia.core.model.item.ItemDefinition;
import com.phantasia.core.model.item.ItemTable;
import com.phantasia.core.model.item.ItemCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

/**
 * Headless, incremental v5 round coordinator. Players use typed v5 storage,
 * while monsters temporarily retain their existing typed v4 wrapper.
 * This does not replace the production v4 CombatManager.
 *
 * ATTACK, THRUST, SLASH, LUNGE, AIM_BLOW, RANGED_ATTACK, PARRY, CAST and NONE are supported player orders.
 * Monster AI is a simple attack against the first living party member.
 */
public final class CombatRoundV5 {
    public record PlayerOrder(
            PlayerRuntimeV5 actor, Action action, Monster enemyTarget,
            PlayerRuntimeV5 allyTarget, Spell spell, CombatRank rank) {
        public PlayerOrder {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(action, "action");
            if (action != Action.ATTACK && action != Action.THRUST && action != Action.SLASH
                    && action != Action.AIM_BLOW
                    && action != Action.LUNGE
                    && action != Action.RANGED_ATTACK
                    && action != Action.PARRY
                    && action != Action.CAST && action != Action.NONE)
                throw new IllegalArgumentException("Unsupported v5 order: " + action);
        }

        public static PlayerOrder attack(PlayerRuntimeV5 actor, Monster target, CombatRank rank) {
            return new PlayerOrder(actor, Action.ATTACK, target, null, null, rank);
        }
        /** Single melee strike with a modest hit-chance bonus. */
        public static PlayerOrder thrust(PlayerRuntimeV5 actor, Monster target, CombatRank rank) {
            return new PlayerOrder(actor, Action.THRUST, target, null, null, rank);
        }
        /** Three independent melee swings against one selected enemy. */
        public static PlayerOrder slash(PlayerRuntimeV5 actor, Monster target, CombatRank rank) {
            return new PlayerOrder(actor, Action.SLASH, target, null, null, rank);
        }
        /** Slow, deliberate single strike with a modest accuracy and damage bonus. */
        public static PlayerOrder aimBlow(PlayerRuntimeV5 actor, Monster target, CombatRank rank) {
            return new PlayerOrder(actor, Action.AIM_BLOW, target, null, null, rank);
        }
        /** Front-rank lunge may reach the enemy's second (middle) rank. */
        public static PlayerOrder lunge(PlayerRuntimeV5 actor, Monster target, CombatRank rank) {
            return new PlayerOrder(actor, Action.LUNGE, target, null, null, rank);
        }
        public static PlayerOrder ranged(PlayerRuntimeV5 actor, Monster target) {
            return new PlayerOrder(actor, Action.RANGED_ATTACK, target, null, null, null);
        }
        public static PlayerOrder cast(PlayerRuntimeV5 actor, Spell spell,
                                       Monster enemy, PlayerRuntimeV5 ally) {
            return new PlayerOrder(actor, Action.CAST, enemy, ally, spell, CombatRank.BACK);
        }
        /** Preplanned defense against monster physical damage for one round. */
        public static PlayerOrder parry(PlayerRuntimeV5 actor) {
            return new PlayerOrder(actor, Action.PARRY, null, null, null, null);
        }
        public static PlayerOrder idle(PlayerRuntimeV5 actor) {
            return new PlayerOrder(actor, Action.NONE, null, null, null, CombatRank.FRONT);
        }
    }

    private final List<PlayerRuntimeV5> party;
    private final List<Monster> enemies;
    private final Map<PlayerRuntimeV5, CombatRank> ranks = new IdentityHashMap<>();
    private final Map<Monster, CombatRank> enemyRanks = new IdentityHashMap<>();
    private int roundNumber;
    private final ToIntFunction<Object> initiativeRoll;
    private final BiPredicate<PlayerRuntimeV5, Monster> playerHitRoll;
    private final BiPredicate<PlayerRuntimeV5, Monster> thrustHitRoll;
    private final BiPredicate<PlayerRuntimeV5, Monster> aimedHitRoll;
    private final BiPredicate<Monster, PlayerRuntimeV5> monsterHitRoll;
    private final ToIntFunction<Monster> monsterDamageRoll;

    /** Compatibility constructor: all party members initially occupy the front rank. */
    public CombatRoundV5(List<PlayerRuntimeV5> party, List<Monster> enemies) {
        this(party, enemies, Map.of(), Map.of());
    }

    /**
     * Pre-battle rank assignment. Missing entries default to FRONT.
     * Unexpected party members and null ranks are rejected.
     */
    public CombatRoundV5(List<PlayerRuntimeV5> party, List<Monster> enemies,
                         Map<PlayerRuntimeV5, CombatRank> assignedRanks) {
        this(party, enemies, assignedRanks, Map.of());
    }

    /**
     * Both formations are fixed for this encounter. Unspecified ranks default
     * to FRONT; enemy positions are not stored in legacy Monster records.
     */
    public CombatRoundV5(List<PlayerRuntimeV5> party, List<Monster> enemies,
                         Map<PlayerRuntimeV5, CombatRank> assignedRanks,
                         Map<Monster, CombatRank> assignedEnemyRanks) {
        this(party, enemies, assignedRanks, assignedEnemyRanks,
                ignored -> FormulaEngine.rollInitiative(), FormulaEngine::playerRollToHit);
    }

    /** Package-private deterministic seam for headless round tests only. */
    CombatRoundV5(List<PlayerRuntimeV5> party, List<Monster> enemies,
                  Map<PlayerRuntimeV5, CombatRank> assignedRanks,
                  Map<Monster, CombatRank> assignedEnemyRanks,
                  ToIntFunction<Object> initiativeRoll,
                  BiPredicate<PlayerRuntimeV5, Monster> playerHitRoll) {
        this(party, enemies, assignedRanks, assignedEnemyRanks,
                initiativeRoll, playerHitRoll,
                FormulaEngine::monsterRollToHit, FormulaEngine::calculateMonsterDamage);
    }

    /** Package-private test seam; public encounters retain ordinary dice. */
    CombatRoundV5(List<PlayerRuntimeV5> party, List<Monster> enemies,
                  Map<PlayerRuntimeV5, CombatRank> assignedRanks,
                  Map<Monster, CombatRank> assignedEnemyRanks,
                  ToIntFunction<Object> initiativeRoll,
                  BiPredicate<PlayerRuntimeV5, Monster> playerHitRoll,
                  BiPredicate<Monster, PlayerRuntimeV5> monsterHitRoll,
                  ToIntFunction<Monster> monsterDamageRoll) {
        this(party, enemies, assignedRanks, assignedEnemyRanks,
                initiativeRoll, playerHitRoll, monsterHitRoll, monsterDamageRoll,
                (actor, defender) -> Dice.chance(thrustHitChance(actor, defender)));
    }

    /** Extended deterministic test seam for THRUST. */
    CombatRoundV5(List<PlayerRuntimeV5> party, List<Monster> enemies,
                  Map<PlayerRuntimeV5, CombatRank> assignedRanks,
                  Map<Monster, CombatRank> assignedEnemyRanks,
                  ToIntFunction<Object> initiativeRoll,
                  BiPredicate<PlayerRuntimeV5, Monster> playerHitRoll,
                  BiPredicate<Monster, PlayerRuntimeV5> monsterHitRoll,
                  ToIntFunction<Monster> monsterDamageRoll,
                  BiPredicate<PlayerRuntimeV5, Monster> thrustHitRoll) {
        this(party, enemies, assignedRanks, assignedEnemyRanks, initiativeRoll,
                playerHitRoll, monsterHitRoll, monsterDamageRoll, thrustHitRoll,
                (actor, target) -> Dice.chance(aimBlowHitChance(actor, target)));
    }

    /** Extended deterministic test seam for AIM_BLOW. */
    CombatRoundV5(List<PlayerRuntimeV5> party, List<Monster> enemies,
                  Map<PlayerRuntimeV5, CombatRank> assignedRanks,
                  Map<Monster, CombatRank> assignedEnemyRanks,
                  ToIntFunction<Object> initiativeRoll,
                  BiPredicate<PlayerRuntimeV5, Monster> playerHitRoll,
                  BiPredicate<Monster, PlayerRuntimeV5> monsterHitRoll,
                  ToIntFunction<Monster> monsterDamageRoll,
                  BiPredicate<PlayerRuntimeV5, Monster> thrustHitRoll,
                  BiPredicate<PlayerRuntimeV5, Monster> aimedHitRoll) {
        this.aimedHitRoll = Objects.requireNonNull(aimedHitRoll, "aimedHitRoll");
        this.thrustHitRoll = Objects.requireNonNull(thrustHitRoll, "thrustHitRoll");
        this.initiativeRoll = Objects.requireNonNull(initiativeRoll, "initiativeRoll");
        this.playerHitRoll = Objects.requireNonNull(playerHitRoll, "playerHitRoll");
        this.monsterHitRoll = Objects.requireNonNull(monsterHitRoll, "monsterHitRoll");
        this.monsterDamageRoll = Objects.requireNonNull(monsterDamageRoll, "monsterDamageRoll");
        this.party = List.copyOf(Objects.requireNonNull(party, "party"));
        this.enemies = List.copyOf(Objects.requireNonNull(enemies, "enemies"));
        requireUnique(this.party);
        requireUnique(this.enemies);
        if (this.party.isEmpty() || this.party.size() > 6)
            throw new IllegalArgumentException("V5 combat requires one to six party members");
        if (this.enemies.isEmpty())
            throw new IllegalArgumentException("V5 combat requires enemies");
        Objects.requireNonNull(assignedRanks, "assignedRanks");
        for (Map.Entry<PlayerRuntimeV5, CombatRank> entry : assignedRanks.entrySet()) {
            if (!containsIdentity(this.party, entry.getKey()))
                throw new IllegalArgumentException("Rank assignment for non-party member");
            Objects.requireNonNull(entry.getValue(), "rank");
        }
        for (PlayerRuntimeV5 member : this.party)
            ranks.put(member, assignedRanks.getOrDefault(member, CombatRank.FRONT));
        Objects.requireNonNull(assignedEnemyRanks, "assignedEnemyRanks");
        for (Map.Entry<Monster, CombatRank> entry : assignedEnemyRanks.entrySet()) {
            if (!containsIdentity(this.enemies, entry.getKey()))
                throw new IllegalArgumentException("Rank assignment for non-enemy");
            Objects.requireNonNull(entry.getValue(), "enemy rank");
        }
        for (Monster enemy : this.enemies)
            enemyRanks.put(enemy, assignedEnemyRanks.getOrDefault(enemy, CombatRank.FRONT));
    }

    public CombatRank enemyRankOf(Monster enemy) {
        if (!containsIdentity(enemies, enemy))
            throw new IllegalArgumentException("Not an encounter enemy");
        return enemyRanks.get(enemy);
    }

    /**
     * Only an exposed enemy rank can be targeted with basic melee.
     * A front-rank LUNGE may instead reach the middle enemy rank.
     */
    public boolean canMeleeReach(PlayerRuntimeV5 actor, Monster target) {
        CombatRank ownRank = rankOf(actor);
        CombatRank enemyRank = enemyRankOf(target);
        if (!target.isAlive() || !actor.isAlive() || ownRank == CombatRank.BACK)
            return false;
        int frontmost = enemies.stream().filter(Monster::isAlive)
                .mapToInt(m -> enemyRankOf(m).position)
                .min().orElse(Integer.MAX_VALUE);
        return enemyRank.position == frontmost;
    }

    /** Lunge reaches normal melee targets, plus the middle enemy rank from front. */
    public boolean canLungeReach(PlayerRuntimeV5 actor, Monster target) {
        CombatRank own = rankOf(actor);
        CombatRank enemy = enemyRankOf(target);
        return canMeleeReach(actor, target)
                || (actor.isAlive() && target.isAlive()
                    && own == CombatRank.FRONT && enemy == CombatRank.MIDDLE);
    }

    public CombatRank rankOf(PlayerRuntimeV5 member) {
        if (!containsIdentity(party, member))
            throw new IllegalArgumentException("Not a party member");
        return ranks.get(member);
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

        // Parry is a preplanned stance, effective for the whole round even
        // when a monster wins initiative. It is not persisted in player state.
        Set<PlayerRuntimeV5> parrying = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PlayerOrder order : planned.values())
            if (order.action() == Action.PARRY && order.actor().isAlive()
                    && !order.actor().isAsleep())
                parrying.add(order.actor());

        // Validate every order before incrementing the round or spending resources.
        roundNumber++;
        List<CombatEvent> events = new ArrayList<>();
        List<Object> actors = new ArrayList<>();
        for (PlayerRuntimeV5 player : party) if (player.isAlive()) actors.add(player);
        for (Monster enemy : enemies) if (enemy.isAlive()) actors.add(enemy);
        Map<Object, Integer> initiative = new IdentityHashMap<>();
        for (Object actor : actors) {
            int rolled = initiativeRoll.applyAsInt(actor);
            if (actor instanceof PlayerRuntimeV5 player) {
                PlayerOrder order = planned.get(player);
                if (order != null && order.action() == Action.AIM_BLOW)
                    rolled -= 3;
            }
            initiative.put(actor, rolled);
        }
        actors.sort((left, right) -> Integer.compare(
                initiative.get(right), initiative.get(left)));
        events.add(new CombatEvent.RoundHeader(roundNumber,
                actors.stream().map(this::actorName).toList()));
        for (PlayerRuntimeV5 member : party)
            if (parrying.contains(member))
                events.add(new CombatEvent.StatusChange(member.name(), "Parrying", true));

        for (Object actor : actors) {
            // Stop at the first terminal outcome. Remaining actors must not
            // spend MP, attack or produce events after victory or defeat.
            if (outcome() != CombatOutcome.ONGOING) break;
            if (actor instanceof PlayerRuntimeV5 player) {
                if (!player.isAlive() || player.isAsleep()) continue;
                PlayerOrder order = planned.get(player);
                if (order == null || order.action() == Action.NONE
                        || order.action() == Action.PARRY) continue;
                if (order.action() == Action.CAST) resolveCast(order, events);
                else if (order.action() == Action.RANGED_ATTACK) resolveRanged(order, events);
                else resolveAttack(order, events);
            } else if (actor instanceof Monster monster) {
                if (!monster.isAlive() || monster.isAsleep()) continue;
                PlayerRuntimeV5 defender = preferredMonsterTarget();
                if (defender == null) continue;
                if (!monsterHitRoll.test(monster, defender)) {
                    events.add(new CombatEvent.Miss(monster.getName(), defender.name()));
                    continue;
                }
                int damage = monsterDamageRoll.applyAsInt(monster);
                if (parrying.contains(defender)) damage = parriedDamage(damage);
                defender.damage(damage);
                events.add(new CombatEvent.Hit(monster.getName(), defender.name(), damage,
                        "Body", false));
                if (!defender.isAlive()) events.add(new CombatEvent.Death(defender.name(), true));
            }
        }
        // Close the temporary stance, including on terminal rounds.
        for (PlayerRuntimeV5 member : party)
            if (parrying.contains(member))
                events.add(new CombatEvent.StatusChange(member.name(), "Parrying", false));
        return new RoundResult(roundNumber, List.copyOf(events), outcome());
    }

    /** AIM_BLOW trades three initiative points for accuracy and stronger damage. */
    static int aimBlowHitChance(PlayerRuntimeV5 attacker, Monster defender) {
        return Math.clamp(FormulaEngine.playerHitChance(attacker, defender) + 10, 5, 95);
    }

    static int aimedDamage(int baseDamage) {
        if (baseDamage < 0) throw new IllegalArgumentException("Negative damage");
        return Math.min(99, (baseDamage * 5 + 3) / 4);
    }

    /** THRUST grants +15 percentage points, respecting the standard 5–95 bounds. */
    static int thrustHitChance(PlayerRuntimeV5 attacker, Monster defender) {
        return Math.clamp(FormulaEngine.playerHitChance(attacker, defender) + 15, 5, 95);
    }

    /** Halve incoming physical damage, rounding up; a hit still deals at least one. */
    static int parriedDamage(int damage) {
        if (damage < 0) throw new IllegalArgumentException("Negative damage");
        return (damage + 1) / 2;
    }

    private void validateOrder(PlayerOrder order) {
        if (order.action() == Action.ATTACK || order.action() == Action.THRUST
                || order.action() == Action.SLASH || order.action() == Action.LUNGE
                || order.action() == Action.AIM_BLOW) {
            if (order.rank() == null || order.rank() != rankOf(order.actor())
                    || order.enemyTarget() == null
                    || !containsIdentity(enemies, order.enemyTarget())
                    || (order.actor().isAlive() && order.enemyTarget().isAlive()
                            && !(order.action() == Action.LUNGE
                                ? canLungeReach(order.actor(), order.enemyTarget())
                                : canMeleeReach(order.actor(), order.enemyTarget())))
                    || order.allyTarget() != null || order.spell() != null)
                throw new IllegalArgumentException("Invalid player attack order");
        } else if (order.action() == Action.RANGED_ATTACK) {
            if (order.enemyTarget() == null || !containsIdentity(enemies, order.enemyTarget())
                    || order.allyTarget() != null || order.spell() != null
                    || order.rank() != null || !hasBow(order.actor()))
                throw new IllegalArgumentException("Ranged attack requires an equipped bow and encounter enemy");
        } else if (order.action() == Action.PARRY) {
            if (order.enemyTarget() != null || order.allyTarget() != null
                    || order.spell() != null || order.rank() != null)
                throw new IllegalArgumentException("Parry takes no target, spell or rank");
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
                            && order.enemyTarget() != null
                            && !containsIdentity(enemies, order.enemyTarget())))
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

    /**
     * Monster AI attacks the nearest occupied rank; within a rank, original
     * party order is a stable tie-breaker. No random targeting policy yet.
     */
    private PlayerRuntimeV5 preferredMonsterTarget() {
        PlayerRuntimeV5 selected = null;
        for (PlayerRuntimeV5 member : party) {
            if (!member.isAlive()) continue;
            if (selected == null || rankOf(member).position < rankOf(selected).position)
                selected = member;
        }
        return selected;
    }

    private void resolveAttack(PlayerOrder order, List<CombatEvent> events) {
        Monster target = order.enemyTarget();
        PlayerRuntimeV5 attacker = order.actor();
        if (!target.isAlive()) {
            events.add(new CombatEvent.ActionSkipped(attacker.name(), target.getName(),
                    "TARGET_DEFEATED"));
            return;
        }
        if (!(order.action() == Action.LUNGE
                ? canLungeReach(attacker, target) : canMeleeReach(attacker, target))) {
            events.add(new CombatEvent.ActionSkipped(attacker.name(), target.getName(),
                    "TARGET_OUT_OF_REACH"));
            return;
        }
        // Match the legacy SLASH convention: three independent attempts,
        // never switching targets or continuing after a killing blow.
        int swings = order.action() == Action.SLASH ? 3 : 1;
        for (int swing = 0; swing < swings && target.isAlive(); swing++) {
            boolean hit = order.action() == Action.THRUST
                    ? thrustHitRoll.test(attacker, target)
                    : order.action() == Action.AIM_BLOW
                        ? aimedHitRoll.test(attacker, target)
                        : playerHitRoll.test(attacker, target);
            if (!hit) {
                events.add(new CombatEvent.Miss(attacker.name(), target.getName()));
                continue;
            }
            int damage = FormulaEngine.calculatePlayerDamage(attacker, order.rank());
            if (order.action() == Action.AIM_BLOW) damage = aimedDamage(damage);
            boolean critical = FormulaEngine.rollCritical();
            if (critical) damage = FormulaEngine.applyCritical(damage);
            target.applyDamage(damage);
            events.add(new CombatEvent.Hit(attacker.name(), target.getName(), damage, "Body", critical));
            if (!target.isAlive())
                events.add(new CombatEvent.Death(target.getName(), false));
        }
    }

    /** A bow can reach any living enemy rank, including from the back line. */
    public boolean canRangedReach(PlayerRuntimeV5 actor, Monster target) {
        rankOf(actor);
        enemyRankOf(target);
        return actor.isAlive() && target.isAlive() && hasBow(actor);
    }

    private boolean hasBow(PlayerRuntimeV5 actor) {
        int id = actor.equippedItemId(EquipSlot.BOW);
        if (id == 0) return false;
        ItemDefinition item = ItemTable.get(id);
        return item.category() == ItemCategory.BOW;
    }

    /**
     * Initial ranged formula: bow attack and enchantment plus a modest Agility
     * bonus and d4 variance. Equipped bows have unlimited shots by design.
     */
    private void resolveRanged(PlayerOrder order, List<CombatEvent> events) {
        PlayerRuntimeV5 attacker = order.actor();
        Monster target = order.enemyTarget();
        if (!target.isAlive()) {
            events.add(new CombatEvent.ActionSkipped(attacker.name(), target.getName(),
                    "TARGET_DEFEATED"));
            return;
        }
        if (!canRangedReach(attacker, target)) {
            events.add(new CombatEvent.ActionSkipped(attacker.name(), target.getName(),
                    "BOW_UNAVAILABLE"));
            return;
        }
        if (!FormulaEngine.playerRollToHit(attacker, target)) {
            events.add(new CombatEvent.Miss(attacker.name(), target.getName()));
            return;
        }
        ItemDefinition bow = ItemTable.get(attacker.equippedItemId(EquipSlot.BOW));
        int damage = Math.min(99, Math.max(1, bow.attack() + bow.enchant()
                + attacker.attribute(Attribute.AGILITY) / 4 + Dice.d4()));
        boolean critical = FormulaEngine.rollCritical();
        if (critical) damage = FormulaEngine.applyCritical(damage);
        target.applyDamage(damage);
        events.add(new CombatEvent.Hit(attacker.name(), target.getName(), damage,
                "Body", critical));
        if (!target.isAlive()) events.add(new CombatEvent.Death(target.getName(), false));
    }

    private void resolveCast(PlayerOrder order, List<CombatEvent> events) {
        Spell spell = order.spell();
        PlayerRuntimeV5 caster = order.actor();
        // Track monster identity, not display names. A defeated monster may
        // share a name with the target actually struck by this cast.
        List<Monster> aliveBeforeDamage = spell.getEffectType() == Spell.EffectType.DAMAGE
                ? enemyTargets(order).stream().filter(Monster::isAlive).toList()
                : List.of();
        SpellCastingV5.Result result = switch (spell.getEffectType()) {
            case DAMAGE -> SpellCastingV5.castDamage(caster, spell, enemyTargets(order));
            case SLEEP -> SpellCastingV5.castSleep(caster, spell, enemyTargets(order));
            case HEAL -> SpellCastingV5.castHeal(caster, spell, allyTargets(order));
            case AWAKEN -> SpellCastingV5.castAwaken(caster, spell, allyTargets(order));
            default -> throw new IllegalStateException("Order validation missed effect");
        };
        if (!result.cast()) {
            boolean hasLivingTarget = switch (spell.getEffectType()) {
                case DAMAGE, SLEEP -> enemyTargets(order).stream().anyMatch(Monster::isAlive);
                case HEAL, AWAKEN -> allyTargets(order).stream().anyMatch(PlayerRuntimeV5::isAlive);
                default -> false;
            };
            String reason = !caster.isAlive() ? "Caster unable to act"
                    : !hasLivingTarget ? "No valid target" : "Insufficient MP";
            events.add(new CombatEvent.SpellCast(caster.name(), spell.getName(),
                    reason, 0, false));
            return;
        }
        // Status casts need an explicit announcement even when every target
        // resists or is already awake. Emit it before individual outcomes.
        boolean statusSpell = spell.getEffectType() == Spell.EffectType.SLEEP
                || spell.getEffectType() == Spell.EffectType.AWAKEN;
        if (statusSpell)
            events.add(new CombatEvent.SpellCast(caster.name(), spell.getName(),
                    "No effect", 0, true));
        for (String missedTarget : result.missedTargets())
            events.add(new CombatEvent.Miss(caster.name(), missedTarget));
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
        if (!statusSpell && result.impacts().isEmpty() && result.missedTargets().isEmpty())
            events.add(new CombatEvent.SpellCast(caster.name(), spell.getName(),
                    "No effect", 0, true));
        // Only monsters alive immediately before this cast can have died
        // from it. Never match death events using non-unique display names.
        for (Monster enemy : aliveBeforeDamage)
            if (!enemy.isAlive())
                events.add(new CombatEvent.Death(enemy.getName(), false));
    }

    private List<Monster> enemyTargets(PlayerOrder order) {
        if (order.spell().getTargetType() != Spell.TargetType.ALL_ENEMIES)
            return List.of(order.enemyTarget());
        // null selector = both/all occupied ranks; a selected enemy designates
        // that entire rank, even if another enemy is ahead of it.
        if (order.enemyTarget() == null) return enemies;
        CombatRank selected = enemyRankOf(order.enemyTarget());
        return enemies.stream().filter(m -> enemyRankOf(m) == selected).toList();
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
