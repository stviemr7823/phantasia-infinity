package com.phantasia.libgdx.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.town.GuildService;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.model.Stat;
import com.phantasia.libgdx.PhantasiaGame;

import java.util.ArrayList;
import java.util.List;

/**
 * Fully functional town screen for the LibGDX frontend.
 *
 * Mirrors {@code phantasia-j2d/tour/TownPanel}:
 *   - Inn: shows party HP deficits, calculates cost (10gp/missing HP, min 5gp),
 *     deducts via PartyLedger.spendGold(), restores all HP+MP.
 *   - Guild: eligibility check via GuildService, shows training cost per member.
 *   - Shop stub: note that it arrives in a future build.
 *   - Party status panel: live HP/MP bars for all members.
 *   - "Leave Town" returns to the map screen.
 *
 * LAYOUT:
 *
 *   ┌──────────────────────────────────────────┐
 *   │  HEADER: town name  id  gold             │
 *   ├────────────────────┬─────────────────────┤
 *   │  SERVICES          │  PARTY STATUS       │
 *   │  - Inn             │  (HP/MP bars)       │
 *   │  - Guild           │                     │
 *   │  - Shop (stub)     │                     │
 *   ├────────────────────┴─────────────────────┤
 *   │  [LEAVE TOWN]  [REST]  [GUILD CHECK]     │
 *   └──────────────────────────────────────────┘
 */
public class TownScreen implements Screen {

    // -------------------------------------------------------------------------
    // Layout
    // -------------------------------------------------------------------------

    private static final float HEADER_H = 48f;
    private static final float FOOTER_H = 60f;
    private static final float LINE_H   = 18f;
    private static final float PAD      = 12f;
    private static final float BAR_W    = 110f;
    private static final float BAR_H    = 9f;

    // -------------------------------------------------------------------------
    // Colors
    // -------------------------------------------------------------------------

    private static final Color C_BG       = new Color(0.071f, 0.055f, 0.031f, 1f);
    private static final Color C_PANEL    = new Color(0.110f, 0.086f, 0.055f, 1f);
    private static final Color C_BORDER   = new Color(0.353f, 0.294f, 0.149f, 1f);
    private static final Color C_ACCENT   = new Color(0.831f, 0.725f, 0.290f, 1f);
    private static final Color C_TEXT     = new Color(0.843f, 0.804f, 0.725f, 1f);
    private static final Color C_DIM      = new Color(0.471f, 0.439f, 0.373f, 1f);
    private static final Color C_GREEN    = new Color(0.275f, 0.784f, 0.333f, 1f);
    private static final Color C_RED      = new Color(0.863f, 0.294f, 0.275f, 1f);
    private static final Color C_HP_GREEN = new Color(0.216f, 0.745f, 0.275f, 1f);
    private static final Color C_HP_RED   = new Color(0.784f, 0.275f, 0.196f, 1f);
    private static final Color C_MP_BLUE  = new Color(0.275f, 0.431f, 0.824f, 1f);
    private static final Color C_BAR_BG   = new Color(0.118f, 0.086f, 0.047f, 1f);
    private static final Color C_BTN_BG   = new Color(0.165f, 0.125f, 0.063f, 1f);

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final PhantasiaGame game;
    private final Screen        returnScreen;
    private final GameSession   session;
    private final int           townId;
    private final String        townName;

    // Feedback message shown for a few seconds after an action
    private String  feedbackMsg  = "";
    private float   feedbackTimer = 0f;
    private Color   feedbackColor = C_TEXT;

    // Guild check results
    private final List<String> guildLines = new ArrayList<>();
    private boolean guildChecked = false;

    // Rendering
    private final ShapeRenderer sr;
    private final Matrix4       proj = new Matrix4();
    private final List<TownButton> buttons = new ArrayList<>();
    private int hoveredBtn = -1;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public TownScreen(PhantasiaGame game, Screen returnScreen,
                      int townId, String townName) {
        this.game         = game;
        this.returnScreen = returnScreen;
        this.session      = game.getSession();
        this.townId       = townId;
        this.townName     = townName;
        this.sr           = new ShapeRenderer();
    }

