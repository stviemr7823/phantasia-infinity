package com.phantasia.core.logic;

import com.phantasia.core.model.*;
import com.phantasia.core.data.*;
import com.phantasia.core.world.WorldPosition;
import java.util.*;

/** Single owner for a fresh native party, guild progression and encounter lifecycle.
 * Rules are supplied by the caller: no new canonical progression table is implied.
 * Learned/readied state is campaign state, never written into reserved record bytes.
 */
public final class NativeCampaignV5 implements LocalSession {
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
    public static final class Member implements LocalSession.Member {
        private final PlayerStateV5 state;
        private final PlayerRuntimeV5 runtime;
        private final Set<Action> learned = EnumSet.noneOf(Action.class);
        private List<Action> readied = List.of();
        private Member(PlayerStateV5 input, boolean fresh) {
            state = PlayerStateV5.fromBytes(input.toBytes()); runtime = new PlayerRuntimeV5(state);
            if (state.level() < 1 || fresh && state.level() != 1) throw new IllegalArgumentException("Invalid starting level");
            if (Arrays.stream(Job.values()).noneMatch(j -> j.getBitValue() == state.jobId()))
                throw new IllegalArgumentException("Unknown job ID");
            if (state.maxHp() == 0 || state.hp() > state.maxHp() || state.mp() > state.maxMp())
                throw new IllegalArgumentException("Invalid starting vitals");
        }
        public PlayerRuntimeV5 runtime() { return runtime; }
        public int level() { return state.level(); }
        public Set<Action> learned() { return Set.copyOf(learned); }
        public List<Action> readied() { return readied; }
        public String getName() { return runtime.name(); }
        public int getHp() { return runtime.hp(); }
        public int getMaxHp() { return runtime.maxHp(); }
        public void setHp(int hp) {
            if (hp < 0 || hp > state.maxHp()) throw new IllegalArgumentException("HP outside native vitals");
            state.setHp(hp);
        }
        public boolean isAlive() { return runtime.isAlive(); }
        public boolean isActiveInParty() { return isAlive(); }
        public int getAgility() { return runtime.attribute(PlayerStateV5.Attribute.AGILITY); }
        public int getLevel() { return level(); }
        public int getJob() { return state.jobId(); }
        public int inventorySlots() { return DataLayoutV5.PC_INVENTORY.length(); }
        public int getInventoryItemId(int slot) { return state.inventory(slot); }
        public void setInventoryItem(int slot, int id) { state.setInventory(slot, id); }
    }
    public record TrainingResult(int level, int cost, List<Action> learned) {}
    public record Reward(long experiencePool, long gold, boolean goldClaimed) {}
    private final List<Member> party;
    private final List<TrainingStep> curriculum;
    private final int readySlots;
    private long gold, pendingGold;
    private int bankGold;
    private NativeShopState shopState = new NativeShopState();
    public NativeShopState shopState() { return shopState; }
    public record ShopReceipt(int itemId, int price, int remaining) {}
    public ShopReceipt buy(String catalog, ShopInventory shop, int row, Member member) {
        outsideBattle(); requireMember(member);
        if (party.stream().noneMatch(Member::isAlive)) throw new IllegalStateException("The party has fallen");
        var quote = shopState.quote(catalog, shop, row, this::hasFlag);
        if (!quote.priceInRange()) throw new IllegalStateException("Shop price exceeds the purse limit");
        if (!quote.visible() || quote.remaining() == 0) throw new IllegalStateException("This listing is unavailable or sold out");
        if (gold < quote.price()) throw new IllegalStateException("Not enough gold");
        int free = -1; for (int i = 0; i < member.inventorySlots(); i++) if (member.getInventoryItemId(i) == 0) { free = i; break; }
        if (free < 0) throw new IllegalStateException("The selected member's pack is full");
        int item = shop.getEntries().get(row).itemId();
        shopState.record(catalog, shop, row); // may reject the state budget before any inventory/gold mutation
        member.setInventoryItem(free, item); gold -= quote.price(); retryGold();
        return new ShopReceipt(item, quote.price(), shopState.quote(catalog, shop, row, this::hasFlag).remaining());
    }
    public int bankGold() { return bankGold; }
    public record RestQuote(long cost, int recipients, int revived) {}
    public RestQuote quoteRest(com.phantasia.core.world.ServiceData.InnData terms) {
        outsideBattle(); Objects.requireNonNull(terms);
        if (terms.costPerHp() < 0) throw new IllegalArgumentException("Negative inn rate");
        long cost = 0; int recipients = 0, revived = 0;
        for (var m : party) if (m.isAlive() || terms.restoredDead()) {
            recipients++; if (!m.isAlive()) revived++;
            cost += (long) (m.state.maxHp() - m.state.hp()) * terms.costPerHp() + m.state.maxMp() - m.state.mp();
        }
        return new RestQuote(cost, recipients, revived);
    }
    /** Requote immediately before committing. Dead members remain untouched unless explicitly authored. */
    public RestQuote rest(com.phantasia.core.world.ServiceData.InnData terms) {
        var quote = quoteRest(terms);
        if (quote.recipients() == 0 || quote.cost() > gold) throw new IllegalStateException("No eligible guests or insufficient inn gold");
        gold -= quote.cost();
        for (var m : party) if (m.isAlive() || terms.restoredDead()) {
            m.state.setHp(m.state.maxHp()); m.state.setMp(m.state.maxMp()); m.runtime.setAsleep(false);
        }
        retryGold(); return quote;
    }
    public record BankTransfer(int gross, int fee, int credited) {}
    /** Amount is the gross debit. Percentage fees round up once; the remainder is credited. */
    public static BankTransfer quoteBank(boolean deposit, int amount, com.phantasia.core.world.ServiceData.BankData terms) {
        Objects.requireNonNull(terms);
        if (amount <= 0 || terms.depositFeePercent() < 0 || terms.depositFeePercent() > 100
                || terms.withdrawalFeePercent() < 0 || terms.withdrawalFeePercent() > 100)
            throw new IllegalArgumentException("Bank amount or fee outside range");
        int rate = deposit ? terms.depositFeePercent() : terms.withdrawalFeePercent();
        int fee = (int) (((long) amount * rate + 99) / 100);
        return new BankTransfer(amount, fee, amount - fee);
    }
    public BankTransfer transferBank(boolean deposit, int amount, com.phantasia.core.world.ServiceData.BankData terms) {
        outsideBattle(); var quote = quoteBank(deposit, amount, terms);
        if (deposit) {
            if (gold < amount || (long) bankGold + quote.credited() > Integer.MAX_VALUE)
                throw new IllegalStateException("Insufficient purse gold or bank capacity");
            gold -= amount; bankGold += quote.credited();
        } else {
            if (bankGold < amount || gold + quote.credited() > Integer.MAX_VALUE)
                throw new IllegalStateException("Insufficient bank gold or purse capacity");
            bankGold -= amount; gold += quote.credited();
        }
        retryGold(); return quote;
    }
    private Battle active;
    private long questFlags;
    private WorldPosition worldPosition = new WorldPosition(0, 0);
    private final LocalInteractionState localInteractions = new LocalInteractionState();
    public List<Member> localMembers() { return party; }
    public int getPartyGold() { return (int) gold; }
    public void addGold(int amount) {
        if (amount < 0 || gold + amount > Integer.MAX_VALUE) throw new IllegalArgumentException("Gold outside purse range");
        gold += amount;
    }
    public boolean spendGold(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative gold cost");
        if (gold < amount) return false;
        gold -= amount; return true;
    }
    public boolean hasFlag(QuestFlag flag) { return (questFlags & (1L << flag.bit)) != 0; }
    public void setFlag(QuestFlag flag) { questFlags |= 1L << flag.bit; }
    public WorldPosition getWorldPosition() { return worldPosition; }
    public void setPosition(WorldPosition position) { worldPosition = Objects.requireNonNull(position); }
    public LocalInteractionState getLocalInteractions() { return localInteractions; }

