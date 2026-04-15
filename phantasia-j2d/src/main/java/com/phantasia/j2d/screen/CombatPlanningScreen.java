// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/CombatPlanningScreen.java
package com.phantasia.j2d.screen;

import com.phantasia.core.data.SpellFactory;
import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;

import com.phantasia.j2d.engine.GameCanvas;
import com.phantasia.j2d.engine.GameState;
import com.phantasia.j2d.engine.Screen;
import com.phantasia.j2d.engine.ScreenManager;
import com.phantasia.j2d.render.ParchmentRenderer;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.phantasia.j2d.render.ParchmentRenderer.*;

/**
 * Phase B of the two-screen combat system — the tactical planning screen.
 *
 * Full-screen parchment UI where the player assigns one action to each
 * party member before any action resolves. Layout at 1920×1080:
 *
 *   Header bar (round number, enemy summary)
 *   Left 55%:  Party action list + action menu + ENGAGE button
 *   Right 45%: Party status cards + enemy info panel
 *
 * Input:  EncounterSplashScreen.EncounterData (enemies, condition, context)
 * Output: Actions assigned on each PC → transition to CombatExecutionScreen
 */
public class CombatPlanningScreen implements Screen {

    /**
     * Carries everything CombatExecutionScreen needs to run the round.
     */
    public record PlanningResult(
            List<PlayerCharacter> party,
            List<Monster>         enemies,
            CombatManager         manager,
            CombatNarrator        narrator,
            int                   roundNumber
    ) {}

    // ─────────────────────────────────────────────────────────────────────
    // Layout constants (design resolution 1920×1080)
    // ─────────────────────────────────────────────────────────────────────

    private static final int MARGIN       = 40;
    private static final int HEADER_H     = 60;
    private static final int HEADER_Y     = 20;
    private static final int MAIN_Y       = HEADER_Y + HEADER_H + 20;
    private static final int GAP          = 20;

    // Left panel
    private static final int LEFT_W       = 1020;
    private static final int ACTION_LIST_H = 400;
    private static final int MENU_H       = 290;

    // Right panel
    private static final int RIGHT_X      = MARGIN + LEFT_W + GAP;
    private static final int RIGHT_W      = 1920 - RIGHT_X - MARGIN;

    // Action row sizing
    private static final int ROW_H        = 44;
    private static final int ROW_PAD      = 14;

    // Menu grid
    private static final int MENU_COLS    = 2;

    // ─────────────────────────────────────────────────────────────────────
    // Available actions (displayed in menu)
    // ─────────────────────────────────────────────────────────────────────

    private static final Action[] MENU_ACTIONS = {
            Action.ATTACK, Action.LUNGE, Action.SLASH, Action.THRUST,
            Action.AIM_BLOW, Action.PARRY, Action.CAST, Action.RUN
    };

    private static final String[] MENU_LABELS = {
            "Attack", "Lunge", "Slash", "Thrust",
            "Aim Blow", "Parry", "Cast Spell", "Run"
    };

    // ─────────────────────────────────────────────────────────────────────
    // Dependencies
    // ─────────────────────────────────────────────────────────────────────

    private final ScreenManager screenManager;
    private final SpellFactory  spellFactory;

    // ─────────────────────────────────────────────────────────────────────
    // Per-combat state
    // ─────────────────────────────────────────────────────────────────────

    private List<PlayerCharacter> party;
    private List<Monster>         enemies;
    private EncounterCondition    condition;
    private CombatManager         combatManager;
    private CombatNarrator        narrator;
    private int                   roundNumber;

    // Selection state
    private int selectedPartyIndex;   // which PC is being assigned
    private int selectedMenuIndex;    // which action is highlighted in the menu
    private boolean menuFocused;      // true = keyboard is in action menu

    // ─────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────

