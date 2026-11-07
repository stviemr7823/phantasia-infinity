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
    private final ScreenManager manager;
    private NativeCampaignV5 campaign;
    private int selected, targetIndex;
    private String message = "Win a test battle, train at the guild, then ready the learned technique.";
    private List<String> log = List.of();
    private final Set<Integer> pressed = new HashSet<>();
    private static final List<Action> TECHNIQUES = List.of(Action.THRUST, Action.SLASH, Action.LUNGE, Action.AIM_BLOW);
    public NativeProgressionScreen(ScreenManager manager) { this.manager = manager; reset(); }
    public NativeCampaignV5 campaign() { return campaign; }
    public void reset() {
        var first = PlayerCreationV5.create("Native test A", 1, Job.FIGHTER, 0, 20, 20, 15, 100, 10);
        var second = PlayerCreationV5.create("Native test B", 2, Job.FIGHTER, 0, 18, 18, 15, 100, 10);
        campaign = new NativeCampaignV5(List.of(first, second), 40, 2, List.of(
                new NativeCampaignV5.TrainingStep(Job.FIGHTER, 2, 20, 10, 4, 1, List.of(Action.THRUST)),
                new NativeCampaignV5.TrainingStep(Job.FIGHTER, 3, 40, 20, 4, 1, List.of(Action.SLASH))));
        selected = 0; targetIndex = 0; log = List.of();
    }
    @Override public void onEnter(Object data) { pressed.clear(); }
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
        if (key == KeyEvent.VK_R) { reset(); message = "Fresh native test party."; return; }
        var battle = campaign.activeBattle();
        if (key == KeyEvent.VK_UP) { selected = Math.max(0, selected - 1); return; }
        if (key == KeyEvent.VK_DOWN) { selected = Math.min(campaign.party().size() - 1, selected + 1); return; }
        var member = campaign.party().get(selected);
        if (battle == null) {
            switch (key) {
                case KeyEvent.VK_ESCAPE -> manager.switchTo(GameState.WORLD_ROAM, null);
                case KeyEvent.VK_B -> {
                    campaign.startBattle(List.of(EncounterFactory.buildMonster("Native foe", 12, 1, 40)), ignored -> 10);
                    targetIndex = 0; defaultOrders(); message = "Review orders, then Enter to run one round."; log = List.of();
                }
                case KeyEvent.VK_G -> {
                    var result = campaign.train(member);
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
            if (key == KeyEvent.VK_ENTER) { battle.acknowledge(); message = "Returned to preparation. Guild training: G."; }
            return;
        }
        if (key == KeyEvent.VK_ENTER) {
            var result = battle.confirmRound(); log = new CombatNarrator().narrateRound(result);
            if (result.isCombatOver()) {
                var reward = battle.settle(); message = result.outcome() + ": XP pool " + reward.experiencePool()
                        + ", gold " + reward.gold() + ". Enter: return to preparation.";
            } else { defaultOrders(); message = "Review the next round. No action runs until Enter."; }
        } else if (key == KeyEvent.VK_T) {
            var living = battle.enemies().stream().filter(Monster::isAlive).toList();
            targetIndex = (targetIndex + 1) % living.size();
            var previous = battle.rows().get(selected).order();
            if (previous == null || previous.action() == Action.PARRY)
                throw new IllegalArgumentException("Choose an attack before overriding its target");
            battle.select(member, previous.action(), living.get(targetIndex));
            message = "Override target: " + living.get(targetIndex).getName();
        } else {
            Action action = key == KeyEvent.VK_A ? Action.ATTACK : key == KeyEvent.VK_D ? Action.PARRY : null;
            int slot = key - KeyEvent.VK_1;
            if (slot >= 0 && slot < member.readied().size()) action = member.readied().get(slot);
            if (action != null) {
                var living = battle.enemies().stream().filter(Monster::isAlive).toList();
                battle.select(member, action, action == Action.PARRY ? null : living.get(Math.min(targetIndex, living.size() - 1)));
                message = member.runtime().name() + ": " + action;
            }
        }
    }
    private void defaultOrders() {
        var battle = campaign.activeBattle();
        for (int i = 0; i < campaign.party().size(); i++) {
            var member = campaign.party().get(i);
            if (!member.runtime().isAlive()) continue;
            var previous = battle.rows().get(i).order();
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
        g.drawString("Non-canon test rules. Separate party from the map mockup; no saves.", 70, 117);
        g.drawString("Gold " + campaign.gold() + "   Pending " + campaign.pendingGold() + "   R: reset native test", 70, 156);
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
                g.drawString("   Order: " + (row.order() == null ? "choose" : row.order().action()
                        + (row.order().enemyTarget() == null ? "" : " -> " + row.order().enemyTarget().getName())) + "   " + row.status(), 70, y + 72);
            } else {
                var step = campaign.nextTraining(m);
                g.drawString(step == null ? "   No further test training defined."
                        : "   Next guild level: XP " + step.experience() + ", gold " + step.cost() + ", learns " + step.learns(), 70, y + 72);
            }
            y += 120;
        }
        g.setColor(Color.WHITE);
        if (battle == null) {
            g.drawString("Up/Down: member   B: battle   G: train selected member   Esc: menu", 70, y + 20);
            g.drawString("Outside battle: 1 Thrust / 2 Slash / 3 Lunge / 4 Aim Blow toggle readied (two slots)", 70, y + 57);
        } else {
            g.drawString("A: attack   D: defend   1/2: readied slot   T: target   Enter: confirm round / result", 70, y + 20);
            g.drawString("Enemies: " + String.join(", ", battle.enemies().stream().map(e -> e.getName() + " HP " + e.getHp()).toList()), 70, y + 57);
        }
        g.setColor(new Color(240, 210, 125)); g.drawString(message, 70, y + 103);
        g.setColor(Color.WHITE); int lineY = y + 148;
        for (var line : log.stream().skip(Math.max(0, log.size() - 7)).toList()) {
            if (lineY > h - 30) break; g.drawString(line, 70, lineY); lineY += 31;
        }
    }
}
