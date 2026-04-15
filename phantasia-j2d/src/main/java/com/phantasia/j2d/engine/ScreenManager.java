// phantasia-j2d/src/main/java/com/phantasia/j2d/engine/ScreenManager.java
package com.phantasia.j2d.engine;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

/**
 * Manages game screen lifecycle and transitions.
 *
 * <p>Owns a {@code Map<GameState, Screen>} registered at startup.
 * Provides three transition modes:</p>
 * <ul>
 *   <li><b>Instant cut</b> — immediate switch, no fade</li>
 *   <li><b>Fade to black</b> — current screen fades out, black frame, new screen fades in</li>
 *   <li><b>Push / Pop</b> — overlay screen (e.g., pause) on top of the current screen</li>
 * </ul>
 *
 * <p>The overlay stack allows screens like Dialogue or Pause to render
 * on top of the underlying screen. The bottom screen still receives
 * {@code render()} calls but not {@code update()} or input events.</p>
 */
public class ScreenManager {

    // -------------------------------------------------------------------------
    // Transition types
    // -------------------------------------------------------------------------

    public enum Transition {
        CUT,
        FADE_TO_BLACK,
        FADE_THROUGH_BLACK
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Duration of each fade half (out + in), in seconds. */
    private static final float FADE_DURATION = 0.3f;

    private static final Color VOID_COLOR = new Color(0x0D, 0x0D, 0x12); // #0D0D12

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final Map<GameState, Screen> screens = new EnumMap<>(GameState.class);
    private final Deque<GameState>       stack   = new ArrayDeque<>();

    private GameState   activeState;
    private Screen      activeScreen;

    // Fade state machine
    private boolean     fading;
    private float       fadeTimer;
    private float       fadeAlpha;       // 0 = transparent, 1 = full black
    private boolean     fadingOut;       // true = fading out old, false = fading in new
    private GameState   fadeTarget;
    private Object      fadeTransitionData;

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers a screen for a given game state.
     * Must be called before the game loop starts.
     */
    public void register(GameState state, Screen screen) {
        screens.put(state, screen);
    }

    // -------------------------------------------------------------------------
    // Transitions
    // -------------------------------------------------------------------------

    /**
     * Transitions to a new screen with an instant cut.
     */
    public void switchTo(GameState target, Object transitionData) {
        doSwitch(target, transitionData);
    }

    /**
     * Transitions with a fade-to-black effect.
     */
    public void fadeTo(GameState target, Object transitionData) {
        if (activeScreen == null) {
            // No current screen — just cut
            doSwitch(target, transitionData);
            return;
        }
        fading             = true;
        fadingOut          = true;
        fadeTimer          = 0f;
        fadeAlpha          = 0f;
        fadeTarget         = target;
        fadeTransitionData = transitionData;
    }

    /**
     * Pushes an overlay screen on top of the current screen.
     * The underlying screen continues to render but does not receive
     * update or input calls.
     */
    public void push(GameState overlay, Object transitionData) {
        if (activeState != null) {
            stack.push(activeState);
        }
        doSwitch(overlay, transitionData);
    }

    /**
     * Pops the overlay and returns to the screen underneath.
     */
    public void pop() {
        if (stack.isEmpty()) return;
        if (activeScreen != null) activeScreen.onExit();

        GameState previous = stack.pop();
        activeState  = previous;
        activeScreen = screens.get(previous);
        // No onEnter — the underlying screen was never exited
    }

    // -------------------------------------------------------------------------
    // Game loop integration
    // -------------------------------------------------------------------------

    /**
     * Called once per logic tick.
     */
    public void update(float dt) {
        if (fading) {
            updateFade(dt);
            return; // Don't update screens during fade
        }
        if (activeScreen != null) {
            activeScreen.update(dt);
        }
    }

    /**
     * Called once per render frame.
     */
    public void render(Graphics2D g, int width, int height) {
        // Render the bottom of the stack if there's an overlay
        if (!stack.isEmpty()) {
            Screen bottom = screens.get(stack.peekFirst());
            if (bottom != null) {
                bottom.render(g);
            }
        }

        // Render active screen
        if (activeScreen != null) {
            activeScreen.render(g);
        }

        // Fade overlay
        if (fading && fadeAlpha > 0f) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha));
            g.setColor(VOID_COLOR);
            g.fillRect(0, 0, width, height);
            g.setComposite(AlphaComposite.SrcOver); // reset
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Screen      getActiveScreen() { return activeScreen; }
    public GameState   getActiveState()  { return activeState;  }
    public boolean     isFading()        { return fading;       }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void doSwitch(GameState target, Object transitionData) {
        if (activeScreen != null) {
            activeScreen.onExit();
        }
        activeState  = target;
        activeScreen = screens.get(target);
        if (activeScreen != null) {
            activeScreen.onEnter(transitionData);
        }
    }

    private void updateFade(float dt) {
        fadeTimer += dt;
        float progress = fadeTimer / FADE_DURATION;

        if (fadingOut) {
            fadeAlpha = Math.min(1f, progress);
            if (progress >= 1f) {
                // Mid-point: switch screens while fully black
                doSwitch(fadeTarget, fadeTransitionData);
                fadingOut = false;
                fadeTimer = 0f;
            }
        } else {
            fadeAlpha = Math.max(0f, 1f - progress);
            if (progress >= 1f) {
                // Fade complete
                fading    = false;
                fadeAlpha = 0f;
            }
        }
    }
}