    // -------------------------------------------------------------------------
    // Screen lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void show() {
        rebuildProj(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        rebuildButtons();
        registerInput();
        System.out.println("[TownScreen] Entered " + townName + " (id=" + townId + ")");
    }

    @Override
    public void render(float delta) {
        if (feedbackTimer > 0f) feedbackTimer -= delta;

        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();

        ScreenUtils.clear(C_BG.r, C_BG.g, C_BG.b, 1f);

        float bodyY  = FOOTER_H;
        float bodyH  = h - HEADER_H - FOOTER_H;
        float halfW  = w / 2f;

        // --- Background panels ---
        sr.setProjectionMatrix(proj);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        // Header
        drawRect(sr, 0, h - HEADER_H, w, HEADER_H, C_PANEL);
        // Left service pane
        drawRect(sr, 0, bodyY, halfW, bodyH, C_BG);
        // Right party pane
        drawRect(sr, halfW, bodyY, halfW, bodyH, C_PANEL);
        // Footer
        drawRect(sr, 0, 0, w, FOOTER_H, C_PANEL);

        sr.end();

        // --- Borders ---
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(C_BORDER);
        sr.line(0, h - HEADER_H, w, h - HEADER_H);
        sr.line(0, bodyY, w, bodyY);
        sr.line(halfW, bodyY, halfW, h - HEADER_H);
        sr.end();

        // --- Button backgrounds ---
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < buttons.size(); i++) {
            TownButton b = buttons.get(i);
            sr.setColor(i == hoveredBtn ? C_BTN_BG : new Color(0, 0, 0, 0));
            sr.rect(b.x, b.y, b.w, b.h);
        }
        sr.end();

