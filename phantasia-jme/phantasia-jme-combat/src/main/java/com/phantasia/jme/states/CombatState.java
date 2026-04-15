// phantasia-jme/phantasia-jme-combat/src/main/java/com/phantasia/jme/states/CombatState.java
package com.phantasia.jme.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.SpellFactory;
import com.phantasia.core.logic.*;
import com.phantasia.core.model.Monster;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.jme.CombatDemoInitializer;

import java.io.IOException;
import java.util.List;

/**
 * Owns the combat scene: entity spatials, round execution, and delegation
 * to VisualBridgeAppState for animated event playback.
 *
 * LAUNCH MODES:
 *   STANDALONE_TEST  — combat-demo fallback (no world.dat).
 *   WORLD_ENCOUNTER  — random or scripted encounter from WorldState.
 *
 * INITIALIZATION ORDER FIX:
 *   CombatMenuState is attached during initialize(), but its own initialize()
 *   doesn't run until the AppStateManager processes it — which happens after
 *   onEnable() returns. Calling openPlayerMenu() from within onEnable() (via
 *   startCombat()) therefore crashes because CombatMenuState.app is still null.
 *
 *   Fix: onEnable() sets a pendingStart flag instead of calling startCombat()
 *   directly. The first update() call consumes the flag and calls startCombat()
 *   at which point CombatMenuState is fully initialized.
 */