    public CombatPlanningScreen(ScreenManager screenManager, SpellFactory spellFactory) {
        this.screenManager = screenManager;
        this.spellFactory  = spellFactory;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Screen lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onEnter(Object transitionData) {
        if (transitionData instanceof EncounterSplashScreen.EncounterData ed) {
            // First round — build the combat manager
            this.party       = (List<PlayerCharacter>) ed.combatContext();
            this.enemies     = ed.enemies();
            this.condition   = ed.condition();
            this.combatManager = new CombatManager(party, enemies, spellFactory, condition);
            this.narrator    = new CombatNarrator();
            this.roundNumber = 1;
        } else if (transitionData instanceof PlanningResult pr) {
            // Subsequent rounds — reuse existing combat
            this.party         = pr.party();
            this.enemies       = pr.enemies();
            this.combatManager = pr.manager();
            this.narrator      = pr.narrator();
            this.roundNumber   = pr.roundNumber();
        }

        // Reset selection
        selectedPartyIndex = 0;
        selectedMenuIndex  = 0;
        menuFocused        = false;

        // Default all living PCs to THRUST
        for (PlayerCharacter pc : party) {
            if (pc.isAlive()) pc.setAction(Action.THRUST);
        }
    }

    @Override
    public void update(float dt) {
        // No animation in planning — purely input-driven
    }

    @Override
    public void render(Graphics2D g) {
        int W = GameCanvas.DESIGN_WIDTH;
        int H = GameCanvas.DESIGN_HEIGHT;

        // Void background
        g.setColor(VOID);
        g.fillRect(0, 0, W, H);

        renderHeader(g, W);
        renderPartyActionList(g);
        renderActionMenu(g);
        renderStatusCards(g);
        renderEnemyPanel(g);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Header bar
    // ─────────────────────────────────────────────────────────────────────

    private void renderHeader(Graphics2D g, int W) {
        drawPanel(g, MARGIN, HEADER_Y, W - MARGIN * 2, HEADER_H);

        drawText(g, "COMBAT", MARGIN + 24, HEADER_Y + 38, TextStyle.HEADER, GOLD);

        String round = "ROUND " + roundNumber;
        drawText(g, round, MARGIN + 200, HEADER_Y + 38, TextStyle.DATA, GOLD_BRIGHT);

        // Enemy summary on the right
        String summary = buildEnemySummary();
        FontMetrics fm = g.getFontMetrics();
        drawText(g, summary, W - MARGIN - 24 - fm.stringWidth(summary),
                HEADER_Y + 38, TextStyle.BODY, DANGER);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Left panel — Party Action List
    // ─────────────────────────────────────────────────────────────────────

    private void renderPartyActionList(Graphics2D g) {
        int px = MARGIN;
        int py = MAIN_Y;

        drawPanel(g, px, py, LEFT_W, ACTION_LIST_H);
        drawText(g, "PARTY ACTIONS", px + 22, py + 28, TextStyle.LABEL, GOLD);
        drawDivider(g, px + 16, py + 38, LEFT_W - 32);

        int rowY = py + 50;
        List<PlayerCharacter> living = party.stream()
                .filter(PlayerCharacter::isAlive).toList();

        for (int i = 0; i < living.size(); i++) {
            PlayerCharacter pc = living.get(i);
            boolean selected = (i == selectedPartyIndex && !menuFocused);

            // Row background
            if (selected) {
                g.setColor(new Color(0xBFA34C, true).brighter());
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f));
                g.fillRoundRect(px + 12, rowY, LEFT_W - 24, ROW_H, 6, 6);
                g.setComposite(AlphaComposite.SrcOver);
            }

            // Selection indicator
            Color dotColor = selected ? GOLD_BRIGHT : BORDER_STR;
            g.setColor(dotColor);
            g.fillOval(px + 24, rowY + ROW_H / 2 - 4, 8, 8);

            // Character name
            drawText(g, pc.getName(), px + 44, rowY + 28, TextStyle.BODY, TEXT);

            // Assigned action
            String actionLabel = actionLabel(pc.getCurrentAction());
            drawText(g, actionLabel, px + 220, rowY + 28, TextStyle.BODY,
                    new Color(0x8A7F6E));

            // Rank badge
            String rank = "R" + pc.getCombatRank().position;
            drawText(g, rank, px + LEFT_W - 80, rowY + 28, TextStyle.DATA, GOLD);

            rowY += ROW_H + 4;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Left panel — Action Menu
    // ─────────────────────────────────────────────────────────────────────

    private void renderActionMenu(Graphics2D g) {
        int px = MARGIN;
        int py = MAIN_Y + ACTION_LIST_H + GAP;

        drawPanel(g, px, py, LEFT_W, MENU_H);
        drawText(g, "SELECT ACTION", px + 22, py + 28, TextStyle.LABEL, GOLD);
        drawDivider(g, px + 16, py + 38, LEFT_W - 32);

        // 2-column grid
        int gridX = px + 16;
        int gridY = py + 50;
        int cellW = (LEFT_W - 48) / MENU_COLS;
        int cellH = 42;
        int cellGap = 6;

        for (int i = 0; i < MENU_ACTIONS.length; i++) {
            int col = i % MENU_COLS;
            int row = i / MENU_COLS;
            int cx = gridX + col * (cellW + cellGap);
            int cy = gridY + row * (cellH + cellGap);

            boolean highlight = (i == selectedMenuIndex && menuFocused);

            // Cell background
            Color cellBg = highlight ? new Color(0x3D3226) : new Color(0x352B1F);
            g.setColor(cellBg);
            g.fillRoundRect(cx, cy, cellW, cellH, 5, 5);

            // Cell border
            g.setColor(highlight ? GOLD : BORDER_SUB);
            g.drawRoundRect(cx, cy, cellW, cellH, 5, 5);

            // Label
            Color textColor = highlight ? GOLD_BRIGHT : TEXT;
            drawText(g, MENU_LABELS[i], cx + 18, cy + 27, TextStyle.BODY, textColor);
        }

        // ENGAGE button + hint
        int engY = py + MENU_H - 52;
        drawDivider(g, px + 16, engY - 10, LEFT_W - 32);

        // Gold button
        g.setColor(GOLD);
        g.fillRoundRect(px + 22, engY, 160, 40, 6, 6);
        drawTextCentered(g, "ENGAGE", px + 22, engY + 27, 160, TextStyle.HEADER, VOID);

        drawText(g, "ESC = cancel   ENTER = confirm", px + 200, engY + 27,
                TextStyle.DATA, new Color(0x8A7F6E));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Right panel — Status Cards (2-column grid)
    // ─────────────────────────────────────────────────────────────────────

    private void renderStatusCards(Graphics2D g) {
        int py = MAIN_Y;
        int cardW = (RIGHT_W - GAP) / 2;
        int cardH = 110;
        int statusH = (int)(ACTION_LIST_H * 1.15);

        drawPanel(g, RIGHT_X, py, RIGHT_W, statusH);
        drawText(g, "PARTY STATUS", RIGHT_X + 22, py + 28, TextStyle.LABEL, GOLD);
        drawDivider(g, RIGHT_X + 16, py + 38, RIGHT_W - 32);

        List<PlayerCharacter> living = party.stream()
                .filter(PlayerCharacter::isAlive).toList();

        int gridY = py + 50;
        for (int i = 0; i < Math.min(living.size(), 6); i++) {
            PlayerCharacter pc = living.get(i);
            int col = i % 2;
            int row = i / 2;
            int cx = RIGHT_X + 12 + col * (cardW + 10);
            int cy = gridY + row * (cardH + 8);

            renderStatusCard(g, pc, cx, cy, cardW, cardH);
        }
    }

    private void renderStatusCard(Graphics2D g, PlayerCharacter pc,
                                  int x, int y, int w, int h) {
        // Card background
        g.setColor(new Color(0x352B1F));
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.setColor(BORDER_SUB);
        g.drawRoundRect(x, y, w, h, 6, 6);

        // Name
        drawText(g, pc.getName(), x + 12, y + 22, TextStyle.LABEL, GOLD);

        // Class + Race
        String classRace = pc.getRaceEnum().name() + " " + Job.fromValue(pc.getJob()).name();
        drawText(g, classRace, x + 12, y + 38, TextStyle.DATA, new Color(0x8A7F6E));

        // HP bar
        int hp    = pc.getHp();
        int maxHp = pc.getStat(Stat.MAX_HP);
        drawText(g, "HP", x + 12, y + 62, TextStyle.DATA, new Color(0x8A7F6E));
        drawHealthBar(g, hp, maxHp, x + 38, y + 52, w - 100, 10);
        drawText(g, hp + "/" + maxHp, x + w - 50, y + 62, TextStyle.DATA, TEXT);

        // MP bar
        int mp    = pc.getStat(Stat.MAGIC_POWER);
        int maxMp = pc.getStat(Stat.MAX_MAGIC);
        drawText(g, "MP", x + 12, y + 82, TextStyle.DATA, new Color(0x8A7F6E));
        drawManaBar(g, mp, maxMp, x + 38, y + 72, w - 100, 10);
        drawText(g, mp + "/" + maxMp, x + w - 50, y + 82, TextStyle.DATA, TEXT);

        // Rank
        String rank = "Rank " + pc.getCombatRank().position + " — "
                + switch (pc.getCombatRank()) {
            case FRONT  -> "Front";
            case MIDDLE -> "Middle";
            case BACK   -> "Rear";
        };
        drawText(g, rank, x + 12, y + 100, TextStyle.DATA, BORDER_STR);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Right panel — Enemy Info
    // ─────────────────────────────────────────────────────────────────────

    private void renderEnemyPanel(Graphics2D g) {
        int py = MAIN_Y + (int)(ACTION_LIST_H * 1.15) + GAP;
        int eh = 1080 - py - 30;

        drawPanel(g, RIGHT_X, py, RIGHT_W, eh);
        drawText(g, "ENEMIES", RIGHT_X + 22, py + 28, TextStyle.LABEL, GOLD);
        drawDivider(g, RIGHT_X + 16, py + 38, RIGHT_W - 32);

        Map<String, Long> counts = enemies.stream()
                .filter(Monster::isAlive)
                .collect(Collectors.groupingBy(Monster::getName, Collectors.counting()));

        int entryY = py + 58;
        for (var entry : counts.entrySet()) {
            drawText(g, entry.getKey(), RIGHT_X + 22, entryY, TextStyle.BODY, DANGER);
            drawText(g, "\u00D7" + entry.getValue(), RIGHT_X + RIGHT_W - 60, entryY,
                    TextStyle.DATA, new Color(0x8A7F6E));
            entryY += 32;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Input handling
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onKeyPressed(KeyEvent e) {
        if (e == null) return;

        List<PlayerCharacter> living = party.stream()
                .filter(PlayerCharacter::isAlive).toList();

        switch (e.getKeyCode()) {

            case KeyEvent.VK_UP -> {
                if (menuFocused) {
                    selectedMenuIndex = Math.max(0, selectedMenuIndex - MENU_COLS);
                } else {
                    selectedPartyIndex = Math.max(0, selectedPartyIndex - 1);
                }
            }

            case KeyEvent.VK_DOWN -> {
                if (menuFocused) {
                    int next = selectedMenuIndex + MENU_COLS;
                    if (next < MENU_ACTIONS.length) selectedMenuIndex = next;
                } else {
                    selectedPartyIndex = Math.min(living.size() - 1, selectedPartyIndex + 1);
                }
            }

            case KeyEvent.VK_LEFT -> {
                if (menuFocused && selectedMenuIndex % MENU_COLS > 0) {
                    selectedMenuIndex--;
                }
            }

            case KeyEvent.VK_RIGHT -> {
                if (menuFocused && selectedMenuIndex % MENU_COLS < MENU_COLS - 1
                        && selectedMenuIndex + 1 < MENU_ACTIONS.length) {
                    selectedMenuIndex++;
                }
            }

            case KeyEvent.VK_TAB -> {
                menuFocused = !menuFocused;
            }

            case KeyEvent.VK_ENTER -> {
                if (menuFocused) {
                    // Assign action to selected PC
                    if (selectedPartyIndex < living.size()) {
                        living.get(selectedPartyIndex).setAction(MENU_ACTIONS[selectedMenuIndex]);
                        menuFocused = false;
                        // Advance to next PC
                        if (selectedPartyIndex < living.size() - 1) {
                            selectedPartyIndex++;
                        }
                    }
                } else {
                    // ENGAGE — run the round
                    engage();
                }
            }

            case KeyEvent.VK_SPACE -> {
                if (!menuFocused) {
                    // Open menu for selected PC
                    menuFocused = true;
                }
            }

            case KeyEvent.VK_E -> {
                // E = Engage shortcut
                engage();
            }
        }
    }

    @Override
    public void onMouseClicked(MouseEvent e) {
        // Future: hit-test action menu cells and party rows
    }

    @Override
    public void onExit() {
        // State preserved — may return from execution screen for next round
    }

    // ─────────────────────────────────────────────────────────────────────
    // Engage — resolve actions and transition
    // ─────────────────────────────────────────────────────────────────────

    private void engage() {
        // Monster AI assigns actions
        CombatAI.assignMonsterActions(enemies, party);

        // Run the round
        RoundResult result = combatManager.runRound();

        // Build execution data
        CombatExecutionScreen.ExecutionData execData = new CombatExecutionScreen.ExecutionData(
                party, enemies, result, narrator, combatManager, roundNumber
        );

        screenManager.fadeTo(GameState.COMBAT_EXECUTION, execData);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private String buildEnemySummary() {
        Map<String, Long> counts = enemies.stream()
                .filter(Monster::isAlive)
                .collect(Collectors.groupingBy(Monster::getName, Collectors.counting()));
        return counts.entrySet().stream()
                .map(e -> e.getValue() + " " + e.getKey() + (e.getValue() > 1 ? "(s)" : ""))
                .collect(Collectors.joining("  \u2022  "));
    }

    private String actionLabel(Action action) {
        return switch (action) {
            case ATTACK   -> "Attack";
            case THRUST   -> "Thrust";
            case SLASH    -> "Slash";
            case LUNGE    -> "Lunge";
            case AIM_BLOW -> "Aim Blow";
            case PARRY    -> "Parry";
            case CAST     -> "Cast Spell";
            case RUN      -> "Run";
            case NONE     -> "—";
        };
    }
}