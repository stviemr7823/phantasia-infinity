package com.phantasia.core.logic;

import com.phantasia.core.model.*;
import java.util.*;

/** Single owner for a fresh native party, guild progression and encounter lifecycle.
 * Rules are supplied by the caller: no new canonical progression table is implied.
 * Learned/readied state is campaign state, never written into reserved record bytes.
 */
public final class NativeCampaignV5 {
    private static final Set<Action> TECHNIQUES = Set.of(Action.THRUST, Action.SLASH, Action.LUNGE, Action.AIM_BLOW);
    public record TrainingStep(Job job, int level, long experience, int cost,
                               int hpGain, int mpGain, List<Action> learns) {
        public TrainingStep {
            Objects.requireNonNull(job); learns = List.copyOf(learns);
            if (level < 2 || level > 255 || experience < 0 || experience > 0xFFFFFFFFL
                    || cost < 0 || hpGain < 0 || hpGain > 65535 || mpGain < 0 || mpGain > 65535
                    || new HashSet<>(learns).size() != learns.size() || !TECHNIQUES.containsAll(learns))
                throw new IllegalArgumentException("Invalid training step");
        }
    }
    public static final class Member {
        private final PlayerStateV5 state;
        private final PlayerRuntimeV5 runtime;
        private final Set<Action> learned = EnumSet.noneOf(Action.class);
        private List<Action> readied = List.of();
        private Member(PlayerStateV5 input) {
            state = PlayerStateV5.fromBytes(input.toBytes()); runtime = new PlayerRuntimeV5(state);
            if (state.level() != 1) throw new IllegalArgumentException("Fresh campaigns require level-one players");
            if (Arrays.stream(Job.values()).noneMatch(j -> j.getBitValue() == state.jobId()))
                throw new IllegalArgumentException("Unknown job ID");
            if (state.maxHp() == 0 || state.hp() > state.maxHp() || state.mp() > state.maxMp())
                throw new IllegalArgumentException("Invalid starting vitals");
        }
        public PlayerRuntimeV5 runtime() { return runtime; }
        public int level() { return state.level(); }
        public Set<Action> learned() { return Set.copyOf(learned); }
        public List<Action> readied() { return readied; }
    }
    public record TrainingResult(int level, int cost, List<Action> learned) {}
    public record Reward(long experiencePool, long gold, boolean goldClaimed) {}
    private final List<Member> party;
    private final List<TrainingStep> curriculum;
    private final int readySlots;
    private long gold, pendingGold;
    private Battle active;

