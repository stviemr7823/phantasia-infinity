// phantasia-jme/phantasia-jme-world/src/main/java/com/phantasia/jme/states/WorldState.java
package com.phantasia.jme.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;           // FOG: new import
import com.jme3.post.filters.FogFilter;             // FOG: new import
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.logic.Direction;
import com.phantasia.core.logic.GameCommand;
import com.phantasia.core.logic.GameEvent;
import com.phantasia.core.logic.GameEventBus;
import com.phantasia.core.logic.NavigationManager;
import com.phantasia.core.logic.WorldEvent;
import com.phantasia.core.world.FeatureRegistry;
import com.phantasia.core.world.WorldMap;
import com.phantasia.core.world.WorldPosition;
import com.phantasia.jme.world.*;
import com.jme3.light.DirectionalLight;
import com.jme3.light.AmbientLight;
import com.jme3.shadow.DirectionalLightShadowFilter;

public class WorldState extends BaseAppState implements ActionListener, AnalogListener {

// -------------------------------------------------------------------------
// Terrain constants
// -------------------------------------------------------------------------

    private static final float TILE_SIZE  = 8.0f;   // was 2.0
    private static final float MAX_HEIGHT = 24.0f;  // was 6.0  — same ratio

// -------------------------------------------------------------------------
// Fog tuning
// -------------------------------------------------------------------------

    private static final float FOG_DISTANCE = 120f;                          // was 40
    private static final float FOG_DENSITY  = 1.2f;                          // was 1.5
    private static final ColorRGBA FOG_COLOR =
            new ColorRGBA(0.65f, 0.70f, 0.80f, 1.0f);                       // unchanged

// -------------------------------------------------------------------------
// Movement tuning
// -------------------------------------------------------------------------

    private static final float MOVE_SPEED  = 24.0f;  // was 6.0 — same tile-crossing time
    private static final float TURN_SPEED  = 2.5f;   // unchanged — feel-based
    private static final float ARRIVE_DIST = 0.05f;  // unchanged


    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final GameSession  session;
    private final WorldMap     worldMap;

    private NavigationManager  navManager;
    private HeightSampler      heightSampler;
    private WorldCameraRig     cameraRig;

    // FOG: post-processor created once in initialize(), added/removed in enable/disable
    private FilterPostProcessor fpp;          // FOG: new field
    private FogFilter           fogFilter;    // FOG: new field
    private DirectionalLightShadowFilter dlsf;

    private final Node worldRoot   = new Node("WorldRoot");
    private final Node featureNode = new Node("WorldFeatures");
    private final Node playerNode  = new Node("PlayerMarker");

    private final Vector3f visualPosition = new Vector3f();
    private final Vector3f targetWorldPos = new Vector3f();
    private       boolean  isMoving       = false;

    private float facingYaw = 0f;

    private boolean keyForward = false;
    private boolean keyBack    = false;
    private boolean keyLeft    = false;
    private boolean keyRight   = false;

    private DayNightCycle dayNight;

    private DirectionalLight sunLight;
    private DirectionalLight moonLight;
    private AmbientLight     ambientLight;

    // Add alongside the existing fields (near the worldRoot, featureNode, playerNode declarations):

    private FeatureModelRegistry featureRegistry;


    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public WorldState(GameSession session, WorldMap worldMap) {
        this.session  = session;
        this.worldMap = worldMap;
    }

    // -------------------------------------------------------------------------
    // AppState lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void initialize(Application app) {

// BEFORE the try block — declare once:
        FeatureRegistry coreRegistry = null;

        try {
            // Assignment only — no 'FeatureRegistry' type keyword here:
            coreRegistry = FeatureRegistry.load(
                    com.phantasia.core.data.DataPaths.DAT_DIR + "/features.dat");
            System.out.println("[WorldState] FeatureRegistry loaded: "
                    + coreRegistry.size() + " records.");
        } catch (Exception e) {
            System.out.println("[WorldState] features.dat not found — "
                    + "feature names will use fallback.");
        }

// Now coreRegistry is in scope here whether load succeeded or not
        navManager = new NavigationManager();
        syncNavManagerToSession();

        String hmpPath = com.phantasia.core.data.DataPaths.DAT_DIR + "/world.hmp.png";
        HeightmapData heightmap = HeightmapData.load(hmpPath, worldMap);
        heightSampler = new HeightSampler(heightmap, TILE_SIZE, MAX_HEIGHT);