public class CombatState extends BaseAppState
        implements com.jme3.input.controls.ActionListener {

    public enum Mode { STANDALONE_TEST, WORLD_ENCOUNTER }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final Node                 combatNode = new Node("CombatScene");
    private final CombatSceneRegistry  registry   = new CombatSceneRegistry();

    private final GameSession          session;
    private final Mode                 mode;
    private       CombatManager        combatManager;
    private       VisualBridgeAppState visualBridge;
    private       CombatMenuState      combatMenu;

    private List<PlayerCharacter> party;
    private List<Monster>         enemies;

    /** True when onEnable() has fired but startCombat() has not yet run. */
    private boolean pendingStart   = false;

    /** True when startCombat() has run but openPlayerMenu() still needs to fire. */
    private boolean pendingMenu    = false;

    /** True when combat is over and conclude() needs to be called. */
    private boolean pendingConclude = false;

    /** Frames waited since pendingConclude was set — gives animations time to drain. */
    private int     concludeDelay  = 0;
    private static final int CONCLUDE_DELAY_FRAMES = 90; // ~1.5s at 60fps

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public CombatState(GameSession session) {
        this(session, Mode.STANDALONE_TEST);
    }

    public CombatState(GameSession session, Mode mode) {
        this.session = session;
        this.mode    = mode;
    }

    // -------------------------------------------------------------------------
    // AppState lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void initialize(Application app) {
        visualBridge = getState(VisualBridgeAppState.class);
        if (visualBridge == null) {
            System.err.println("[CombatState] WARNING: VisualBridgeAppState not found.");
        }

        combatMenu = new CombatMenuState();
        getStateManager().attach(combatMenu);

        app.getInputManager().addMapping("Combat_AutoAction",
                new com.jme3.input.controls.KeyTrigger(
                        com.jme3.input.KeyInput.KEY_RETURN));
        app.getInputManager().addListener(this, "Combat_AutoAction");
    }

    @Override
    protected void onEnable() {
        ((SimpleApplication) getApplication()).getRootNode().attachChild(combatNode);
        // Do NOT call startCombat() here — CombatMenuState is attached but not
        // yet initialized. Set the flag and let update() start combat next frame.
        pendingStart = true;
    }

    @Override
    protected void onDisable() {
        combatNode.removeFromParent();
        if (combatMenu != null) combatMenu.setEnabled(false);
    }

    @Override
    protected void cleanup(Application app) {
        app.getInputManager().removeListener(this);
        app.getInputManager().deleteMapping("Combat_AutoAction");
        if (combatMenu != null) {
            getStateManager().detach(combatMenu);
            combatMenu = null;
        }
        if (visualBridge != null) visualBridge.clearSpatials();
        registry.clear();
        combatNode.detachAllChildren();
        combatManager = null;
        pendingStart    = false;
        pendingMenu     = false;
        pendingConclude = false;
        concludeDelay   = 0;
    }

    // -------------------------------------------------------------------------
    // Update — consume pendingStart once CombatMenuState is initialized
    // -------------------------------------------------------------------------

    @Override
    public void update(float tpf) {
        if (pendingStart) {
            pendingStart = false;
            switch (mode) {
                case STANDALONE_TEST -> CombatDemoInitializer.startTestBattle(this, session);
                case WORLD_ENCOUNTER -> CombatDemoInitializer.startRandomBattle(this, session);
            }
        }
        if (pendingMenu) {
            boolean menuInit = combatMenu != null && combatMenu.isInitialized();
            System.out.println("[CombatState] pendingMenu drain — combatMenu=" + combatMenu
                    + " initialized=" + menuInit
                    + " party=" + (party != null ? party.size() : "null")
                    + " enemies=" + (enemies != null ? enemies.size() : "null"));
            if (menuInit && party != null && enemies != null) {
                pendingMenu = false;
                combatMenu.setEnabled(true);
                combatMenu.openMenu(party, enemies);
            }
            // If not initialized yet, leave pendingMenu=true and retry next frame
        }
        if (pendingConclude) {
            concludeDelay++;
            // Give VisualBridge time to drain animations before concluding.
            // After CONCLUDE_DELAY_FRAMES, conclude directly as a safety net.
            boolean ready = (visualBridge == null) || concludeDelay >= CONCLUDE_DELAY_FRAMES;
            if (ready) {
                pendingConclude = false;
                concludeDelay   = 0;
                conclude();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Combat setup
    // -------------------------------------------------------------------------

    public void startCombat(List<PlayerCharacter> party,
                            List<Monster>         monsters,
                            SpellFactory          spellFactory,
                            EncounterCondition    condition) {
        this.party   = party;
        this.enemies = monsters;

        try {
            spellFactory.loadSpells();
        } catch (IOException e) {
            System.err.println("[CombatState] Could not load spells.dat — "
                    + "spells will fail gracefully. " + e.getMessage());
        }

        this.combatManager = new CombatManager(party, monsters, spellFactory, condition);
        setupScene(party, monsters);

        GameEventBus.get().fire(new GameEvent.CombatStarted(
                party.size(), monsters.size(), mode == Mode.STANDALONE_TEST));

        openPlayerMenu();
    }

    private void setupScene(List<PlayerCharacter> party, List<Monster> monsters) {
        if (visualBridge != null) visualBridge.clearSpatials();
        registry.clear();
        combatNode.detachAllChildren();

        for (int i = 0; i < party.size(); i++)
            spawnPlaceholder(party.get(i).getName(), new Vector3f(-5, 0, i * 2f), false);
        for (int i = 0; i < monsters.size(); i++)
            spawnPlaceholder(monsters.get(i).getName(), new Vector3f(5, 0, i * 2f), true);

        GameEventBus.get().fire(new GameEvent.PartyDisplaySetup(party));

        // Position camera to view the combat scene.
        float sceneDepth = Math.max(party.size(), monsters.size()) * 2f * 0.5f;
        Camera cam = getApplication().getCamera();
        cam.setLocation(new Vector3f(-14f, 8f, sceneDepth));
        cam.lookAt(new Vector3f(2f, 0f, sceneDepth), Vector3f.UNIT_Y);

        // Lights so placeholder boxes and future models are visible
        com.jme3.light.DirectionalLight sun = new com.jme3.light.DirectionalLight();
        sun.setDirection(new Vector3f(-0.5f, -1f, -0.5f).normalizeLocal());
        sun.setColor(ColorRGBA.White);
        combatNode.addLight(sun);

        com.jme3.light.AmbientLight ambient = new com.jme3.light.AmbientLight();
        ambient.setColor(new ColorRGBA(0.3f, 0.3f, 0.3f, 1f));
        combatNode.addLight(ambient);
    }

    private void spawnPlaceholder(String name, Vector3f pos, boolean isMonster) {
        com.jme3.scene.shape.Box   mesh = new com.jme3.scene.shape.Box(0.4f, 0.9f, 0.4f);
        com.jme3.scene.Geometry    geom = new com.jme3.scene.Geometry(name, mesh);
        com.jme3.material.Material mat  = new com.jme3.material.Material(
                getApplication().getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", isMonster
                ? com.jme3.math.ColorRGBA.Red
                : com.jme3.math.ColorRGBA.Cyan);
        geom.setMaterial(mat);
        geom.setLocalTranslation(pos);
        combatNode.attachChild(geom);
        registry.register(name, geom);
        if (visualBridge != null) visualBridge.registerSpatial(name, geom);
    }

    // -------------------------------------------------------------------------
    // Round execution
    // -------------------------------------------------------------------------

    public void executeRound() {
        if (combatManager == null) return;

        CombatAI.assignMonsterActions(enemies, party);

        RoundResult result = combatManager.runRound();
        if (visualBridge != null) visualBridge.processRoundResults(result);

        if (combatManager.isCombatOver()) {
            // Combat is over — VisualBridge will call conclude() once animations
            // drain, but if it can't find this CombatState via getState() (e.g.
            // due to module class loader differences), call it directly here as
            // a safety net after a short delay via pendingConclude flag.
            pendingConclude = true;
        } else {
            openPlayerMenu();
        }
    }

    // -------------------------------------------------------------------------
    // Conclusion
    // -------------------------------------------------------------------------

    public BattleResult conclude() {
        if (combatManager == null)
            return new BattleResult(CombatOutcome.ONGOING, null);

        if (combatMenu != null) combatMenu.setEnabled(false);

        BattleResult result = combatManager.conclude(party, session.getLedger());
        GameEventBus.get().fire(new GameEvent.CombatConcluded(result));
        return result;
    }

    // -------------------------------------------------------------------------
    // Player menu
    // -------------------------------------------------------------------------

    private void openPlayerMenu() {
        // Always defer via pendingMenu — CombatMenuState may not be initialized
        // yet if this is the first call after CombatState was just attached.
        // update() will drain the flag once the AppStateManager has initialized it.
        pendingMenu = true;
    }

    // -------------------------------------------------------------------------
    // Input — ENTER = auto-assign all PCs and fire immediately
    // -------------------------------------------------------------------------

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!"Combat_AutoAction".equals(name) || !isPressed) return;
        if (isCombatOver()) return;

        CombatAI.assignPartyActions(party, enemies);
        if (combatMenu != null) combatMenu.setEnabled(false);
        executeRound();
    }

    // -------------------------------------------------------------------------
    // State queries
    // -------------------------------------------------------------------------

    public boolean isCombatOver() {
        return combatManager != null && combatManager.isCombatOver();
    }

    public CombatOutcome getOutcome() {
        return combatManager != null ? combatManager.getOutcome() : CombatOutcome.ONGOING;
    }

    public List<PlayerCharacter>               getParty()    { return party;    }
    public List<Monster>                       getEnemies()  { return enemies;  }
    public CombatSceneRegistry                 getRegistry() { return registry; }
    public com.phantasia.core.data.PartyLedger getLedger()   { return session.getLedger(); }
}