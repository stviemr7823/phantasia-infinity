package com.phantasia.jme.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.logic.GameEvent;
import com.phantasia.core.logic.GameEventBus;
import com.phantasia.core.model.PlayerCharacter;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * THE FACE: Manages the Lemur-based Heads-Up Display.
 * Updated to Revision 4.0: Reactive event listening and direct party refresh hooks.
 */
public class HUDState extends BaseAppState {

    private static final Logger logger = Logger.getLogger(HUDState.class.getName());

    private final GameSession session;
    private Consumer<GameEvent> busListener;

    public HUDState(GameSession session) {
        this.session = session;
    }

    /**
     * THE RESOLVED SYMBOL: Called by ItemState, CombatState, or WorldState
     * to manually trigger a re-draw of the party's status bars.
     */
    public void setupPartyDisplay(List<PlayerCharacter> party) {
        // Implementation for Lemur UI to update HP/MP bars
        // and apply spectral indicators for Undead members.
        refreshStatsDisplay();
    }

    @Override
    protected void initialize(Application app) {
        // Store the consumer reference for clean unregistration later
        this.busListener = this::onGameEvent;
    }

    @Override
    protected void onEnable() {
        // Register to the core nervous system
        GameEventBus.get().register(busListener);

        // Initial inhale of the party's current state
        setupPartyDisplay(session.getParty());
    }

    /**
     * Reacts to world changes reported by the Core (e.g., Judgment results).
     */
    private void onGameEvent(GameEvent event) {
        if (event instanceof GameEvent.PartyJudged) {
            logger.info("HUD acknowledging Judgment; refreshing party display.");
            setupPartyDisplay(session.getParty());
        }
    }

    public void refreshStatsDisplay() {
        // Core Lemur UI refresh logic goes here
    }

    @Override
    protected void onDisable() {
        // Clean unregistration to prevent memory leaks in the 'Face' layer
        GameEventBus.get().unregister(busListener);
    }

    @Override
    protected void cleanup(Application app) {}
}