        buildTerrain(app, heightmap);
        buildFeatureMarkers(app);
        buildPlayerMarker(app);
        worldRoot.attachChild(featureNode);
        worldRoot.attachChild(playerNode);

        cameraRig = new WorldCameraRig();
        cameraRig.distance           = 36f;   // was 10
        cameraRig.heightOffset       = 10f;   // was 3
        cameraRig.pitchDegrees       = -22f;  // was -18
        cameraRig.focusLerpSpeed     = 8f;    // unchanged
        cameraRig.rotationSlerpSpeed = 3f;    // unchanged

        WorldPosition startPos = session.getWorldPosition();
        visualPosition.set(tileCenter(startPos));
        targetWorldPos.set(visualPosition);

        // FOG: build the FilterPostProcessor once — reuse across enable/disable cycles
        // --- FilterPostProcessor stack ---
// ORDER IS CRITICAL: shadows must be added before fog.
// Fog is the final pass — it applies to both lit and shadowed pixels,
// so distant shadows fade into haze naturally rather than rendering
// black on top of the atmospheric color.
        fpp = new FilterPostProcessor(app.getAssetManager());

// 1. Shadows — PSSM at 2048px across 3 frustum splits
        dlsf = new DirectionalLightShadowFilter(app.getAssetManager(), 2048, 3);
        dlsf.setLight(sunLight);
        dlsf.setShadowIntensity(0.6f);        // softer than default — terrain reads better
        dlsf.setEnabledStabilization(true);   // prevents shadow edge crawl as camera pans
        dlsf.setEnabled(true);
        fpp.addFilter(dlsf);

// 2. Fog — final pass, applied over shadowed and lit pixels alike
        fogFilter = new FogFilter();
        fogFilter.setFogColor(FOG_COLOR);
        fogFilter.setFogDistance(FOG_DISTANCE);
        fogFilter.setFogDensity(FOG_DENSITY);
        fpp.addFilter(fogFilter);

// Day/night cycle — references sunLight, moonLight, ambientLight, fogFilter
        dayNight = new DayNightCycle(sunLight, moonLight, ambientLight, fogFilter);
        dayNight.setCycleDurationSeconds(60f);

    }

    @Override
    protected void onEnable() {
        ((SimpleApplication) getApplication()).getRootNode().attachChild(worldRoot);
        syncNavManagerToSession();

        WorldPosition pos = session.getWorldPosition();
        visualPosition.set(tileCenter(pos));
        targetWorldPos.set(visualPosition);
        isMoving = false;

        registerInputs(getApplication());
        cameraRig.snapTo(playerWorldPos(), facingYaw, getApplication().getCamera());

        // FOG: attach fog to the main viewport when world exploration becomes active
        getApplication().getViewPort().addProcessor(fpp);       // FOG: new

        // Fire initial PartyMoved so the HUD terrain indicator is populated on startup
        com.phantasia.core.world.Tile startTile =
                worldMap.getTile(pos.x(), pos.y());
        GameEventBus.get().fire(
                new GameEvent.PartyMoved(pos, startTile.getType()));
    }

    @Override
    protected void onDisable() {
        worldRoot.removeFromParent();
        getApplication().getInputManager().removeListener(this);
        keyForward = false;
        keyBack    = false;
        keyLeft    = false;
        keyRight   = false;

        // FOG: remove fog before combat/town scenes render — they have their own lighting
        getApplication().getViewPort().removeProcessor(fpp);    // FOG: new
    }

    // -------------------------------------------------------------------------
    // cleanup() — add the four new mouse mappings to deletion
    // -------------------------------------------------------------------------

    @Override
    protected void cleanup(Application app) {
        app.getInputManager().removeListener(this);
        app.getInputManager().deleteMapping("World_Forward");
        app.getInputManager().deleteMapping("World_Back");
        app.getInputManager().deleteMapping("World_TurnLeft");
        app.getInputManager().deleteMapping("World_TurnRight");
        app.getInputManager().deleteMapping("World_MouseLeft");
        app.getInputManager().deleteMapping("World_MouseRight");
        app.getInputManager().deleteMapping("World_MouseUp");
        app.getInputManager().deleteMapping("World_MouseDown");
        app.getInputManager().setCursorVisible(true);  // restore on exit
    }

    // -------------------------------------------------------------------------
    // Update — unchanged from Rev 3.0
    // -------------------------------------------------------------------------

    @Override
    public void update(float tpf) {
        dayNight.update(tpf);
        dlsf.setEnabled(dayNight.isDaytime());
        handleTurning(tpf);
        handleMovement(tpf);
        updatePlayerTransform();
        cameraRig.update(tpf, playerWorldPos(), facingYaw, getApplication().getCamera());
    }

    // -------------------------------------------------------------------------
    // Turning
    // -------------------------------------------------------------------------

    private void handleTurning(float tpf) {
        if (keyLeft)  facingYaw += TURN_SPEED * tpf;
        if (keyRight) facingYaw -= TURN_SPEED * tpf;
        while (facingYaw >  FastMath.PI) facingYaw -= FastMath.TWO_PI;
        while (facingYaw < -FastMath.PI) facingYaw += FastMath.TWO_PI;
    }

    // -------------------------------------------------------------------------
    // Movement
    // -------------------------------------------------------------------------

    private void handleMovement(float tpf) {
        if (isMoving) {
            // Glide toward target tile centre
            Vector3f delta = targetWorldPos.subtract(visualPosition);
            float dist = delta.length();
            if (dist <= ARRIVE_DIST) {
                visualPosition.set(targetWorldPos);
                isMoving = false;
            } else {
                float step = Math.min(MOVE_SPEED * tpf, dist);
                visualPosition.addLocal(delta.normalizeLocal().multLocal(step));
            }
            return;
        }

        if (keyForward || keyBack) {
            // JME spatial default forward = +Z. fromAngleAxis(yaw, Y) rotates from +Z.
            // So at facingYaw=0, Jaime faces +Z. Forward = (sin(yaw), 0, cos(yaw)).
            float fwdX = FastMath.sin(facingYaw);
            float fwdZ = FastMath.cos(facingYaw);
            Direction dir = snapToCardinal(fwdX, fwdZ, keyBack);
            attemptMove(dir);
        }
    }

    /**
     * Snaps the continuous facing vector to the nearest cardinal Direction.
     * Assumes +Z = south, -Z = north (matching JME's z=-row*TILE_SIZE convention).
     * Jaime's default forward is +Z, so at facingYaw=0 he faces south/+Z.
     */
    private Direction snapToCardinal(float fwdX, float fwdZ, boolean backward) {
        if (backward) { fwdX = -fwdX; fwdZ = -fwdZ; }
        if (Math.abs(fwdX) >= Math.abs(fwdZ)) {
            return fwdX > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return fwdZ > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    /**
     * Asks NavigationManager if moving in dir is legal. If yes, updates grid
     * position, sets targetWorldPos, and fires bus events.
     */
    private void attemptMove(Direction dir) {
        NavigationManager.MoveResult result =
                navManager.attemptMove(dir.toLegacyString(), session.getPartyLead());
        if (!result.moved) return;

        session.setPosition(result.newPosition);
        syncNavManagerToSession();
        targetWorldPos.set(tileCenter(result.newPosition));
        isMoving = true;

        GameEventBus.get().fire(
                new GameEvent.PartyMoved(result.newPosition, result.tile.getType()));
        result.worldEvent().ifPresent(this::dispatchWorldEvent);
    }

    // -------------------------------------------------------------------------
    // Player transform
    // -------------------------------------------------------------------------

    private void updatePlayerTransform() {
        float y = heightSampler.getHeightAt(visualPosition.x, visualPosition.z);
        playerNode.setLocalTranslation(visualPosition.x, y, visualPosition.z);

        Quaternion rot = new Quaternion();
        rot.fromAngleAxis(facingYaw, Vector3f.UNIT_Y);
        playerNode.setLocalRotation(rot);
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // registerInputs() — replace existing method
    // -------------------------------------------------------------------------

    private void registerInputs(Application app) {
        var im = app.getInputManager();

        im.addMapping("World_Forward",
                new KeyTrigger(KeyInput.KEY_W), new KeyTrigger(KeyInput.KEY_UP));
        im.addMapping("World_Back",
                new KeyTrigger(KeyInput.KEY_S), new KeyTrigger(KeyInput.KEY_DOWN));
        im.addMapping("World_TurnLeft",
                new KeyTrigger(KeyInput.KEY_A), new KeyTrigger(KeyInput.KEY_LEFT));
        im.addMapping("World_TurnRight",
                new KeyTrigger(KeyInput.KEY_D), new KeyTrigger(KeyInput.KEY_RIGHT));

        // Mouse look axes — false = positive axis (right/down), true = negative axis
        im.addMapping("World_MouseLeft",  new MouseAxisTrigger(MouseInput.AXIS_X, true));
        im.addMapping("World_MouseRight", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        im.addMapping("World_MouseUp",    new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        im.addMapping("World_MouseDown",  new MouseAxisTrigger(MouseInput.AXIS_Y, true));

        im.addListener(this,
                "World_Forward", "World_Back", "World_TurnLeft", "World_TurnRight");
        im.addListener(this,
                "World_MouseLeft", "World_MouseRight", "World_MouseUp", "World_MouseDown");

        im.setCursorVisible(false);
    }

    // -------------------------------------------------------------------------
    // onAction() — add deactivateMouseLook on any movement key press
    // -------------------------------------------------------------------------

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "World_Forward"   -> { keyForward = isPressed; if (isPressed) cameraRig.deactivateMouseLook(); }
            case "World_Back"      -> { keyBack    = isPressed; if (isPressed) cameraRig.deactivateMouseLook(); }
            case "World_TurnLeft"  -> { keyLeft    = isPressed; if (isPressed) cameraRig.deactivateMouseLook(); }
            case "World_TurnRight" -> { keyRight   = isPressed; if (isPressed) cameraRig.deactivateMouseLook(); }
        }
    }

    // -------------------------------------------------------------------------
    // onAnalog() — new method for mouse look
    // -------------------------------------------------------------------------

    @Override
    public void onAnalog(String name, float value, float tpf) {
        // Only activate mouse look when truly stationary
        boolean stationary = !isMoving && !keyForward && !keyBack
                && !keyLeft && !keyRight;
        if (!stationary) return;

        cameraRig.activateMouseLook();

        switch (name) {
            case "World_MouseLeft"  -> cameraRig.applyMouseDelta( value, 0);
            case "World_MouseRight" -> cameraRig.applyMouseDelta(-value, 0);
            case "World_MouseUp"    -> cameraRig.applyMouseDelta(0,  value);
            case "World_MouseDown"  -> cameraRig.applyMouseDelta(0, -value);
        }
    }

    // -------------------------------------------------------------------------
    // World event dispatch
    // -------------------------------------------------------------------------

    private void dispatchWorldEvent(WorldEvent event) {
        switch (event) {
            case WorldEvent.RandomEncounter ignored -> {
                System.out.println("[WorldState] Random encounter at "
                        + session.getWorldPosition() + "!");
                GameEventBus.get().fire(new GameEvent.EncounterTriggered());
            }
            case WorldEvent.ScriptedBattle s -> {
                System.out.println("[WorldState] Scripted encounter: "
                        + s.count() + "x " + s.monsterName() + "!");
                GameEventBus.get().fire(new GameEvent.EncounterTriggered());
            }
            case WorldEvent.EnterTown t -> {
                System.out.println("[WorldState] Entering: " + t.name());
                GameEventBus.get().fire(new GameEvent.TownEntered(t.id(), t.name()));
            }
            case WorldEvent.EnterDungeon d ->
                    System.out.println("[WorldState] Dungeon '" + d.name() + "' — not yet implemented.");
            case WorldEvent.TileEventPrompt p ->
                    System.out.println("[WorldState] Tile event: " + p.tileEvent().description);
            case WorldEvent.NpcInteraction npc -> {
                System.out.println("[WorldState] NPC: " + npc.npcName());
                GameEventBus.get().fire(new GameEvent.CombatLogEntry(
                        "You encounter " + npc.npcName() + "."));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scene construction
    // -------------------------------------------------------------------------

    private void buildTerrain(Application app, HeightmapData heightmap) {
        com.jme3.scene.Mesh terrainMesh =
                TerrainMeshBuilder.build(heightmap, worldMap, TILE_SIZE, MAX_HEIGHT);
        Geometry terrainGeom = new Geometry("Terrain", terrainMesh);

        Material mat = new Material(app.getAssetManager(),
                "Common/MatDefs/Light/Lighting.j3md");
        try {
            com.jme3.texture.Texture grass = app.getAssetManager()
                    .loadTexture("Textures/Terrain/splat/grass.jpg");
            grass.setWrap(com.jme3.texture.Texture.WrapMode.Repeat);
            mat.setTexture("DiffuseMap", grass);
        } catch (Exception e) {
            System.out.println("[WorldState] grass.jpg not found — vertex color only.");
        }
        mat.setBoolean("UseVertexColor", true);

        sunLight = new DirectionalLight();
        sunLight.setDirection(new Vector3f(-0.5f, -1f, -0.5f).normalizeLocal());
        sunLight.setColor(new ColorRGBA(1f, 0.95f, 0.85f, 1f));
        worldRoot.addLight(sunLight);

        moonLight = new DirectionalLight();
        moonLight.setColor(new ColorRGBA(0.02f, 0.03f, 0.06f, 1f)); // starts dim
        moonLight.setDirection(new Vector3f(0.5f, 1f, 0.5f).normalizeLocal());
        worldRoot.addLight(moonLight);

        ambientLight = new AmbientLight();
        ambientLight.setColor(new ColorRGBA(0.4f, 0.4f, 0.5f, 1f));
        worldRoot.addLight(ambientLight);

        terrainGeom.setMaterial(mat);
        worldRoot.attachChild(terrainGeom);

        // Ocean border — hides the black void at map edges
        float mapW = worldMap.getWidth()  * TILE_SIZE;
        float mapH = worldMap.getHeight() * TILE_SIZE;
        float ext  = 200f;
        Quad borderMesh = new Quad(mapW + ext * 2, mapH + ext * 2);
        Geometry borderGeom = new Geometry("OceanBorder", borderMesh);
        Material borderMat = new Material(app.getAssetManager(),
                "Common/MatDefs/Misc/Unshaded.j3md");
        borderMat.setColor("Color", new ColorRGBA(0.10f, 0.25f, 0.55f, 1f));
        borderGeom.setMaterial(borderMat);
        borderGeom.rotate(-FastMath.HALF_PI, 0, 0);
        borderGeom.setLocalTranslation(-ext, -0.05f, ext);
        worldRoot.attachChild(borderGeom);
    }

    private void buildFeatureMarkers(Application app) {
        for (int x = 0; x < worldMap.getWidth(); x++) {
            for (int y = 0; y < worldMap.getHeight(); y++) {
                com.phantasia.core.world.Tile tile = worldMap.getTile(x, y);
                if (!tile.hasFeature()) continue;

                float markerSize = TILE_SIZE * 0.35f;
                float wx = x * TILE_SIZE + TILE_SIZE * 0.5f;
                float wz = -(y * TILE_SIZE + TILE_SIZE * 0.5f);
                float wy = heightSampler.getHeightAt(wx, wz) + markerSize;

                Box mesh = new Box(markerSize, markerSize, markerSize);
                Geometry geom = new Geometry("Feature_" + x + "_" + y, mesh);
                ColorRGBA color = tile.getFeature().isTown()
                        ? new ColorRGBA(1.0f, 0.90f, 0.20f, 1f)
                        : new ColorRGBA(0.70f, 0.10f, 0.80f, 1f);
                Material mat = new Material(app.getAssetManager(),
                        "Common/MatDefs/Misc/Unshaded.j3md");
                mat.setColor("Color", color);
                geom.setMaterial(mat);
                geom.setLocalTranslation(wx, wy, wz);
                featureNode.attachChild(geom);
            }
        }
    }

    private void buildPlayerMarker(Application app) {
        try {
            Spatial jaime = app.getAssetManager().loadModel("Models/Jaime/Jaime.j3o");
            jaime.scale(1.8f);   // was 0.5 — Jaime ~1.8wu tall looks right on an 8wu tile
            playerNode.attachChild(jaime);
            System.out.println("[WorldState] Jaime model loaded.");
        } catch (Exception e) {
            System.out.println("[WorldState] Jaime not found — box fallback.");
            Box mesh = new Box(TILE_SIZE * 0.18f, TILE_SIZE * 0.35f, TILE_SIZE * 0.18f);
            Geometry geom = new Geometry("PlayerMarkerGeom", mesh);
            Material mat = new Material(app.getAssetManager(),
                    "Common/MatDefs/Misc/Unshaded.j3md");
            mat.setColor("Color", ColorRGBA.White);
            geom.setMaterial(mat);
            playerNode.attachChild(geom);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Vector3f tileCenter(WorldPosition pos) {
        float wx = pos.x() * TILE_SIZE + TILE_SIZE * 0.5f;
        float wz = -(pos.y() * TILE_SIZE + TILE_SIZE * 0.5f);
        float wy = heightSampler.getHeightAt(wx, wz);
        return new Vector3f(wx, wy, wz);
    }

    private Vector3f playerWorldPos() {
        float y = heightSampler.getHeightAt(visualPosition.x, visualPosition.z);
        return new Vector3f(visualPosition.x, y, visualPosition.z);
    }

    private void syncNavManagerToSession() {
        WorldPosition p = session.getWorldPosition();
        // Logic: Check if the new position 'p' is different from the current session position
        if (!p.equals(session.getPosition())) {
            session.setPosition(p);
        }
    }
}