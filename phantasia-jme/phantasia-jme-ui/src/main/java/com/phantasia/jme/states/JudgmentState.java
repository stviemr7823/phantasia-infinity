package com.phantasia.jme.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.logic.JudgmentEngine;
// CORRECTED IMPORTS:
import com.phantasia.core.logic.JudgmentEngine.CharacterJudgment;
import com.phantasia.core.logic.JudgmentEngine.JudgmentResult;
import com.phantasia.core.model.PlayerCharacter;

import java.util.logging.Logger;

/**
 * THE FACE: Visual representation of the Judgment process.
 * Maps Core mathematical "fates" to 3D visual effects.
 */
public class JudgmentState extends BaseAppState {

    private static final Logger logger = Logger.getLogger(JudgmentState.class.getName());

    private final GameSession session;
    private final Node rootNode = new Node("JudgmentScene");
    private AssetManager assetManager;

    public JudgmentState(GameSession session) {
        this.session = session;
    }

    @Override
    protected void initialize(Application app) {
        this.assetManager = app.getAssetManager();
        // Setup the ethereal scene node here
    }

    @Override
    protected void onEnable() {
        // INHALE: Trigger the Core math and capture the updated Result record
        JudgmentResult results = JudgmentEngine.judge(session.getParty());

        logger.info("The Gods have spoken. Processing fates...");

        // REFLECT: Apply visual changes based on the results
        for (CharacterJudgment j : results.individualFates()) {
            applyVisualOddities(j);
        }
    }

    private void applyVisualOddities(CharacterJudgment j) {
        PlayerCharacter pc = j.character();

        switch (j.fate()) {
            case RESURRECTED -> {
                logger.info(pc.getName() + " restored to life.");
                // Reset standard materials or play a holy effect
            }
            case UNDEAD -> {
                logger.info(pc.getName() + " returns as a spectral remnant.");
                applySpectralShader(pc);
            }
            case DESTROYED -> {
                logger.info(pc.getName() + " is lost to the void.");
                dissolveCharacterModel(pc);
            }
        }
    }

    private void applySpectralShader(PlayerCharacter pc) {
        // JME Material Logic to swap textures to the "Ghost" look
    }

    private void dissolveCharacterModel(PlayerCharacter pc) {
        // JME Node manipulation to hide/remove the model
    }

    @Override
    protected void onDisable() {
        rootNode.removeFromParent();
    }

    @Override protected void cleanup(Application app) {}
}