package com.phantasia.j2d.diagnostic;

import com.phantasia.core.data.EncounterFactory;
import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;
import com.phantasia.j2d.engine.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/** Isolated native-v5 vertical slice. Fixture progression values are not game balance. */
public final class NativeProgressionScreen implements Screen {
    public record LocalContext(com.phantasia.j2d.screen.LocalMapScreen.Visit visit, boolean guild,
                               com.phantasia.core.world.ServiceData.GuildData terms, com.phantasia.core.world.PlacedFeature feature) {
        public LocalContext(com.phantasia.j2d.screen.LocalMapScreen.Visit visit, boolean guild) { this(visit, guild, null, null); }
    }
    private final ScreenManager manager;
    private NativeCampaignV5 campaign;
    private LocalContext local;
    private int selected, targetIndex;
    private String message = "Win a test battle, train at the guild, then ready the learned technique.";
    private List<String> log = List.of();
    private final Set<Integer> pressed = new HashSet<>();
    private static final List<Action> TECHNIQUES = List.of(Action.THRUST, Action.SLASH, Action.LUNGE, Action.AIM_BLOW);
    public NativeProgressionScreen(ScreenManager manager) { this.manager = manager; reset(); }
    public NativeProgressionScreen(ScreenManager manager, NativeCampaignV5 campaign) { this.manager = manager; this.campaign = java.util.Objects.requireNonNull(campaign); }
    public NativeCampaignV5 campaign() { return campaign; }
    public void reset() {
        campaign = freshCampaign(); selected = 0; targetIndex = 0; log = List.of();
    }
    public static NativeCampaignV5 freshCampaign() {
        var first = PlayerCreationV5.create("Native test A", 1, Job.FIGHTER, 0, 20, 20, 15, 100, 10);
        var second = PlayerCreationV5.create("Native test B", 2, Job.FIGHTER, 0, 18, 18, 15, 100, 10);
        return new NativeCampaignV5(List.of(first, second), 40, 2, List.of(
                new NativeCampaignV5.TrainingStep(Job.FIGHTER, 2, 20, 10, 4, 1, List.of(Action.THRUST)),
                new NativeCampaignV5.TrainingStep(Job.FIGHTER, 3, 40, 20, 4, 1, List.of(Action.SLASH))));
    }
    @Override public void onEnter(Object data) {
        pressed.clear(); local = data instanceof LocalContext context ? context : null;
        if (local != null) {
            if (local.visit().nativeEncounters().campaign() != campaign) throw new IllegalArgumentException("Different native campaign");
            if (campaign.activeBattle() != null && campaign.activeBattle().outcome() == CombatOutcome.ONGOING) defaultOrders();
            log = List.of(); message = campaign.activeBattle() != null ? "Review orders, then Enter to run one round."
                    : local.guild() ? "Guild: G trains the selected member. Esc returns to the area." : "Prepare techniques here. Visit a trainer to learn more. Esc returns to the area.";
        }
    }
    @Override public void onExit() { pressed.clear(); }
    @Override public void update(float dt) {}
    @Override public void onMouseClicked(MouseEvent e) {}
    @Override public void onKeyReleased(KeyEvent e) { pressed.remove(e.getKeyCode()); }
    @Override public void onKeyPressed(KeyEvent e) {
        if (!pressed.add(e.getKeyCode())) return;
        try { handle(e.getKeyCode()); }
        catch (IllegalArgumentException | IllegalStateException ex) { message = ex.getMessage(); }
    }
    private void handle(int key) {
        if (key == KeyEvent.VK_R) {
            if (local != null) { message = "Return to the area and use Esc / R to reset the whole map session."; return; }
            reset(); message = "Fresh native test party."; return;
        }
        var battle = campaign.activeBattle();
        if (key == KeyEvent.VK_UP) { selected = Math.max(0, selected - 1); return; }
        if (key == KeyEvent.VK_DOWN) { selected = Math.min(campaign.party().size() - 1, selected + 1); return; }
        var member = campaign.party().get(selected);
        if (battle == null) {
            switch (key) {
                case KeyEvent.VK_ESCAPE -> manager.switchTo(local == null ? GameState.WORLD_ROAM : GameState.NATIVE_LOCAL_MAP, local == null ? null : local.visit());
                case KeyEvent.VK_B -> {
                    if (local != null) { message = "Battles begin while exploring the dungeon."; return; }
                    campaign.startBattle(List.of(EncounterFactory.buildMonster("Native foe", 12, 1, 40)), ignored -> 10);
                    targetIndex = 0; defaultOrders(); message = "Review orders, then Enter to run one round."; log = List.of();
                }
                case KeyEvent.VK_G -> {
                    if (local != null && !local.guild()) { message = "Visit a guild trainer to gain a level and learn techniques."; return; }
                    if (local != null && local.feature() != null && !local.visit().traversal().featureAvailable(local.feature()))
                        throw new IllegalStateException("This guild counter has already been resolved.");
                    var result = campaign.train(member, local == null ? null : local.terms());
                    if (local != null && local.feature() != null) local.visit().traversal().completeInteraction(local.feature());
                    message = member.runtime().name() + " trained to level " + result.level() + "; learned " + result.learned();
                }
                default -> {
                    int index = key - KeyEvent.VK_1;
                    if (index >= 0 && index < TECHNIQUES.size()) {
                        var list = new ArrayList<>(member.readied()); var action = TECHNIQUES.get(index);
                        if (!list.remove(action)) list.add(action);
                        campaign.ready(member, list); message = "Readied: " + member.readied();
                    }
                }
            }
            return;
        }
        if (battle.outcome() != CombatOutcome.ONGOING) {
            if (key == KeyEvent.VK_ENTER) {
                if (local != null) {
                    local.visit().nativeEncounters().returnFromBattle(battle);
                    manager.switchTo(GameState.NATIVE_LOCAL_MAP, local.visit());
                } else { battle.acknowledge(); message = "Returned to preparation. Guild training: G."; }
            }
            return;
        }
        if (key == KeyEvent.VK_ENTER) {
            var result = battle.confirmRound(); log = new CombatNarrator().narrateRound(result);
            if (result.isCombatOver()) {
                var reward = battle.settle(); message = result.outcome() + ": XP pool " + reward.experiencePool()
                        + ", gold " + reward.gold() + ". Enter: " + (local == null ? "return to preparation." : "return to area.");
            } else { defaultOrders(); message = "Review the next round. No action runs until Enter."; }
        } else if (key == KeyEvent.VK_S) {
            var choices = DiagnosticSpells.BOOK.available(member);
            if (choices.isEmpty()) throw new IllegalArgumentException("No test spells for this job and level. C at the entry menu starts a fresh caster party.");
            var previous = battle.rows().get(selected).order();
            int index = -1;
            for (int i = 0; i < choices.size(); i++) if (previous != null && previous.spell() == choices.get(i).spell()) index = i;
            var choice = choices.get((index + 1) % choices.size());
            battle.selectSpell(member, DiagnosticSpells.BOOK, choice.id(), null, null);
            message = choice.spell().getName() + " selected (" + choice.spell().getMpCost() + " MP). T enemy / Y ally for single targets.";
        } else if (key == KeyEvent.VK_I) {
            var slots = battle.potionSlots(member).stream().filter(slot -> !battle.potionTargets(member, slot).isEmpty()).toList();
            if (slots.isEmpty()) throw new IllegalArgumentException("No carried potion can help a living ally right now.");
            var previous = battle.rows().get(selected).order();
            int index = previous != null && previous.itemUse() != null ? slots.indexOf(previous.itemUse().slot()) : -1;
            battle.selectPotion(member, slots.get((index + 1) % slots.size()), null);
            message = "Potion selected. Y changes ally; Enter confirms the full party plan.";
        } else if (key == KeyEvent.VK_Y) {
            var previous = battle.rows().get(selected).order();
            if (previous != null && previous.spell() != null) {
                if (previous.spell().getTargetType() != Spell.TargetType.SINGLE_ALLY) throw new IllegalArgumentException("This spell has no individual ally target.");
                var targets = campaign.party().stream().filter(NativeCampaignV5.Member::isAlive).toList();
                int index = -1; for (int i = 0; i < targets.size(); i++) if (targets.get(i).runtime() == previous.allyTarget()) index = i;
                int id = spellId(previous.spell());
                battle.selectSpell(member, DiagnosticSpells.BOOK, id, null, targets.get((index + 1) % targets.size()));
                message = "Spell ally updated. Review before Enter."; return;
            }
            if (previous == null || previous.itemUse() == null) throw new IllegalArgumentException("Select a potion or ally spell first.");
            var targets = battle.potionTargets(member, previous.itemUse().slot());
            if (targets.isEmpty()) throw new IllegalArgumentException("No living ally needs this potion.");
            int index = -1; for (int i = 0; i < targets.size(); i++) if (targets.get(i).runtime() == previous.allyTarget()) index = i;
            battle.selectPotion(member, previous.itemUse().slot(), targets.get((index + 1) % targets.size()));
            message = "Potion ally updated. Review the order before Enter.";
        } else if (key == KeyEvent.VK_T) {
            var living = battle.enemies().stream().filter(Monster::isAlive).toList();
            targetIndex = (targetIndex + 1) % living.size();
            var previous = battle.rows().get(selected).order();
            if (previous == null || previous.action() == Action.PARRY || previous.action() == Action.RUN || previous.itemUse() != null)
                throw new IllegalArgumentException("Choose an attack before overriding its target");
            if (previous.spell() != null) {
                if (previous.spell().getTargetType() != Spell.TargetType.SINGLE_ENEMY) throw new IllegalArgumentException("This spell has no individual enemy target.");
                battle.selectSpell(member, DiagnosticSpells.BOOK, spellId(previous.spell()), living.get(targetIndex), null);
            } else battle.select(member, previous.action(), living.get(targetIndex));
            message = "Override target: " + living.get(targetIndex).getName();
        } else {
            Action action = key == KeyEvent.VK_A ? Action.ATTACK : key == KeyEvent.VK_D ? Action.PARRY : key == KeyEvent.VK_F ? Action.RUN : null;
            int slot = key - KeyEvent.VK_1;
            if (slot >= 0 && slot < member.readied().size()) action = member.readied().get(slot);
            if (action != null) {
                var living = battle.enemies().stream().filter(Monster::isAlive).toList();
                battle.select(member, action, action == Action.PARRY || action == Action.RUN ? null : living.get(Math.min(targetIndex, living.size() - 1)));
                message = member.runtime().name() + ": " + action + (action == Action.RUN ? " (" + FormulaEngine.escapeChance(member.runtime()) + "% chance). Success withdraws the party." : "");
            }
        }
    }
    private static int spellId(Spell spell) {
        return DiagnosticSpells.BOOK.idOf(spell);
    }
    private void defaultOrders() {
        var battle = campaign.activeBattle();
        for (int i = 0; i < campaign.party().size(); i++) {
            var member = campaign.party().get(i);
            if (!member.runtime().isAlive()) continue;
            var previous = battle.rows().get(i).order();
            if (previous != null && (previous.itemUse() != null || previous.spell() != null)) { battle.clearOrder(member); continue; }
            var target = previous == null ? null : previous.enemyTarget();
            if (target != null && !target.isAlive()) target = null;
            battle.select(member, previous == null ? Action.ATTACK : previous.action(), target);
        }
    }
    @Override public void render(Graphics2D g) {
        int w = GameCanvas.DESIGN_WIDTH, h = GameCanvas.DESIGN_HEIGHT;
        g.setColor(new Color(20, 26, 31)); g.fillRect(0, 0, w, h);
        g.setColor(new Color(240, 210, 125)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        g.drawString("Native-v5 / battle, guild and readied techniques", 70, 75);
        g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 23));
        g.drawString(local == null ? "Non-canon test rules. N explores with this native party; checkpoints through menu/pause."
                : "Same native party as exploration. " + (local.guild() ? "Guild training." : "Field preparation / battle."), 70, 117);
        g.drawString("Gold " + campaign.gold() + "   Pending " + campaign.pendingGold() + (local == null ? "   R: reset native test" : "   Reset through the map pause menu."), 70, 156);
        var battle = campaign.activeBattle();
        int y = 210;
        for (int i = 0; i < campaign.party().size(); i++) {
            var m = campaign.party().get(i); var p = m.runtime();
            g.setColor(i == selected ? new Color(255, 225, 130) : Color.WHITE);
            g.drawString((i == selected ? "> " : "  ") + p.name() + "   L" + m.level() + "   XP " + p.experience()
                    + "   HP " + p.hp() + "/" + p.maxHp() + "   MP " + p.mp() + "/" + p.maxMp(), 70, y);
            g.drawString("   Learned " + m.learned() + "   Readied " + m.readied(), 70, y + 36);
            if (battle != null) {
                var row = battle.rows().get(i);
                String order = row.order() == null ? "choose" : row.order().itemUse() != null
                        ? com.phantasia.core.model.item.ItemTable.get(row.order().itemUse().itemId()).name()
                                + " [slot " + (row.order().itemUse().slot() + 1) + "] -> " + row.order().allyTarget().name()
                        : row.order().spell() != null ? row.order().spell().getName() + " (" + row.order().spell().getMpCost() + " MP) -> "
                                + (row.order().spell().getTargetType() == Spell.TargetType.ALL_ENEMIES ? "all enemies"
                                : row.order().spell().getTargetType() == Spell.TargetType.ALL_ALLIES ? "all allies"
                                : row.order().allyTarget() != null ? row.order().allyTarget().name() : row.order().enemyTarget().getName())
                        : row.order().action() + (row.order().enemyTarget() == null ? "" : " -> " + row.order().enemyTarget().getName());
                g.drawString("   Order: " + order + "   " + row.status(), 70, y + 72);
            } else {
                var step = campaign.nextTraining(m);
                g.drawString(step == null ? "   No further test training defined."
                        : "   Next guild level: XP " + step.experience() + ", gold " + campaign.trainingCost(m, local == null ? null : local.terms())
                                + ", learns " + step.learns() + (local != null && local.terms() != null && local.terms().maxLevel() != 0 ? "; guild cap " + local.terms().maxLevel() : ""), 70, y + 72);
            }
            y += 120;
        }
        g.setColor(Color.WHITE);
        if (battle == null) {
            g.drawString(local == null ? "Up/Down: member   B: battle   G: train selected member   Esc: menu"
                    : "Up/Down: member   " + (local.guild() ? "G: guild training   " : "") + "Esc: return to area", 70, y + 20);
            g.drawString("Outside battle: 1 Thrust / 2 Slash / 3 Lunge / 4 Aim Blow toggle readied (two slots)", 70, y + 57);
        } else {
            g.drawString("A attack   D defend   F run   S spell   I potion   Y ally   1/2 technique   T enemy   Enter confirm / result", 70, y + 20);
            g.drawString("Enemies: " + String.join(", ", battle.enemies().stream().map(e -> e.getName() + " HP " + e.getHp()).toList()), 70, y + 57);
        }
        g.setColor(new Color(240, 210, 125)); g.drawString(message, 70, y + 103);
        g.setColor(Color.WHITE); int lineY = y + 148;
        for (var line : log.stream().skip(Math.max(0, log.size() - 7)).toList()) {
            if (lineY > h - 30) break; g.drawString(line, 70, lineY); lineY += 31;
        }
    }
}
