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
    public DiagnosticApplication(DiagnosticContent.Bundle content, Runnable quit) { this(content, DiagnosticContent.SETTLEMENT, true, quit); }
    public DiagnosticApplication(DiagnosticContent.Bundle content, int entryMap, boolean fixture, Runnable quit) {
        this.content = content; this.entryMap = entryMap; this.fixture = fixture; this.quit = quit; reset();
    }
    public ScreenManager manager() { return manager; }
    public GameSession session() { return session; }
    public LocalMapTraversal traversal() { return traversal; }
    public void reset() {
        session = new GameSession(List.of(PlayerCharacter.fromLegacyCreation("Test adventurer", Job.WIZARD, 100, 50, 20, 20, 20, 20, 1)), PartyLedger.newGame(), new WorldPosition(0, 0), 0);
        traversal = null; manager = new ScreenManager();
        manager.register(GameState.LOCAL_MAP, new LocalMapScreen(manager, session, content.npcs(), content.shops(),
                () -> List.of(EncounterFactory.buildMonster("Test creature", 12, 1, 25))));
        var spells = new SpellFactory();
        manager.register(GameState.ENCOUNTER_SPLASH, new EncounterSplashScreen(manager));
        manager.register(GameState.COMBAT_PLANNING, new CombatPlanningScreen(manager, spells));
        manager.register(GameState.COMBAT_EXECUTION, new CombatExecutionScreen(manager));
        manager.register(GameState.VICTORY, new VictoryScreen(manager));
        manager.register(GameState.DEFEAT, new DefeatScreen(manager, quit));
        manager.register(GameState.DIALOGUE, new DialogueScreen(manager)); manager.register(GameState.SHOP, new ShopOverlay(manager));
        manager.register(GameState.INN, new InnOverlay(manager)); manager.register(GameState.GUILD, new GuildOverlay(manager)); manager.register(GameState.BANK, new BankOverlay(manager));
        manager.register(GameState.WORLD_ROAM, new Menu(false)); manager.register(GameState.PAUSE, new Menu(true)); manager.switchTo(GameState.WORLD_ROAM, null);
    }
    public void enter(int mapId) {
        traversal = new LocalMapTraversal(content.maps(), session, mapId, null, p -> p.equals(new WorldPosition(0, 0)));
        manager.switchTo(GameState.LOCAL_MAP, new LocalMapScreen.Visit(traversal, GameState.WORLD_ROAM));
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
                    : new String[]{"Enter: enter the test settlement", "East: two connected dungeon rooms", "North: castle courtyard and hall", "Speak to Test guide to open the castle gate", "Arrows / WASD: move and bump into NPCs", "Esc: pause   R: fresh test session   Q: quit", "Non-canon test content. Normal game saves are not used.", "Treasure, traps and test battles are active. Native-v5 campaign progression remains pending."};
            if (!pause && !fixture) lines = new String[]{"Enter: enter authored map #" + entryMap,
                    "Arrows / WASD: move and bump into NPCs", "Esc: pause   R: fresh test session   Q: quit",
                    "Fresh diagnostic party. Normal game saves are not used."};
            for (int i = 0; i < lines.length; i++) g.drawString(lines[i], 155, 285 + i * 65);
        }
    }
}
