package com.phantasia.jme.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.logic.GameEvent;
import com.phantasia.core.logic.GameEventBus;
import com.phantasia.core.logic.JudgmentEngine;
import com.phantasia.core.logic.JudgmentEngine.JudgmentResult;

import java.util.logging.Logger;

public class GameManagerState extends BaseAppState {

    private static final Logger logger = Logger.getLogger(GameManagerState.class.getName());
    private final GameSession session;

    public GameManagerState(GameSession session) {
        this.session = session;
    }

    @Override
    protected void initialize(Application app) {
        // Now compiles because GameEventBus accepts Consumer<GameEvent>
        GameEventBus.get().register(this::onGameEvent);
    }

    private void onGameEvent(GameEvent event) {
        if (event instanceof GameEvent.PartyJudged e) {
            handlePartyWipe(e.result());
        }
    }

    private void handlePartyWipe(JudgmentResult result) {
        logger.info("Transitioning to Judgment Screen...");

        // Disable world exploration
        WorldState world = getStateManager().getState(WorldState.class);
        if (world != null) world.setEnabled(false);

        // Attach the visual 'Face' of the judgment
        getStateManager().attach(new JudgmentState(session));
    }

    @Override
    protected void onDisable() {
        GameEventBus.get().unregister(this::onGameEvent);
    }

    @Override
    protected void onEnable() {}
    @Override
    protected void cleanup(Application app) {}
}