    public NativeCampaignV5(List<PlayerStateV5> players, int gold, int readySlots, List<TrainingStep> curriculum) {
        if (players.isEmpty() || players.size() > 6 || gold < 0 || readySlots < 1 || readySlots > TECHNIQUES.size())
            throw new IllegalArgumentException("Invalid campaign configuration");
        Set<PlayerStateV5> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Long> ids = new HashSet<>();
        for (var p : players) {
            Objects.requireNonNull(p);
            if (!seen.add(p) || (p.recordId() != 0 && !ids.add(p.recordId())))
                throw new IllegalArgumentException("Duplicate party member");
        }
        this.curriculum = List.copyOf(curriculum);
        for (Job job : Job.values()) {
            var steps = this.curriculum.stream().filter(s -> s.job() == job)
                    .sorted(Comparator.comparingInt(TrainingStep::level)).toList();
            int previousLevel = 1; long previousXp = -1;
            for (var step : steps) {
                if (step.level() != previousLevel + 1 || step.experience() < previousXp)
                    throw new IllegalArgumentException("Training steps must be contiguous with nondecreasing XP");
                previousLevel = step.level(); previousXp = step.experience();
            }
        }
        party = players.stream().map(Member::new).toList(); this.gold = gold; this.readySlots = readySlots;
    }
    public List<Member> party() { return party; }
    public long gold() { return gold; }
    public long pendingGold() { return pendingGold; }
    public int readySlots() { return readySlots; }
    public Battle activeBattle() { return active; }
    private void requireMember(Member member) {
        if (!party.contains(member)) throw new IllegalArgumentException("Not a campaign member");
    }
    private void outsideBattle() {
        if (active != null) throw new IllegalStateException("Finish the battle before changing preparation");
    }
    public TrainingStep nextTraining(Member member) {
        requireMember(member);
        return curriculum.stream().filter(s -> s.job().getBitValue() == member.state.jobId()
                && s.level() == member.level() + 1).findFirst().orElse(null);
    }
    public boolean canTrain(Member member) {
        var step = nextTraining(member);
        return active == null && member.runtime.isAlive() && !member.runtime.isAsleep() && step != null
                && member.runtime.experience() >= step.experience() && gold >= step.cost()
                && (long) member.state.maxHp() + step.hpGain() <= 65535
                && (long) member.state.maxMp() + step.mpGain() <= 65535;
    }
    /** All checks precede the level, resource, gold and learned-set commit. */
    public TrainingResult train(Member member) {
        requireMember(member); outsideBattle();
        if (!canTrain(member)) throw new IllegalStateException("Guild training requirements are not met");
        var step = nextTraining(member);
        var newlyLearned = step.learns().stream().filter(a -> !member.learned.contains(a)).toList();
        gold -= step.cost(); member.state.setLevel(step.level());
        member.state.setMaxHp(member.state.maxHp() + step.hpGain());
        member.state.setHp(member.state.hp() + step.hpGain());
        member.state.setMaxMp(member.state.maxMp() + step.mpGain());
        member.state.setMp(member.state.mp() + step.mpGain());
        member.learned.addAll(newlyLearned); retryGold();
        return new TrainingResult(step.level(), step.cost(), newlyLearned);
    }
    /** Replace the full selection, atomically, anywhere outside battle. */
    public void ready(Member member, List<Action> techniques) {
        requireMember(member); outsideBattle(); var copy = List.copyOf(techniques);
        if (copy.size() > readySlots || new HashSet<>(copy).size() != copy.size()
                || !member.learned.containsAll(copy)) throw new IllegalArgumentException("Invalid readied techniques");
        member.readied = copy;
    }
    private void retryGold() {
        long collected = Math.min(pendingGold, Integer.MAX_VALUE - gold);
        gold += collected; pendingGold -= collected;
    }
    public Battle startBattle(List<Monster> enemies, java.util.function.ToIntFunction<Monster> enemyAgility) {
        outsideBattle();
        if (party.stream().noneMatch(m -> m.runtime.isAlive())) throw new IllegalStateException("Party has fallen");
        var copy = List.copyOf(enemies);
        if (copy.isEmpty() || copy.stream().anyMatch(e -> !e.isAlive())) throw new IllegalArgumentException("Living enemies required");
        long xp = copy.stream().mapToLong(Monster::getExperience).sum();
        long treasure = copy.stream().mapToLong(Monster::getTreasure).sum();
        Math.addExact(pendingGold, treasure);
        var combat = CombatRoundV5.agilityOrdered(party.stream().map(Member::runtime).toList(), copy, enemyAgility);
        active = new Battle(combat, copy, xp, treasure); return active;
    }
    public final class Battle {
        private final CombatRoundV5 combat;
        private final CombatOrderPlannerV5 planner;
        private final List<Monster> enemies;
        private final long xp, treasure;
        private boolean settled, acknowledged;
        private Reward reward;
        private Battle(CombatRoundV5 combat, List<Monster> enemies, long xp, long treasure) {
            this.combat = combat; planner = new CombatOrderPlannerV5(combat); this.enemies = enemies;
            this.xp = xp; this.treasure = treasure;
        }
        public List<Monster> enemies() { return enemies; }
        public CombatOutcome outcome() { return combat.outcome(); }
        public List<CombatOrderPlannerV5.OrderRow> rows() { return planner.orderRows(); }
        public boolean canConfirm() { return !acknowledged && planner.canConfirm(); }
        private void requireActive() { if (active != this || acknowledged) throw new IllegalStateException("Battle is no longer active"); }
        /** Null target selects the first living reachable enemy; explicit overrides are validated. */
        public void select(Member member, Action action, Monster target) {
            requireActive(); requireMember(member); Objects.requireNonNull(action);
            if (!member.runtime.isAlive()) throw new IllegalArgumentException("Inactive member");
            if (action != Action.ATTACK && action != Action.PARRY && !member.readied.contains(action))
                throw new IllegalArgumentException("Technique is not readied");
            CombatRoundV5.PlayerOrder order;
            if (action == Action.PARRY) {
                if (target != null) throw new IllegalArgumentException("Defend takes no target");
                order = CombatRoundV5.PlayerOrder.parry(member.runtime);
            } else {
                if (target == null) target = enemies.stream().filter(Monster::isAlive)
                        .filter(e -> action == Action.LUNGE ? combat.canLungeReach(member.runtime, e)
                                : combat.canMeleeReach(member.runtime, e)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No reachable enemy"));
                if (!target.isAlive()) throw new IllegalArgumentException("Target has fallen");
                order = new CombatRoundV5.PlayerOrder(member.runtime, action, target, null, null, combat.rankOf(member.runtime));
            }
            combat.validateOrders(List.of(order)); planner.setOrder(order);
        }
        public RoundResult confirmRound() {
            requireActive(); var result = planner.confirmRound();
            if (result.isCombatOver()) settle(); return result;
        }
        /** Terminal reward receipt is idempotent, including repeated UI entry. */
        public Reward settle() {
            if (settled) return reward;
            requireActive();
            if (outcome() == CombatOutcome.ONGOING) throw new IllegalStateException("Battle is still active");
            if (outcome() == CombatOutcome.VICTORY) {
                var survivors = party.stream().filter(m -> m.runtime.isAlive()).toList();
                long share = xp / survivors.size();
                for (var m : survivors) m.runtime.awardExperience(Math.min(share, 0xFFFFFFFFL - m.runtime.experience()));
                pendingGold = Math.addExact(pendingGold, treasure); retryGold();
                reward = new Reward(xp, treasure, pendingGold == 0);
            } else reward = new Reward(0, 0, true);
            settled = true; return reward;
        }
        public void acknowledge() {
            if (acknowledged) return;
            requireActive(); settle(); acknowledged = true; active = null;
        }
    }
}
