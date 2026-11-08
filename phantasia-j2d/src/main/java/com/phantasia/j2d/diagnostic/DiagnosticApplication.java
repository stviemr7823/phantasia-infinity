package com.phantasia.j2d.diagnostic;

import com.phantasia.core.data.*;
import com.phantasia.core.logic.LocalMapTraversal;
import com.phantasia.core.model.*;
import com.phantasia.core.world.WorldPosition;
import com.phantasia.j2d.engine.*;
import com.phantasia.j2d.screen.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/** Headless-testable wiring. Reset replaces the entire isolated test session. */
public final class DiagnosticApplication {
    private final DiagnosticContent.Bundle content;
    private final Runnable quit;
    private final int entryMap;
    private final boolean fixture;
    private ScreenManager manager;
    private GameSession session;
    private LocalMapTraversal traversal;
    private LocalMapTraversal nativeTraversal;
    private NativeProgressionScreen nativeScreen;
    private LocalMapScreen nativeMapScreen;
    private java.nio.file.Path nativeSavePath;
    private String saveMessage = "";
    public void setNativeSavePath(java.nio.file.Path path) { nativeSavePath = path; }
    public com.phantasia.core.logic.NativeCampaignV5 nativeCampaign() { return nativeScreen.campaign(); }
    public LocalMapTraversal nativeTraversal() { return nativeTraversal; }
    public DiagnosticApplication(DiagnosticContent.Bundle content, Runnable quit) { this(content, DiagnosticContent.SETTLEMENT, true, quit); }
    public DiagnosticApplication(DiagnosticContent.Bundle content, int entryMap, boolean fixture, Runnable quit) {
        this.content = content; this.entryMap = entryMap; this.fixture = fixture; this.quit = quit; reset();
    }
    public ScreenManager manager() { return manager; }
    public GameSession session() { return session; }
    public LocalMapTraversal traversal() { return traversal; }
    public void reset() {
        var legacy = new GameSession(List.of(PlayerCharacter.fromLegacyCreation("Test adventurer", Job.WIZARD, 100, 50, 20, 20, 20, 20, 1)), PartyLedger.newGame(), new WorldPosition(0, 0), 0);
        install(legacy, NativeProgressionScreen.freshCampaign(), null, null); saveMessage = "";
    }
    private LocalMapScreen nativeMap(ScreenManager target, com.phantasia.core.logic.NativeCampaignV5 campaign) {
        return new LocalMapScreen(target, campaign, content.npcs(), content.shops(),
                () -> List.of(EncounterFactory.buildMonster("Native foe", 12, 1, 40)));
    }
    private void install(GameSession legacy, com.phantasia.core.logic.NativeCampaignV5 campaign,
                         LocalMapTraversal restoredVisit, com.phantasia.core.logic.NativeLocalEncounterFlow.Snapshot pace) {
        var next = new ScreenManager(); var partyScreen = new NativeProgressionScreen(next, campaign);
        var mapScreen = nativeMap(next, campaign);
        next.register(GameState.NATIVE_DIAGNOSTIC, partyScreen); next.register(GameState.NATIVE_LOCAL_MAP, mapScreen);
        next.register(GameState.NATIVE_SERVICE, new NativeServiceOverlay(next));
        next.register(GameState.LOCAL_MAP, new LocalMapScreen(next, legacy, content.npcs(), content.shops(),
                () -> List.of(EncounterFactory.buildMonster("Test creature", 12, 1, 25))));
        var spells = new SpellFactory();
        next.register(GameState.ENCOUNTER_SPLASH, new EncounterSplashScreen(next));
        next.register(GameState.COMBAT_PLANNING, new CombatPlanningScreen(next, spells));
        next.register(GameState.COMBAT_EXECUTION, new CombatExecutionScreen(next));
        next.register(GameState.VICTORY, new VictoryScreen(next)); next.register(GameState.DEFEAT, new DefeatScreen(next, quit));
        next.register(GameState.DIALOGUE, new DialogueScreen(next)); next.register(GameState.SHOP, new ShopOverlay(next));
        next.register(GameState.INN, new InnOverlay(next)); next.register(GameState.GUILD, new GuildOverlay(next)); next.register(GameState.BANK, new BankOverlay(next));
        next.register(GameState.WORLD_ROAM, new Menu(false)); next.register(GameState.PAUSE, new Menu(true));
        if (restoredVisit == null) next.switchTo(GameState.WORLD_ROAM, null);
        else {
            var flow = com.phantasia.core.logic.NativeLocalEncounterFlow.restore(restoredVisit, campaign,
                    () -> List.of(EncounterFactory.buildMonster("Native foe", 12, 1, 40)), ignored -> 10, pace, content.maps());
            next.switchTo(GameState.NATIVE_LOCAL_MAP, new LocalMapScreen.Visit(restoredVisit, GameState.WORLD_ROAM, null, flow));
        }
        // Every import, content check and candidate screen entry succeeded before this swap.
        session = legacy; manager = next; nativeScreen = partyScreen; nativeMapScreen = mapScreen;
        traversal = null; nativeTraversal = restoredVisit;
    }
    private void requireCheckpointScreen() throws java.io.IOException {
        var state = manager.getActiveState();
        if (manager.isFading() || nativeCampaign().activeBattle() != null || !(state == GameState.WORLD_ROAM
                || state == GameState.NATIVE_LOCAL_MAP || state == GameState.PAUSE && manager.underlyingState() == GameState.NATIVE_LOCAL_MAP))
            throw new java.io.IOException("Save/load requires the entry menu or an outside-combat native area.");
    }
    private String contentId() throws java.io.IOException {
        content.validate(); return AuthoredContentFingerprint.compute(content.maps(), content.npcs(), content.shops());
    }
    public void saveNative(java.nio.file.Path path) throws java.io.IOException {
        requireCheckpointScreen(); String id = contentId();
        var visit = manager.getActiveState() == GameState.WORLD_ROAM ? null : nativeMapScreen.visit();
        try {
            var checkpoint = new NativeSaveManager.Checkpoint(id, nativeCampaign().snapshot(),
                    visit == null ? null : visit.traversal().snapshot(), visit == null ? null : visit.nativeEncounters().snapshot());
            NativeSaveManager.save(path, checkpoint, id, content.maps(), content.shops(), p -> p.equals(new WorldPosition(0, 0)));
        } catch (IllegalArgumentException | IllegalStateException ex) { throw new java.io.IOException("Cannot save native checkpoint: " + ex.getMessage(), ex); }
    }
    public void loadNative(java.nio.file.Path path) throws java.io.IOException {
        requireCheckpointScreen();
        var loaded = NativeSaveManager.load(path, contentId(), content.maps(), content.shops(), p -> p.equals(new WorldPosition(0, 0)));
        install(session, loaded.campaign(), loaded.traversal(), loaded.encounters());
        nativeMapScreen.notice("Native checkpoint loaded.");
    }
    public void enter(int mapId) {
        traversal = new LocalMapTraversal(content.maps(), session, mapId, null, p -> p.equals(new WorldPosition(0, 0)));
        manager.switchTo(GameState.LOCAL_MAP, new LocalMapScreen.Visit(traversal, GameState.WORLD_ROAM));
    }
    public void enterNative(int mapId) {
        if (nativeCampaign().activeBattle() != null) throw new IllegalStateException("Native battle is still active");
        nativeTraversal = new LocalMapTraversal(content.maps(), nativeCampaign(), mapId, null, p -> p.equals(new WorldPosition(0, 0)));
        nativeMapScreen = nativeMap(manager, nativeCampaign());
        manager.register(GameState.NATIVE_LOCAL_MAP, nativeMapScreen);
        manager.switchTo(GameState.NATIVE_LOCAL_MAP, new LocalMapScreen.Visit(nativeTraversal, GameState.WORLD_ROAM));
    }
    private final class Menu implements Screen {
        private final boolean pause;
        Menu(boolean pause) { this.pause = pause; }
        @Override public void onEnter(Object data) {} @Override public void onExit() {} @Override public void update(float dt) {}
        @Override public void onKeyPressed(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_ENTER -> { if (pause) manager.pop(); else enter(entryMap); }
                case KeyEvent.VK_ESCAPE -> { if (pause) manager.pop(); }
                case KeyEvent.VK_R -> reset();
                case KeyEvent.VK_V -> { if (!pause) manager.switchTo(GameState.NATIVE_DIAGNOSTIC, null); }
                case KeyEvent.VK_C -> {
                    if (!pause && nativeCampaign().activeBattle() == null) {
                        install(session, DiagnosticSpells.freshCampaign(), null, null);
                        saveMessage = "Fresh non-canon caster party. N explores; V tests standalone battles.";
                    }
                }
                case KeyEvent.VK_N -> { if (!pause) enterNative(entryMap); }
                case KeyEvent.VK_F5, KeyEvent.VK_F9 -> {
                    if (nativeSavePath == null) { saveMessage = "Enable checkpoints with --native-save PATH."; break; }
                    try {
                        if (e.getKeyCode() == KeyEvent.VK_F5) { saveNative(nativeSavePath); saveMessage = "Native checkpoint saved."; }
                        else { loadNative(nativeSavePath); saveMessage = "Native checkpoint loaded."; }
                    } catch (java.io.IOException ex) { saveMessage = ex.getMessage(); }
                }
                case KeyEvent.VK_Q -> quit.run();
            }
        }
        @Override public void onKeyReleased(KeyEvent e) {} @Override public void onMouseClicked(MouseEvent e) {}
        @Override public void render(Graphics2D g) {
            int w = GameCanvas.DESIGN_WIDTH, h = GameCanvas.DESIGN_HEIGHT;
            g.setColor(pause ? new Color(15, 20, 25, 225) : new Color(20, 26, 31)); g.fillRect(0, 0, w, h);
            g.setColor(new Color(240, 210, 125)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
            g.drawString(pause ? "Diagnostic paused" : "Phantasia Infinity / diagnostic mockup", 150, 185);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28)); g.setColor(Color.WHITE);
            String[] lines = pause ? new String[]{"Enter / Esc: resume", "R: reset test party and flags", "Q: quit"}
                    : new String[]{"Enter: enter the test settlement", "East: two connected dungeon rooms", "North: castle courtyard and hall", "Speak to Test guide to open the castle gate", "Arrows / WASD: move and bump into NPCs", "Esc: pause   R: fresh test session   Q: quit", "Non-canon test content. Normal game saves are not used.", "Treasure, traps and battles are active. N uses native progression; Enter compares legacy."};
            if (!pause && !fixture) lines = new String[]{"Enter: enter authored map #" + entryMap,
                    "Arrows / WASD: move and bump into NPCs", "Esc: pause   R: fresh test session   Q: quit",
                    "Fresh diagnostic party. Normal game saves are not used."};
            for (int i = 0; i < lines.length; i++) g.drawString(lines[i], 155, 285 + i * 65);
            if (!pause) {
                g.drawString("N: authored areas with the native party", 155, 285 + lines.length * 65);
                g.drawString("V: native party preparation / battle   C: fresh caster test party", 155, 330 + lines.length * 65);
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 23));
            g.drawString(nativeSavePath == null ? "Checkpoints disabled (optional --native-save PATH)." : "F5: native checkpoint   F9: load (entry menu or native map pause)", 155, h - 80);
            g.drawString(saveMessage, 155, h - 42);
        }
    }
}
