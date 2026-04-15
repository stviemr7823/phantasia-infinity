package com.phantasia.libgdx.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import com.phantasia.core.data.EncounterFactory;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.SpellFactory;
import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;
import com.phantasia.libgdx.PhantasiaGame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fully playable turn-based combat screen for the LibGDX frontend.
 *
 * Mirrors the functionality of {@code phantasia-j2d/tour/CombatPanel}:
 *   - Party and enemy HP/MP roster bars
 *   - Per-character action menu (Attack, job-specific, Cast, Parry, Run)
 *   - AUTO ALL shortcut (ENTER key) — assigns all via CombatAI and fires
 *   - Scrolling combat log fed by CombatNarrator
 *   - Round execution via CombatManager
 *   - Victory: loot summary + XP via LootManager / ExperienceTable
 *   - Defeat / Escaped end states
 *   - "Return to Map" button on combat end
 *
 * ENTRY POINTS (replaces the old stub constructor):
 *   {@link #forRandom(PhantasiaGame, Screen, GameSession)}  — random encounter
 *   {@link #forScripted(PhantasiaGame, Screen, GameSession, String, int)} — scripted
 *
 * LAYOUT (all rendered via SpriteBatch + ShapeRenderer, no Scene2D):
 *
 *   ┌──────────────────────┬────────────────────┐
 *   │  PARTY ROSTER        │  ENEMY ROSTER      │
 *   ├──────────────────────┴────────────────────┤
 *   │  COMBAT LOG  (scrolling narrator text)    │
 *   ├───────────────────────────────────────────┤
 *   │  ACTION MENU  (buttons for current PC)    │
 *   └───────────────────────────────────────────┘
 */
public class CombatScreen implements Screen {

    // -------------------------------------------------------------------------
    // Layout constants
    // -------------------------------------------------------------------------

    private static final float ROSTER_H   = 180f;
    private static final float MENU_H     = 110f;
    private static final float LINE_H     = 18f;
    private static final float PAD        = 10f;
    private static final int   MAX_LOG    = 120;

    // -------------------------------------------------------------------------
    // Colors  (matches j2d TourFrame palette)
    // -------------------------------------------------------------------------

    private static final Color C_BG        = new Color(0.059f, 0.051f, 0.078f, 1f);
    private static final Color C_PANEL     = new Color(0.110f, 0.094f, 0.141f, 1f);
    private static final Color C_BORDER    = new Color(0.255f, 0.216f, 0.314f, 1f);
    private static final Color C_ACCENT    = new Color(0.745f, 0.569f, 0.216f, 1f);
    private static final Color C_TEXT      = new Color(0.843f, 0.804f, 0.725f, 1f);
    private static final Color C_DIM       = new Color(0.471f, 0.439f, 0.373f, 1f);
    private static final Color C_BLUE      = new Color(0.333f, 0.569f, 0.863f, 1f);
    private static final Color C_RED       = new Color(0.863f, 0.294f, 0.275f, 1f);
    private static final Color C_GREEN     = new Color(0.275f, 0.784f, 0.333f, 1f);
    private static final Color C_HP_GREEN  = new Color(0.216f, 0.745f, 0.275f, 1f);
    private static final Color C_HP_RED    = new Color(0.784f, 0.275f, 0.196f, 1f);
    private static final Color C_MP_BLUE   = new Color(0.275f, 0.431f, 0.824f, 1f);
    private static final Color C_BAR_BG    = new Color(0.118f, 0.110f, 0.086f, 1f);
    private static final Color C_BTN_BG    = new Color(0.125f, 0.110f, 0.165f, 1f);
    private static final Color C_BTN_HOV   = new Color(0.196f, 0.176f, 0.255f, 1f);

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final PhantasiaGame     game;
    private final Screen            returnScreen;
    private final GameSession       session;
    private final boolean           scripted;
    private final String            scriptedMonsterName;
    private final int               scriptedCount;

    private CombatManager           manager;
    private List<PlayerCharacter>   party;
    private List<Monster>           enemies;
    private final CombatNarrator    narrator = new CombatNarrator();

    // Combat state
    private int                     assignIdx   = 0;
    private boolean                 combatOver  = false;
    private CombatOutcome           outcome;

    // Log
    private final List<String>      logLines    = new ArrayList<>();
    private int                     logScroll   = 0;  // lines scrolled from bottom
    private static final int        LOG_VISIBLE = 7;

    // Menu buttons — rebuilt each turn
    private final List<MenuButton>  menuButtons = new ArrayList<>();
    private int                     hoveredBtn  = -1;

    // Rendering
    private final ShapeRenderer     sr;
    private final Matrix4           proj = new Matrix4();

    // -------------------------------------------------------------------------
    // Static factories (replace old stub constructor)
    // -------------------------------------------------------------------------

    /** Creates a CombatScreen for a random encounter. */
    public static CombatScreen forRandom(PhantasiaGame game, Screen returnScreen,
                                         GameSession session) {
        return new CombatScreen(game, returnScreen, session, false, null, 0);
    }

    /** Creates a CombatScreen for a scripted encounter. */
    public static CombatScreen forScripted(PhantasiaGame game, Screen returnScreen,
                                           GameSession session,
                                           String monsterName, int count) {
        return new CombatScreen(game, returnScreen, session, true, monsterName, count);
    }

    // -------------------------------------------------------------------------
    // Construction (private — use factories above)
    // -------------------------------------------------------------------------

    private CombatScreen(PhantasiaGame game, Screen returnScreen,
                         GameSession session,
                         boolean scripted, String monsterName, int count) {
        this.game                = game;
        this.returnScreen        = returnScreen;
        this.session             = session;
        this.scripted            = scripted;
        this.scriptedMonsterName = monsterName;
        this.scriptedCount       = count;
        this.sr                  = new ShapeRenderer();
    }

    // -------------------------------------------------------------------------
    // Screen lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void show() {
        rebuildProj(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        initCombat();
        registerInput();
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(C_BG.r, C_BG.g, C_BG.b, 1f);

        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();

        float rosterY  = h - ROSTER_H;
        float logY     = MENU_H;
        float logH     = rosterY - MENU_H;

        // --- Background panels ---
        sr.setProjectionMatrix(proj);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        // Roster background (split vertically)
        drawPanel(sr, 0,      rosterY, w / 2f, ROSTER_H, C_PANEL);
        drawPanel(sr, w / 2f, rosterY, w / 2f, ROSTER_H, C_PANEL);
        // Log background
        drawPanel(sr, 0, logY, w, logH, new Color(0.039f, 0.031f, 0.047f, 1f));
        // Menu background
        drawPanel(sr, 0, 0, w, MENU_H, C_PANEL);

        sr.end();

        // --- Border lines ---
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(C_BORDER);
        sr.line(w / 2f, rosterY, w / 2f, h);           // roster split
        sr.line(0, rosterY, w, rosterY);                // roster / log split
        sr.line(0, logY, w, logY);                      // log / menu split
        sr.end();

        // --- Text content ---
        game.getBatch().setProjectionMatrix(proj);
        game.getBatch().begin();

        drawPartyRoster(w, h, rosterY);
        drawEnemyRoster(w, h, rosterY);
        drawLog(w, logY, logH);
        drawMenu(w, h);

        game.getBatch().end();

        // --- Button highlights (shape pass over buttons) ---
        sr.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i < menuButtons.size(); i++) {
            MenuButton b = menuButtons.get(i);
            sr.setColor(i == hoveredBtn ? b.color : C_BORDER);
            sr.rect(b.x, b.y, b.w, b.h);
        }
        sr.end();
    }

    @Override
    public void resize(int width, int height) {
        rebuildProj(width, height);
        rebuildMenu();
    }

    @Override public void hide()    { Gdx.input.setInputProcessor(null); }
    @Override public void pause()   {}
    @Override public void resume()  {}

    @Override
    public void dispose() {
        sr.dispose();
    }

    // -------------------------------------------------------------------------
    // Combat initialisation
    // -------------------------------------------------------------------------

    private void initCombat() {
        party = new ArrayList<>(session.getParty());

        if (scripted) {
            enemies = new ArrayList<>();
            for (int i = 0; i < scriptedCount; i++) {
                String label = scriptedCount > 1
                        ? scriptedMonsterName + " " + (char)('A' + i)
                        : scriptedMonsterName;
                enemies.add(EncounterFactory.buildMonster(
                        label, 20 + (int)(Math.random() * 20), scriptedCount, 50));
            }
        } else {
            enemies = EncounterFactory.generateEncounter();
        }

        SpellFactory sf = new SpellFactory();
        try { sf.loadSpells(); }
        catch (IOException e) {
            appendLog("spells.dat not found — spells will fail gracefully.");
        }

        EncounterCondition cond = scripted
                ? EncounterCondition.NORMAL
                : FormulaEngine.rollEncounterCondition();

        manager   = new CombatManager(party, enemies, sf, cond);
        assignIdx = 0;

        appendLog("══════════════════════════════════");
        appendLog("  ENCOUNTER — " + cond.announcement());
        appendLog(rosterSummary("Party", party));
        appendLog(rosterSummary("Foes",  enemies));
        appendLog("══════════════════════════════════");

        rebuildMenu();
    }

    // -------------------------------------------------------------------------
    // Action assignment
    // -------------------------------------------------------------------------

    private void assignAction(PlayerCharacter pc, Action action, boolean heal) {
        pc.setAction(action);
        if (action == Action.CAST) {
            pc.setSelectedSpellId(heal ? 2 : 1);
            pc.setPrimaryTarget(heal ? mostWounded() : firstLivingEnemy());
        } else if (action == Action.PARRY || action == Action.RUN) {
            pc.setPrimaryTarget(null);
        } else {
            pc.setPrimaryTarget(firstLivingEnemy());
        }
        appendLog("  " + pc.getName() + " → " + action.name());
        assignIdx++;
        rebuildMenu();
    }

    private void autoAll() {
        CombatAI.assignPartyActions(party, enemies);
        appendLog("  [AUTO] AI assigned all actions.");
        executeRound();
    }

    // -------------------------------------------------------------------------
    // Round execution
    // -------------------------------------------------------------------------

    private void executeRound() {
        if (manager == null || combatOver) return;

        CombatAI.assignMonsterActions(enemies, party);
        RoundResult result = manager.runRound();

        appendLog("");
        appendLog("── Round " + result.roundNumber() + " ──────────────────────");
        for (String line : narrator.narrateRound(result)) {
            if (line != null) appendLog("  " + line);
        }

        if (result.isCombatOver()) {
            endCombat(result.outcome());
        } else {
            assignIdx = 0;
            rebuildMenu();
        }
    }

    // -------------------------------------------------------------------------
    // End of combat
    // -------------------------------------------------------------------------

    private void endCombat(CombatOutcome o) {
        combatOver = true;
        outcome    = o;

        appendLog("");
        appendLog("══════════════════════════════════");
        appendLog("  " + o.name());

        if (o == CombatOutcome.VICTORY) {
            LootManager.LootResult loot =
                    new LootManager(party, session.getLedger())
                            .distributeFrom(enemies);
            appendLog(loot.describe(session.getLedger()));

            int xpPool = enemies.stream().mapToInt(Entity::getExperience).sum();
            appendLog(ExperienceTable.awardCombatXp(party, xpPool));
        }

        appendLog("══════════════════════════════════");
        rebuildMenu();
    }

    // -------------------------------------------------------------------------
    // Drawing — rosters
    // -------------------------------------------------------------------------

    private void drawPartyRoster(int w, int h, float rosterY) {
        float x = PAD;
        float y = h - PAD - LINE_H;

        game.getFont().setColor(C_BLUE);
        draw("PARTY", x, y);
        y -= LINE_H + 2;

        if (party == null) return;
        for (PlayerCharacter pc : party) {
            boolean alive = pc.isAlive();
            game.getFont().setColor(alive ? C_TEXT : C_DIM);

            Job job = Job.fromValue(pc.getJob());
            draw(String.format("%-10s %-7s", pc.getName(), job), x, y);
            y -= LINE_H - 2;

            if (alive) {
                float barX = x + 4;
                drawBar(barX, y, 100, 8,
                        pc.getStat(Stat.HP), pc.getStat(Stat.MAX_HP),
                        C_HP_GREEN, C_HP_RED, "HP");
                drawBar(barX + 115, y, 80, 8,
                        pc.getStat(Stat.MAGIC_POWER), pc.getStat(Stat.MAX_MAGIC),
                        C_MP_BLUE, C_DIM, "MP");
            } else {
                game.getFont().setColor(C_DIM);
                draw("  DEAD", x, y);
            }
            y -= LINE_H;
            if (y < rosterY + PAD) break;
        }
    }

    private void drawEnemyRoster(int w, int h, float rosterY) {
        float x = w / 2f + PAD;
        float y = h - PAD - LINE_H;

        game.getFont().setColor(C_RED);
        draw("ENEMIES", x, y);
        y -= LINE_H + 2;

        if (enemies == null) return;
        for (Monster m : enemies) {
            boolean alive = m.isAlive();
            game.getFont().setColor(alive ? C_TEXT : C_DIM);
            draw(String.format("%-18s", m.getName()), x, y);
            y -= LINE_H - 2;

            if (alive) {
                drawBar(x + 4, y, 110, 8,
                        m.getHp(), m.getHp() + 1, C_HP_GREEN, C_HP_RED, "HP");
            } else {
                game.getFont().setColor(C_DIM);
                draw("  SLAIN", x, y);
            }
            y -= LINE_H;
            if (y < rosterY + PAD) break;
        }
    }

    // -------------------------------------------------------------------------
    // Drawing — combat log
    // -------------------------------------------------------------------------

    private void drawLog(int w, float logY, float logH) {
        float x = PAD + 2;
        float y = logY + logH - PAD - LINE_H;

        int total   = logLines.size();
        int visible = Math.min(LOG_VISIBLE, total);
        int start   = Math.max(0, total - visible - logScroll);
        int end     = Math.min(total, start + visible);

        for (int i = start; i < end; i++) {
            String line = logLines.get(i);
            Color  c    = lineColor(line);
            game.getFont().setColor(c);
            draw(line, x, y);
            y -= LINE_H;
        }
    }

    // -------------------------------------------------------------------------
    // Drawing — action menu
    // -------------------------------------------------------------------------

    private void drawMenu(int w, int h) {
        if (menuButtons.isEmpty()) return;

        // Menu prompt (character name + stats)
        game.getFont().setColor(C_ACCENT);
        String prompt = menuPrompt();
        draw(prompt, PAD, MENU_H - PAD - 2);

        // Button labels
        for (int i = 0; i < menuButtons.size(); i++) {
            MenuButton b = menuButtons.get(i);
            Color labelColor = (i == hoveredBtn) ? Color.WHITE : b.color;
            game.getFont().setColor(labelColor);
            // Centre label in button
            float labelX = b.x + b.w / 2f - b.label.length() * 3.5f;
            float labelY = b.y + b.h / 2f + LINE_H / 2f - 2;
            draw(b.label, labelX, labelY);
        }

        // Scroll hint
        if (logLines.size() > LOG_VISIBLE) {
            game.getFont().setColor(C_DIM);
            draw("▲▼ scroll log", w - 90f, MENU_H - PAD - 2);
        }
    }

    // -------------------------------------------------------------------------
    // Menu construction — rebuilt on every state change
    // -------------------------------------------------------------------------

    private void rebuildMenu() {
        menuButtons.clear();

        int w = Gdx.graphics.getWidth();
        float btnH = 32f;
        float btnY = PAD + 4;
        float btnX = PAD;
        float gap  = 6f;

        if (combatOver) {
            // Single "Return to Map" button
            addButton("RETURN TO MAP", C_ACCENT, btnX, btnY,
                    w - PAD * 2, btnH, () -> returnToMap());
            return;
        }

        PlayerCharacter current = currentPC();
        if (current == null) {
            // All assigned — show Execute Round
            addButton("▶  EXECUTE ROUND  [ENTER]", C_GREEN,
                    btnX, btnY, w - PAD * 2, btnH, this::executeRound);
            return;
        }

        // Per-character action buttons
        float bw = 120f;
        addButton("ATTACK", C_ACCENT, btnX, btnY, bw, btnH,
                () -> assignAction(current, Action.ATTACK, false));
        btnX += bw + gap;

        Job job = Job.fromValue(current.getJob());
        switch (job) {
            case FIGHTER -> {
                addButton("SLASH ×3", C_ACCENT, btnX, btnY, bw, btnH,
                        () -> assignAction(current, Action.SLASH, false));
                btnX += bw + gap;
            }
            case THIEF -> {
                addButton("THRUST", C_ACCENT, btnX, btnY, bw, btnH,
                        () -> assignAction(current, Action.THRUST, false));
                btnX += bw + gap;
            }
            case RANGER -> {
                addButton("AIM BLOW", C_ACCENT, btnX, btnY, bw, btnH,
                        () -> assignAction(current, Action.AIM_BLOW, false));
                btnX += bw + gap;
            }
            default -> {}
        }

        boolean canCast = (job == Job.WIZARD || job == Job.PRIEST)
                && current.getStat(Stat.MAGIC_POWER) >= 3;
        if (canCast) {
            boolean heal = (job == Job.PRIEST);
            addButton(heal ? "CAST (Heal)" : "CAST (Spell)", C_BLUE,
                    btnX, btnY, bw, btnH,
                    () -> assignAction(current, Action.CAST, heal));
            btnX += bw + gap;
        }

        addButton("PARRY", C_DIM, btnX, btnY, bw * 0.8f, btnH,
                () -> assignAction(current, Action.PARRY, false));
        btnX += bw * 0.8f + gap;

        addButton("RUN", C_DIM, btnX, btnY, bw * 0.6f, btnH,
                () -> assignAction(current, Action.RUN, false));
        btnX += bw * 0.6f + gap;

        // AUTO ALL always last
        addButton("AUTO ALL ↵", C_DIM,
                w - PAD - 110f, btnY, 110f, btnH, this::autoAll);
    }

    private void addButton(String label, Color color,
                           float x, float y, float w, float h,
                           Runnable action) {
        menuButtons.add(new MenuButton(label, color, x, y, w, h, action));
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    private void registerInput() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    if (!combatOver) {
                        if (currentPC() == null) executeRound();
                        else autoAll();
                    } else {
                        returnToMap();
                    }
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    returnToMap();
                    return true;
                }
                // Log scroll
                if (keycode == Input.Keys.UP) {
                    logScroll = Math.min(logScroll + 1,
                            Math.max(0, logLines.size() - LOG_VISIBLE));
                    return true;
                }
                if (keycode == Input.Keys.DOWN) {
                    logScroll = Math.max(0, logScroll - 1);
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                // LibGDX y=0 is top; our proj has y=0 at bottom
                float gy = Gdx.graphics.getHeight() - screenY;
                for (int i = 0; i < menuButtons.size(); i++) {
                    MenuButton b = menuButtons.get(i);
                    if (screenX >= b.x && screenX <= b.x + b.w
                            && gy >= b.y && gy <= b.y + b.h) {
                        b.action.run();
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                float gy = Gdx.graphics.getHeight() - screenY;
                hoveredBtn = -1;
                for (int i = 0; i < menuButtons.size(); i++) {
                    MenuButton b = menuButtons.get(i);
                    if (screenX >= b.x && screenX <= b.x + b.w
                            && gy >= b.y && gy <= b.y + b.h) {
                        hoveredBtn = i;
                        break;
                    }
                }
                return false;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                int delta = (int) amountY;
                logScroll = Math.max(0,
                        Math.min(logScroll - delta,
                                Math.max(0, logLines.size() - LOG_VISIBLE)));
                return true;
            }
        });
    }

    // -------------------------------------------------------------------------
    // Helpers — drawing
    // -------------------------------------------------------------------------

    private void draw(String text, float x, float y) {
        game.getFont().draw(game.getBatch(), text, x, y);
    }

    /**
     * Draws a labeled bar inline using the current ShapeRenderer.
     * Caller must have ended the sr batch first; we open a local batch here.
     *
     * Since we're inside getBatch().begin(), we end batch, draw shape, reopen.
     */
    private void drawBar(float x, float y, float w, float h,
                         int cur, int max,
                         Color full, Color low, String label) {
        // End text batch, draw bar with shape renderer, reopen text batch
        game.getBatch().end();

        sr.setProjectionMatrix(proj);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        // Background
        sr.setColor(C_BAR_BG);
        sr.rect(x, y - h, w, h);

        // Fill
        float pct  = max > 0 ? (float) cur / max : 0f;
        Color fill = pct < 0.3f ? low : full;
        sr.setColor(fill);
        sr.rect(x, y - h, w * pct, h);

        sr.end();

        // Border
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(C_BORDER);
        sr.rect(x, y - h, w, h);
        sr.end();

        // Label text
        game.getBatch().setProjectionMatrix(proj);
        game.getBatch().begin();
        game.getFont().setColor(C_DIM);
        draw(label + ":" + cur + "/" + max, x + w + 4, y);
    }

    private void drawPanel(ShapeRenderer sr, float x, float y,
                           float w, float h, Color c) {
        sr.setColor(c);
        sr.rect(x, y, w, h);
    }

    // -------------------------------------------------------------------------
    // Helpers — combat
    // -------------------------------------------------------------------------

    private PlayerCharacter currentPC() {
        while (assignIdx < party.size()) {
            if (party.get(assignIdx).isAlive()) return party.get(assignIdx);
            assignIdx++;
        }
        return null;
    }

    private Monster firstLivingEnemy() {
        if (enemies == null) return null;
        for (Monster m : enemies) if (m.isAlive()) return m;
        return null;
    }

    private PlayerCharacter mostWounded() {
        PlayerCharacter worst = null;
        int low = 101;
        for (PlayerCharacter pc : party) {
            if (!pc.isAlive()) continue;
            int maxHp = pc.getStat(Stat.MAX_HP);
            if (maxHp == 0) continue;
            int pct = pc.getHp() * 100 / maxHp;
            if (pct < low) { low = pct; worst = pc; }
        }
        return worst;
    }

    private String menuPrompt() {
        if (combatOver)
            return "  Combat over: " + (outcome != null ? outcome.name() : "");
        PlayerCharacter pc = currentPC();
        if (pc == null) return "  All actions assigned — ready to execute.";
        Job job = Job.fromValue(pc.getJob());
        return "  " + pc.getName() + " (" + job + ")"
                + "   HP: " + pc.getHp() + "/" + pc.getStat(Stat.MAX_HP)
                + "   MP: " + pc.getStat(Stat.MAGIC_POWER)
                + "/" + pc.getStat(Stat.MAX_MAGIC);
    }

    private void appendLog(String line) {
        logLines.add(line);
        if (logLines.size() > MAX_LOG) logLines.remove(0);
        logScroll = 0; // auto-scroll to bottom
    }

    private Color lineColor(String line) {
        if (line.startsWith("══") || line.startsWith("──")) return C_BORDER;
        if (line.contains("VICTORY") || line.contains("healed"))  return C_GREEN;
        if (line.contains("DEFEAT")  || line.contains("dies"))    return C_RED;
        if (line.contains("ENCOUNTER") || line.contains("Round")) return C_ACCENT;
        if (line.contains("[AUTO]") || line.contains("→"))        return C_BLUE;
        return C_TEXT;
    }

    private String rosterSummary(String label, List<?> list) {
        StringBuilder sb = new StringBuilder("  " + label + ": ");
        list.forEach(o -> {
            if (o instanceof PlayerCharacter pc)
                sb.append(pc.getName()).append("(").append(pc.getHp()).append("hp) ");
            else if (o instanceof Monster m)
                sb.append(m.getName()).append("(").append(m.getHp()).append("hp) ");
        });
        return sb.toString().trim();
    }

    private void returnToMap() {
        game.setScreen(returnScreen);
    }

    private void rebuildProj(int w, int h) {
        proj.setToOrtho2D(0, 0, w, h);
    }

    // -------------------------------------------------------------------------
    // MenuButton record
    // -------------------------------------------------------------------------

    private static class MenuButton {
        final String   label;
        final Color    color;
        final float    x, y, w, h;
        final Runnable action;

        MenuButton(String label, Color color,
                   float x, float y, float w, float h,
                   Runnable action) {
            this.label  = label;
            this.color  = color;
            this.x = x; this.y = y;
            this.w = w; this.h = h;
            this.action = action;
        }
    }
}