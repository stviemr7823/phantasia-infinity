// phantasia-jme/src/main/java/com/phantasia/jme/states/CombatMenuState.java
package com.phantasia.jme.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.phantasia.core.model.Action;
import com.phantasia.core.model.Job;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.model.Stat;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.ListBox;
import com.simsilica.lemur.core.VersionedList;

import java.util.List;

/**
 * Drives the player's turn: one Lemur panel per living party member,
 * collecting an Action choice before the round fires.
 *
 * FLOW:
 *   1. CombatState calls openMenu(party, enemies) at the start of each turn.
 *   2. CombatMenuState shows a panel for party.get(0) (first living member).
 *   3. Player clicks an action button.  The choice is written directly onto
 *      the PlayerCharacter via setAction() / setPrimaryTarget().
 *   4. The panel advances to the next living member.
 *   5. Once all members are assigned, CombatMenuState calls
 *      combatState.executeRound() and hides itself.
 *
 * MONSTER ACTIONS:
 *   Monsters are still assigned by CombatAI.assignMonsterActions() inside
 *   CombatState.executeRound() — this state only handles the party.
 *
 * ACTION AVAILABILITY:
 *   ATTACK  — always available
 *   SLASH   — FIGHTER only (multi-hit)
 *   THRUST  — THIEF only (accuracy bonus)
 *   CAST    — WIZARD / PRIEST only, and only if MP >= 3
 *   PARRY   — always available (defensive stance)
 *   RUN     — always available (escape attempt)
 *
 * TARGET SELECTION:
 *   For simplicity, Attack/Slash/Thrust/Cast auto-target the first living
 *   enemy (same as the original AI). A target picker can be layered on top
 *   later without touching this state's structure.
 */
public class CombatMenuState extends BaseAppState {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private CombatState            combatState;
    private List<PlayerCharacter>  party;
    private List<Monster>          enemies;

    private int                    currentIndex = 0;  // which party member we're assigning

    private Container              menuPanel;
    private Container              logPanel;
    private VersionedList<String>  logData;
    private ListBox<String>        logBox;

    private static final int MAX_LOG = 20;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public CombatMenuState() {}