    public NativeCampaignV5(List<PlayerStateV5> players, int gold, int readySlots, List<TrainingStep> curriculum) {
        this(players, gold, readySlots, curriculum, true);
    }
    private NativeCampaignV5(List<PlayerStateV5> players, int gold, int readySlots, List<TrainingStep> curriculum, boolean fresh) {
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
        party = players.stream().map(p -> new Member(p, fresh)).toList(); this.gold = gold; this.readySlots = readySlots;
    }
    public record MemberSnapshot(byte[] record, List<Action> learned, List<Action> readied) {
        public MemberSnapshot { record = record.clone(); learned = List.copyOf(learned); readied = List.copyOf(readied); }
        @Override public byte[] record() { return record.clone(); }
    }
    public record Snapshot(List<MemberSnapshot> members, int gold, int bankGold, long pendingGold, int readySlots,
                           List<TrainingStep> curriculum, long questFlags, WorldPosition worldPosition,
                           Map<LocalInteractionState.Key, LootTable.LootResult> pendingChests, List<NativeShopState.Purchase> shopPurchases) {
        public Snapshot {
            members = List.copyOf(members); curriculum = List.copyOf(curriculum); shopPurchases = List.copyOf(shopPurchases); Objects.requireNonNull(worldPosition);
            var copy = new HashMap<LocalInteractionState.Key, LootTable.LootResult>();
            pendingChests.forEach((k, v) -> copy.put(k, new LootTable.LootResult(v.gold(), List.copyOf(v.items()))));
            pendingChests = Map.copyOf(copy);
        }
        public Snapshot(List<MemberSnapshot> members, int gold, int bankGold, long pendingGold, int readySlots,
                        List<TrainingStep> curriculum, long questFlags, WorldPosition worldPosition,
                        Map<LocalInteractionState.Key, LootTable.LootResult> pendingChests) {
            this(members, gold, bankGold, pendingGold, readySlots, curriculum, questFlags, worldPosition, pendingChests, List.of());
        }
        /** Source-compatible version-one snapshot construction. */
        public Snapshot(List<MemberSnapshot> members, int gold, long pendingGold, int readySlots,
                        List<TrainingStep> curriculum, long questFlags, WorldPosition worldPosition,
                        Map<LocalInteractionState.Key, LootTable.LootResult> pendingChests) {
            this(members, gold, 0, pendingGold, readySlots, curriculum, questFlags, worldPosition, pendingChests);
        }
    }
    /** Battle results must be acknowledged too: receipts cannot be lost across a checkpoint. */
    public Snapshot snapshot() {
        outsideBattle();
        return new Snapshot(party.stream().map(m -> new MemberSnapshot(m.state.toBytes(),
                m.learned.stream().sorted(Comparator.comparing(Enum::name)).toList(), m.readied)).toList(),
                (int) gold, bankGold, pendingGold, readySlots, curriculum, questFlags, worldPosition, localInteractions.snapshot(), shopState.snapshot());
    }
    /** Build and validate a detached owner; never patch the running campaign during import. */
    public static NativeCampaignV5 restore(Snapshot data) {
        if (data.bankGold() < 0 || data.pendingGold() < 0 || data.pendingGold() > Long.MAX_VALUE - Integer.MAX_VALUE
                || data.pendingChests().size() > 10000) throw new IllegalArgumentException("Invalid pending rewards");
        var states = data.members().stream().map(m -> PlayerStateV5.fromBytes(m.record())).toList();
        var restored = new NativeCampaignV5(states, data.gold(), data.readySlots(), data.curriculum(), false);
        restored.shopState = NativeShopState.restore(data.shopPurchases());
        for (int i = 0; i < states.size(); i++) {
            var state = states.get(i); var member = restored.party.get(i); var saved = data.members().get(i);
            if ((state.statusFlags() & ~PlayerRuntimeV5.STATUS_ASLEEP) != 0) throw new IllegalArgumentException("Unknown player status");
            var expected = EnumSet.noneOf(Action.class);
            for (int level = 2; level <= state.level(); level++) {
                final int at = level;
                var step = data.curriculum().stream().filter(s -> s.job().getBitValue() == state.jobId() && s.level() == at)
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Missing trained level"));
                if (state.experience() < step.experience()) throw new IllegalArgumentException("XP below trained level");
                expected.addAll(step.learns());
            }
            if (new HashSet<>(saved.learned()).size() != saved.learned().size()
                    || !expected.equals(new HashSet<>(saved.learned()))) throw new IllegalArgumentException("Invalid learned techniques");
            for (int slot = 0; slot < member.inventorySlots(); slot++) validateItem(state.inventory(slot));
            for (var slot : com.phantasia.core.model.item.EquipSlot.values()) {
                int id = state.equipment(slot); validateItem(id);
                if (id != 0) {
                    var item = com.phantasia.core.model.item.ItemTable.get(id);
                    if (!slot.accepts(item.category()) || item.levelRequirement() > state.level()
                            || item.jobRestriction() != 0 || item.raceRestriction() != 0)
                        throw new IllegalArgumentException("Invalid native equipment");
                }
            }
            member.learned.addAll(expected); restored.ready(member, saved.readied());
        }
        restored.bankGold = data.bankGold(); restored.pendingGold = data.pendingGold(); restored.questFlags = data.questFlags(); restored.worldPosition = data.worldPosition();
        data.pendingChests().forEach((key, loot) -> {
            if (key.mapId() < 0 || key.x() < 0 || key.y() < 0 || loot.gold() < 0 || loot.items().size() > 10000)
                throw new IllegalArgumentException("Invalid pending chest");
            for (var item : loot.items()) {
                validateItem(item.itemId());
                if (item.itemId() == 0 || item.quantity() <= 0 || item.weight() < 0) throw new IllegalArgumentException("Invalid pending item");
            }
            restored.localInteractions.remember(key, loot);
        });
        return restored;
    }
    private static void validateItem(int id) {
        if (id != 0 && !com.phantasia.core.model.item.ItemTable.exists(id)) throw new IllegalArgumentException("Unknown native item: " + id);
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
        return canTrain(member, null);
    }
    public long trainingCost(Member member, com.phantasia.core.world.ServiceData.GuildData terms) {
        var step = nextTraining(member);
        if (terms == null) return step == null ? 0 : step.cost();
        if (terms.trainingCostBase() < 0 || terms.trainingCostScale() < 0 || terms.maxLevel() < 0)
            throw new IllegalArgumentException("Invalid guild terms");
        return terms.trainingCostBase() + (long) member.level() * terms.trainingCostScale();
    }
    public boolean canTrain(Member member, com.phantasia.core.world.ServiceData.GuildData terms) {
        var step = nextTraining(member);
        long cost = trainingCost(member, terms);
        return active == null && member.runtime.isAlive() && !member.runtime.isAsleep() && step != null
                && (terms == null || terms.maxLevel() == 0 || step.level() <= terms.maxLevel())
                && member.runtime.experience() >= step.experience() && gold >= cost
                && (long) member.state.maxHp() + step.hpGain() <= 65535
                && (long) member.state.maxMp() + step.mpGain() <= 65535;
    }
    /** All checks precede the level, resource, gold and learned-set commit. */
    public TrainingResult train(Member member) {
        return train(member, null);
    }
    public TrainingResult train(Member member, com.phantasia.core.world.ServiceData.GuildData terms) {
        requireMember(member); outsideBattle();
        if (!canTrain(member, terms)) throw new IllegalStateException("Guild training requirements or level cap are not met");
        var step = nextTraining(member);
        int cost = (int) trainingCost(member, terms); // canTrain proved it fits the purse
        var newlyLearned = step.learns().stream().filter(a -> !member.learned.contains(a)).toList();
        gold -= cost; member.state.setLevel(step.level());
        member.state.setMaxHp(member.state.maxHp() + step.hpGain());
        member.state.setHp(member.state.hp() + step.hpGain());
        member.state.setMaxMp(member.state.maxMp() + step.mpGain());
        member.state.setMp(member.state.mp() + step.mpGain());
        member.learned.addAll(newlyLearned); retryGold();
        return new TrainingResult(step.level(), cost, newlyLearned);
    }
    /** Replace the full selection, atomically, anywhere outside battle. */
    public void ready(Member member, List<Action> techniques) {
        requireMember(member); outsideBattle(); var copy = List.copyOf(techniques);
        if (copy.size() > readySlots || new HashSet<>(copy).size() != copy.size()
                || !member.learned.containsAll(copy)) throw new IllegalArgumentException("Invalid readied techniques");
        member.readied = copy;
    }
    public void retryGold() {
        long collected = Math.min(pendingGold, Integer.MAX_VALUE - gold);
        gold += collected; pendingGold -= collected;
    }
    public Battle startBattle(List<Monster> enemies, java.util.function.ToIntFunction<Monster> enemyAgility) {
        return startBattle(enemies, enemyAgility, FormulaEngine::rollEscape);
    }
    /** Package-private seam; callers of the public API cannot force escape outcomes. */
    Battle startBattle(List<Monster> enemies, java.util.function.ToIntFunction<Monster> enemyAgility,
                       java.util.function.Predicate<PlayerRuntimeV5> escapeRoll) {
        outsideBattle();
        if (party.stream().noneMatch(m -> m.runtime.isAlive())) throw new IllegalStateException("Party has fallen");
        var copy = List.copyOf(enemies);
        if (copy.isEmpty() || copy.stream().anyMatch(e -> !e.isAlive())) throw new IllegalArgumentException("Living enemies required");
        long xp = copy.stream().mapToLong(Monster::getExperience).sum();
        long treasure = copy.stream().mapToLong(Monster::getTreasure).sum();
        Math.addExact(pendingGold, treasure);
        var combat = CombatRoundV5.agilityOrdered(party.stream().map(Member::runtime).toList(), copy, enemyAgility, escapeRoll);
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
        public List<CombatOrderPlannerV5.OrderRow> rows() {
            return planner.orderRows().stream().map(row -> row.status() == CombatOrderPlannerV5.RowStatus.READY
                    && row.order().spell() != null && (row.actor().mp() < row.order().spell().getMpCost()
                    || row.order().allyTarget() != null && !row.order().allyTarget().isAlive())
                    ? new CombatOrderPlannerV5.OrderRow(row.actor(), row.order(), CombatOrderPlannerV5.RowStatus.NEEDS_REVIEW) : row).toList();
        }
        public boolean canConfirm() { return !acknowledged && planner.canConfirm() && rows().stream().noneMatch(r -> r.status() == CombatOrderPlannerV5.RowStatus.NEEDS_REVIEW); }
        private void requireActive() { if (active != this || acknowledged) throw new IllegalStateException("Battle is no longer active"); }
        public List<Integer> potionSlots(Member member) {
            requireActive(); requireMember(member);
            return java.util.stream.IntStream.range(0, member.inventorySlots()).filter(slot -> {
                int id = member.getInventoryItemId(slot);
                return com.phantasia.core.model.item.ItemTable.exists(id) && com.phantasia.core.model.item.ItemTable.get(id).isConsumable();
            }).boxed().toList();
        }
        public List<Member> potionTargets(Member member, int slot) {
            requireActive(); requireMember(member);
            int id = member.runtime.inventoryItemId(slot);
            if (!com.phantasia.core.model.item.ItemTable.exists(id) || !com.phantasia.core.model.item.ItemTable.get(id).isConsumable())
                throw new IllegalArgumentException("Select a carried potion");
            return party.stream().filter(m -> CombatRoundV5.potionHelps(id, m.runtime)).toList();
        }
        /** Null ally chooses the first living party member who benefits, in party order. */
        public void selectPotion(Member member, int slot, Member target) {
            requireActive(); requireMember(member);
            if (!member.isAlive()) throw new IllegalArgumentException("Inactive potion user");
            if (target == null) target = potionTargets(member, slot).stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No living ally needs this potion"));
            requireMember(target);
            var order = CombatRoundV5.PlayerOrder.item(member.runtime, slot, target.runtime);
            combat.validateOrders(List.of(order)); planner.setOrder(order);
        }
        /** Null single targets choose the first living target; group casts use the complete living group. */
        public void selectSpell(Member member, NativeSpellBook book, int id, Monster enemy, Member ally) {
            requireActive(); requireMember(member);
            var entry = Objects.requireNonNull(book).entry(id); var spell = entry.spell();
            if (!member.isAlive() || !entry.permits(member)) throw new IllegalArgumentException("Spell is not available to this member");
            if (member.runtime.mp() < spell.getMpCost()) throw new IllegalArgumentException("Insufficient MP; review the order");
            switch (spell.getTargetType()) {
                case SINGLE_ENEMY -> {
                    if (ally != null) throw new IllegalArgumentException("Enemy spell takes no ally");
                    if (enemy == null) enemy = enemies.stream().filter(Monster::isAlive).findFirst().orElseThrow();
                    if (!enemy.isAlive()) throw new IllegalArgumentException("Target has fallen");
                }
                case SINGLE_ALLY -> {
                    if (enemy != null) throw new IllegalArgumentException("Ally spell takes no enemy");
                    if (ally == null) ally = party.stream().filter(Member::isAlive)
                            .filter(m -> spell.getEffectType() == Spell.EffectType.HEAL ? m.runtime.hp() < m.runtime.maxHp() : m.runtime.isAsleep())
                            .findFirst().orElseGet(() -> party.stream().filter(Member::isAlive).findFirst().orElseThrow());
                    requireMember(ally);
                    if (!ally.isAlive()) throw new IllegalArgumentException("Target has fallen");
                }
                case ALL_ENEMIES, ALL_ALLIES -> {
                    if (enemy != null || ally != null) throw new IllegalArgumentException("Group spell takes no individual target");
                }
            }
            var order = CombatRoundV5.PlayerOrder.cast(member.runtime, spell, enemy, ally == null ? null : ally.runtime);
            combat.validateOrders(List.of(order)); planner.setOrder(order);
        }
        public void clearOrder(Member member) { requireActive(); requireMember(member); planner.clearOrder(member.runtime); }
        /** Null target selects the first living reachable enemy; explicit overrides are validated. */
        public void select(Member member, Action action, Monster target) {
            requireActive(); requireMember(member); Objects.requireNonNull(action);
            if (action == Action.USE_ITEM) throw new IllegalArgumentException("Select a carried potion slot and ally");
            if (!member.runtime.isAlive()) throw new IllegalArgumentException("Inactive member");
            if (action != Action.ATTACK && action != Action.PARRY && action != Action.RUN && !member.readied.contains(action))
                throw new IllegalArgumentException("Technique is not readied");
            CombatRoundV5.PlayerOrder order;
            if (action == Action.PARRY || action == Action.RUN) {
                if (target != null) throw new IllegalArgumentException("Defend/run takes no target");
                order = action == Action.RUN ? CombatRoundV5.PlayerOrder.run(member.runtime) : CombatRoundV5.PlayerOrder.parry(member.runtime);
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
            requireActive();
            if (rows().stream().anyMatch(r -> r.status() == CombatOrderPlannerV5.RowStatus.NEEDS_REVIEW))
                throw new IllegalStateException("Orders need review before confirmation");
            var result = planner.confirmRound();
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
