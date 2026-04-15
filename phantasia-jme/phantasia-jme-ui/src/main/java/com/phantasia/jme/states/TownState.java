// phantasia-jme/src/main/java/com/phantasia/jme/states/TownState.java
package com.phantasia.jme.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.logic.GameEvent;
import com.phantasia.core.logic.GameEventBus;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.model.Stat;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Label;

import java.util.List;

/**
 * Town screen — shown when the party enters a town tile.
 *
 * Services:
 *   INN   — Restores HP and MP for all living party members. Costs gold.
 *           Gold is deducted via PartyLedger.spendGold(), which fires
 *           GameEvent.PartyGoldChanged so HUDState updates automatically.
 *   SHOP  — Stub (disabled until ItemState is implemented).
 *   GUILD — Stub (disabled until character training is implemented).
 *   LEAVE — Re-enables WorldState and detaches this state.
 *
 * INN PRICING:
 *   10 gp × total missing HP across living party members, minimum 5 gp.
 *   Returns 0 if no one needs healing (inn section shows "already healed").
 *
 * STAT CORRECTNESS:
 *   MP is Stat.MAGIC_POWER (current) and Stat.MAX_MAGIC (maximum).
 *   These map to DataLayout.PC_MAGIC_POWER and PC_MAX_MAGIC — both 16-bit.
 *
 * BUGS FIXED vs initial version:
 *   - MAX_MAGIC_POWER → Stat.MAX_MAGIC  (was an undefined constant)
 *   - ledger.spendPartyGold() → ledger.spendGold()  (correct method name)
 */
public class TownState extends BaseAppState {

    private final GameSession session;
    private final int         townId;
    private final String      townName;

    private Container townPanel;

    public TownState(GameSession session, int townId, String townName) {
        this.session  = session;
        this.townId   = townId;
        this.townName = townName;
    }

    @Override
    protected void initialize(Application app) {
        GuiGlobals.initialize(app);
        townPanel = buildTownPanel(app);
    }

    @Override
    protected void onEnable() {
        ((SimpleApplication) getApplication()).getGuiNode().attachChild(townPanel);
        System.out.println("[TownState] Entered " + townName + " (id=" + townId + ")");
    }

    @Override
    protected void onDisable() { townPanel.removeFromParent(); }

    @Override
    protected void cleanup(Application app) {
        if (townPanel != null) townPanel.removeFromParent();
    }

    // -------------------------------------------------------------------------
    // Panel construction
    // -------------------------------------------------------------------------

    private Container buildTownPanel(Application app) {
        Container panel = new Container();

        Label title = panel.addChild(new Label(townName.toUpperCase()));
        title.setFontSize(28);

        panel.addChild(new Label("What would you like to do?"));
        panel.addChild(new Label(" "));

        panel.addChild(buildInnSection());
        panel.addChild(new Label(" "));

        Button shopBtn  = panel.addChild(new Button("SHOP  (coming soon)"));
        shopBtn.setEnabled(false);

        Button guildBtn = panel.addChild(new Button("GUILD  (coming soon)"));
        guildBtn.setEnabled(false);

        panel.addChild(new Label(" "));

        Button leaveBtn = panel.addChild(new Button("LEAVE TOWN"));
        leaveBtn.addClickCommands(cmd -> leaveTown());

        var cam = app.getCamera();
        panel.setLocalTranslation(
                cam.getWidth()  / 2f - 140,
                cam.getHeight() / 2f + 180,
                20);

        return panel;
    }

    private Container buildInnSection() {
        Container inn = new Container();
        inn.addChild(new Label("INN — Rest and recover all HP/MP"));

        int cost = innCost();

        if (cost == 0) {
            inn.addChild(new Label("  The party is already at full health."));
            return inn;
        }

        int gold = session.getLedger().getPartyGold();
        inn.addChild(new Label("  Cost: " + cost + " gp   (You have: " + gold + " gp)"));

        Button restBtn = inn.addChild(new Button("REST  (" + cost + " gp)"));
        restBtn.setEnabled(gold >= cost);

        if (gold < cost) {
            inn.addChild(new Label("  Not enough gold!"));
        }

        restBtn.addClickCommands(cmd -> {
            // spendGold fires GameEvent.PartyGoldChanged → HUD refreshes automatically
            if (session.getLedger().spendGold(cost)) {
                restoreParty();
                rebuildPanel();
            }
        });

        return inn;
    }

    // -------------------------------------------------------------------------
    // Inn logic
    // -------------------------------------------------------------------------

    private void restoreParty() {
        for (PlayerCharacter pc : session.getParty()) {
            if (!pc.isAlive()) continue;
            pc.setStat(Stat.HP,          pc.getStat(Stat.MAX_HP));
            pc.setStat(Stat.MAGIC_POWER, pc.getStat(Stat.MAX_MAGIC));
        }
        System.out.println("[TownState] Party rested at inn in " + townName);

        HUDState hud = getState(HUDState.class);
        if (hud != null) hud.setupPartyDisplay(List.copyOf(session.getParty()));
    }

    /** 10 gp per missing HP across the party, minimum 5 gp, 0 if no healing needed. */
    private int innCost() {
        int missing = 0;
        for (PlayerCharacter pc : session.getParty()) {
            if (pc.isAlive())
                missing += Math.max(0, pc.getStat(Stat.MAX_HP) - pc.getHp());
        }
        if (missing == 0) return 0;
        return Math.max(5, missing * 10);
    }

    // -------------------------------------------------------------------------
    // Panel refresh after purchase
    // -------------------------------------------------------------------------

    private void rebuildPanel() {
        var guiNode = ((SimpleApplication) getApplication()).getGuiNode();
        townPanel.removeFromParent();
        townPanel = buildTownPanel(getApplication());
        if (isEnabled()) guiNode.attachChild(townPanel);
    }

    // -------------------------------------------------------------------------
    // Leave town
    // -------------------------------------------------------------------------

    private void leaveTown() {
        System.out.println("[TownState] Leaving " + townName);
        getStateManager().detach(this);
        GameEventBus.get().fire(new GameEvent.ReturnToWorldRequested());
    }
}