    // -------------------------------------------------------------------------
    // AppState lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void initialize(Application app) {
        GuiGlobals.initialize(app);
        buildLogPanel(app);
    }

    @Override
    protected void onEnable() {
        var gui = ((SimpleApplication) getApplication()).getGuiNode();
        gui.attachChild(logPanel);
    }

    @Override
    protected void onDisable() {
        dismissMenu();
        logPanel.removeFromParent();
    }

    @Override
    protected void cleanup(Application app) {
        dismissMenu();
        logPanel.removeFromParent();
    }

    // -------------------------------------------------------------------------
    // Public API — called by CombatState
    // -------------------------------------------------------------------------

    /**
     * Opens the action menu for the first living party member.
     * Call this at the start of every player turn.
     */
    public void openMenu(List<PlayerCharacter> party, List<Monster> enemies) {
        this.party   = party;
        this.enemies = enemies;
        this.currentIndex = 0;

        combatState = getState(CombatState.class);
        advanceToNextLivingMember();
    }

    // -------------------------------------------------------------------------
    // Panel construction
    // -------------------------------------------------------------------------

    private void showMenuForMember(PlayerCharacter pc) {
        dismissMenu();

        Application app = getApplication();
        var cam = app.getCamera();

        menuPanel = new Container();

        // Header: character name + HP/MP
        Job job = Job.fromValue(pc.getJob());
        Label header = menuPanel.addChild(
                new Label(pc.getName() + "  (" + job + ")"));
        header.setFontSize(20);

        menuPanel.addChild(new Label(
                "HP: " + pc.getHp() + "/" + pc.getStat(Stat.MAX_HP)
                        + "   MP: " + pc.getStat(Stat.MAGIC_POWER)
                        + "/" + pc.getStat(Stat.MAX_MAGIC)));
        menuPanel.addChild(new Label("Choose action:"));
        menuPanel.addChild(new Label(" "));

        // --- Action buttons ---
        addAttackButton(pc);
        addJobSpecificButton(pc);
        if (canCast(pc)) addCastButton(pc);
        addParryButton(pc);
        addRunButton(pc);

        // Position: center-bottom
        menuPanel.setLocalTranslation(
                cam.getWidth()  / 2f - 120,
                200,
                20);

        ((SimpleApplication) app).getGuiNode().attachChild(menuPanel);
    }

    // -------------------------------------------------------------------------
    // Button builders
    // -------------------------------------------------------------------------

    private void addAttackButton(PlayerCharacter pc) {
        Button btn = menuPanel.addChild(new Button("ATTACK"));
        btn.addClickCommands(cmd -> {
            pc.setAction(Action.ATTACK);
            pc.setPrimaryTarget(firstLivingEnemy());
            log(pc.getName() + ": Attack");
            advance();
        });
    }

    private void addJobSpecificButton(PlayerCharacter pc) {
        Job job = Job.fromValue(pc.getJob());
        switch (job) {
            case FIGHTER -> {
                Button btn = menuPanel.addChild(new Button("SLASH  (multi-hit)"));
                btn.addClickCommands(cmd -> {
                    pc.setAction(Action.SLASH);
                    pc.setPrimaryTarget(firstLivingEnemy());
                    log(pc.getName() + ": Slash");
                    advance();
                });
            }
            case THIEF -> {
                Button btn = menuPanel.addChild(new Button("THRUST  (accuracy+)"));
                btn.addClickCommands(cmd -> {
                    pc.setAction(Action.THRUST);
                    pc.setPrimaryTarget(firstLivingEnemy());
                    log(pc.getName() + ": Thrust");
                    advance();
                });
            }
            case RANGER -> {
                Button btn = menuPanel.addChild(new Button("AIM  (high damage)"));
                btn.addClickCommands(cmd -> {
                    pc.setAction(Action.AIM_BLOW);
                    pc.setPrimaryTarget(firstLivingEnemy());
                    log(pc.getName() + ": Aimed Blow");
                    advance();
                });
            }
            default -> {} // MONK, WIZARD, PRIEST — no extra melee option
        }
    }

    private void addCastButton(PlayerCharacter pc) {
        Job job = Job.fromValue(pc.getJob());
        int defaultSpellId = (job == Job.PRIEST) ? 2 : 1;
        String label = (job == Job.PRIEST) ? "CAST  (Heal)" : "CAST  (Spell)";

        Button btn = menuPanel.addChild(new Button(label));
        btn.addClickCommands(cmd -> {
            pc.setAction(Action.CAST);
            pc.setSelectedSpellId(defaultSpellId);
            // Priests target most-wounded ally; offensive casters target an enemy
            if (job == Job.PRIEST) {
                pc.setPrimaryTarget(mostWoundedAlly());
            } else {
                pc.setPrimaryTarget(firstLivingEnemy());
            }
            log(pc.getName() + ": Cast");
            advance();
        });
    }

    private void addParryButton(PlayerCharacter pc) {
        Button btn = menuPanel.addChild(new Button("PARRY  (defend)"));
        btn.addClickCommands(cmd -> {
            pc.setAction(Action.PARRY);
            pc.setPrimaryTarget(null);
            log(pc.getName() + ": Parry");
            advance();
        });
    }

    private void addRunButton(PlayerCharacter pc) {
        Button btn = menuPanel.addChild(new Button("RUN"));
        btn.addClickCommands(cmd -> {
            pc.setAction(Action.RUN);
            pc.setPrimaryTarget(null);
            log(pc.getName() + ": Run");
            advance();
        });
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private void advance() {
        currentIndex++;
        advanceToNextLivingMember();
    }

    private void advanceToNextLivingMember() {
        // Skip dead members
        while (currentIndex < party.size() && !party.get(currentIndex).isAlive()) {
            currentIndex++;
        }

        if (currentIndex >= party.size()) {
            // All members assigned — fire the round
            dismissMenu();
            if (combatState != null) combatState.executeRound();
        } else {
            showMenuForMember(party.get(currentIndex));
        }
    }

    private void dismissMenu() {
        if (menuPanel != null) {
            menuPanel.removeFromParent();
            menuPanel = null;
        }
    }

    // -------------------------------------------------------------------------
    // Battle log panel
    // -------------------------------------------------------------------------

    private void buildLogPanel(Application app) {
        logData  = new VersionedList<>();
        logPanel = new Container();
        logBox   = logPanel.addChild(new ListBox<>(logData));
        logBox.setVisibleItems(4);

        var cam = app.getCamera();
        logPanel.setLocalTranslation(
                cam.getWidth() / 2f - 200,
                160,
                10);
    }

    public void log(String message) {
        logData.add(message);
        if (logData.size() > MAX_LOG) logData.remove(0);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean canCast(PlayerCharacter pc) {
        Job job = Job.fromValue(pc.getJob());
        return (job == Job.WIZARD || job == Job.PRIEST)
                && pc.getStat(Stat.MAGIC_POWER) >= 3;
    }

    private Monster firstLivingEnemy() {
        if (enemies == null) return null;
        for (Monster m : enemies) {
            if (m.isAlive()) return m;
        }
        return null;
    }

    private PlayerCharacter mostWoundedAlly() {
        PlayerCharacter worst = null;
        int lowestPct = 101;
        for (PlayerCharacter pc : party) {
            if (!pc.isAlive()) continue;
            int maxHp = pc.getStat(Stat.MAX_HP);
            if (maxHp <= 0) continue;
            int pct = (pc.getHp() * 100) / maxHp;
            if (pct < lowestPct) {
                lowestPct = pct;
                worst     = pc;
            }
        }
        return worst;
    }
}