        sr.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i < buttons.size(); i++) {
            TownButton b = buttons.get(i);
            sr.setColor(i == hoveredBtn ? b.color : C_BORDER);
            sr.rect(b.x, b.y, b.w, b.h);
        }
        sr.end();

        // --- Text ---
        game.getBatch().setProjectionMatrix(proj);
        game.getBatch().begin();

        drawHeader(w, h);
        drawServices(halfW, bodyY, bodyH, w, h);
        drawPartyStatus(halfW, bodyY, bodyH, w, h);
        drawFooter(w, h);

        game.getBatch().end();
    }

    @Override
    public void resize(int width, int height) {
        rebuildProj(width, height);
        rebuildButtons();
    }

    @Override public void hide()    { Gdx.input.setInputProcessor(null); }
    @Override public void pause()   {}
    @Override public void resume()  {}
    @Override public void dispose() { sr.dispose(); }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    private void drawHeader(int w, int h) {
        float y = h - PAD - LINE_H;

        game.getFont().setColor(C_ACCENT);
        draw(townName.toUpperCase(), PAD, y);

        game.getFont().setColor(C_DIM);
        draw("id=" + townId, PAD + 180, y);

        game.getFont().setColor(C_ACCENT);
        draw("Gold: " + session.getLedger().getPartyGold() + " gp",
                w - 160f, y);
    }

    private void drawServices(float halfW, float bodyY, float bodyH,
                              int w, int h) {
        float x  = PAD;
        float y  = bodyY + bodyH - PAD - LINE_H;

        // ── INN ────────────────────────────────────────
        game.getFont().setColor(C_ACCENT);
        draw("INN  —  Rest and recover", x, y);
        y -= LINE_H + 2;

        int cost = innCost();
        if (cost == 0) {
            game.getFont().setColor(C_DIM);
            draw("  Party is at full health.", x, y);
        } else {
            game.getFont().setColor(C_TEXT);
            draw("  Cost: " + cost + " gp  "
                            + "(have: " + session.getLedger().getPartyGold() + " gp)",
                    x, y);
        }
        y -= LINE_H * 2f;

        // ── GUILD ──────────────────────────────────────
        game.getFont().setColor(C_ACCENT);
        draw("GUILD  —  Level up (training)", x, y);
        y -= LINE_H + 2;

        if (guildChecked) {
            game.getFont().setColor(C_TEXT);
            for (String line : guildLines) {
                draw("  " + line, x, y);
                y -= LINE_H;
            }
        } else {
            game.getFont().setColor(C_DIM);
            draw("  Press [G] or click \"Guild Check\".", x, y);
            y -= LINE_H;
        }
        y -= LINE_H;

        // ── SHOP ───────────────────────────────────────
        game.getFont().setColor(C_ACCENT);
        draw("SHOP  —  Buy equipment", x, y);
        y -= LINE_H + 2;
        game.getFont().setColor(C_DIM);
        draw("  Shop coming in a future build.", x, y);

        // ── Feedback ───────────────────────────────────
        if (feedbackTimer > 0 && !feedbackMsg.isEmpty()) {
            game.getFont().setColor(feedbackColor);
            draw(feedbackMsg, PAD, bodyY + PAD + LINE_H * 2);
        }
    }

    private void drawPartyStatus(float halfW, float bodyY, float bodyH,
                                 int w, int h) {
        float x = halfW + PAD;
        float y = bodyY + bodyH - PAD - LINE_H;

        game.getFont().setColor(C_ACCENT);
        draw("PARTY STATUS", x, y);
        y -= LINE_H + 4;

        for (PlayerCharacter pc : session.getParty()) {
            boolean alive = pc.isAlive();
            game.getFont().setColor(alive ? C_TEXT : C_DIM);
            draw(String.format("%-10s", pc.getName())
                    + (alive ? "" : " [DEAD]"), x, y);
            y -= LINE_H - 2;

            if (alive) {
                drawBarInline(x + 4, y,
                        pc.getStat(Stat.HP), pc.getStat(Stat.MAX_HP),
                        C_HP_GREEN, C_HP_RED, "HP");
                drawBarInline(x + 4 + BAR_W + 55, y,
                        pc.getStat(Stat.MAGIC_POWER), pc.getStat(Stat.MAX_MAGIC),
                        C_MP_BLUE, C_DIM, "MP");
            }
            y -= LINE_H + 2;

            if (y < bodyY + PAD) break;
        }
    }

    private void drawFooter(int w, int h) {
        // Button labels are drawn here — buttons were outlined in render()
        for (int i = 0; i < buttons.size(); i++) {
            TownButton b = buttons.get(i);
            Color labelColor = (i == hoveredBtn) ? Color.WHITE : b.color;
            game.getFont().setColor(labelColor);
            float lx = b.x + b.w / 2f - b.label.length() * 3.5f;
            float ly = b.y + b.h / 2f + LINE_H / 2f - 2;
            draw(b.label, lx, ly);
        }
    }

    // -------------------------------------------------------------------------
    // Inline bar (opens/closes ShapeRenderer while inside getBatch context)
    // -------------------------------------------------------------------------

    private void drawBarInline(float x, float y,
                               int cur, int max,
                               Color full, Color low,
                               String label) {
        game.getBatch().end();

        sr.setProjectionMatrix(proj);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        sr.setColor(C_BAR_BG);
        sr.rect(x, y - BAR_H, BAR_W, BAR_H);

        float pct  = max > 0 ? (float) cur / max : 0f;
        sr.setColor(pct < 0.3f ? low : full);
        sr.rect(x, y - BAR_H, BAR_W * pct, BAR_H);

        sr.end();

        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(C_BORDER);
        sr.rect(x, y - BAR_H, BAR_W, BAR_H);
        sr.end();

        game.getBatch().setProjectionMatrix(proj);
        game.getBatch().begin();

        game.getFont().setColor(C_DIM);
        draw(label + ":" + cur + "/" + max, x + BAR_W + 4, y);
    }

    // -------------------------------------------------------------------------
    // Inn logic
    // -------------------------------------------------------------------------

    private int innCost() {
        int missing = 0;
        for (PlayerCharacter pc : session.getParty())
            if (pc.isAlive())
                missing += Math.max(0, pc.getStat(Stat.MAX_HP) - pc.getHp());
        return missing == 0 ? 0 : Math.max(5, missing * 10);
    }

    private void doRest() {
        int cost = innCost();
        if (cost == 0) {
            feedback("Party is already at full health.", C_DIM);
            return;
        }
        if (!session.getLedger().spendGold(cost)) {
            feedback("Not enough gold — need " + cost + " gp.", C_RED);
            return;
        }
        session.getParty().forEach(pc -> {
            if (!pc.isAlive()) return;
            pc.setStat(Stat.HP,          pc.getStat(Stat.MAX_HP));
            pc.setStat(Stat.MAGIC_POWER, pc.getStat(Stat.MAX_MAGIC));
        });
        feedback("Party rested — " + cost + " gp. All HP and MP restored.", C_GREEN);
        rebuildButtons();
        System.out.println("[TownScreen] Party rested in " + townName + " (" + cost + " gp)");
    }

    // -------------------------------------------------------------------------
    // Guild logic
    // -------------------------------------------------------------------------

    private void doGuildCheck() {
        guildLines.clear();
        GuildService g = GuildService.INSTANCE;
        for (PlayerCharacter pc : session.getParty()) {
            if (!pc.isAlive()) {
                guildLines.add(pc.getName() + ": DEAD");
                continue;
            }
            if (g.canTrain(pc)) {
                guildLines.add(pc.getName() + ": READY — " + g.trainingCost(pc) + " gp");
            } else {
                guildLines.add(pc.getName() + ": Not eligible yet");
            }
        }
        guildChecked = true;
        System.out.println("[TownScreen] Guild check performed in " + townName);
    }

    // -------------------------------------------------------------------------
    // Button construction
    // -------------------------------------------------------------------------

    private void rebuildButtons() {
        buttons.clear();

        int w = Gdx.graphics.getWidth();
        float btnH = 36f;
        float btnY = (FOOTER_H - btnH) / 2f;
        float x    = PAD;
        float gap  = 8f;
        float bw   = 130f;

        addBtn("LEAVE TOWN", C_ACCENT, x, btnY, bw, btnH, this::leaveTown);
        x += bw + gap;

        int cost = innCost();
        String restLabel = cost == 0
                ? "REST (full HP)"
                : "REST (" + cost + " gp)";
        boolean canRest = cost > 0 && session.getLedger().getPartyGold() >= cost;
        addBtn(restLabel, canRest ? C_GREEN : C_DIM, x, btnY, bw + 20, btnH,
                canRest ? this::doRest : () -> feedback("Cannot rest now.", C_DIM));
        x += bw + 20 + gap;

        addBtn("GUILD CHECK  [G]", C_TEXT, x, btnY, bw + 20, btnH,
                this::doGuildCheck);
    }

    private void addBtn(String label, Color color,
                        float x, float y, float w, float h,
                        Runnable action) {
        buttons.add(new TownButton(label, color, x, y, w, h, action));
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    private void registerInput() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE
                        || keycode == Input.Keys.ENTER
                        || keycode == Input.Keys.SPACE) {
                    leaveTown();
                    return true;
                }
                if (keycode == Input.Keys.G) {
                    doGuildCheck();
                    return true;
                }
                if (keycode == Input.Keys.R) {
                    doRest();
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                float gy = Gdx.graphics.getHeight() - screenY;
                for (TownButton b : buttons) {
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
                for (int i = 0; i < buttons.size(); i++) {
                    TownButton b = buttons.get(i);
                    if (screenX >= b.x && screenX <= b.x + b.w
                            && gy >= b.y && gy <= b.y + b.h) {
                        hoveredBtn = i;
                        break;
                    }
                }
                return false;
            }
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void draw(String text, float x, float y) {
        game.getFont().draw(game.getBatch(), text, x, y);
    }

    private void drawRect(ShapeRenderer sr, float x, float y,
                          float w, float h, Color c) {
        sr.setColor(c);
        sr.rect(x, y, w, h);
    }

    private void feedback(String msg, Color color) {
        feedbackMsg   = msg;
        feedbackColor = color;
        feedbackTimer = 3.5f;
    }

    private void leaveTown() {
        System.out.println("[TownScreen] Leaving " + townName);
        game.setScreen(returnScreen);
    }

    private void rebuildProj(int w, int h) {
        proj.setToOrtho2D(0, 0, w, h);
    }

    // -------------------------------------------------------------------------
    // TownButton record
    // -------------------------------------------------------------------------

    private static class TownButton {
        final String   label;
        final Color    color;
        final float    x, y, w, h;
        final Runnable action;

        TownButton(String label, Color color,
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