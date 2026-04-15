// phantasia-j2d/src/main/java/com/phantasia/j2d/engine/Screen.java
package com.phantasia.j2d.engine;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * The contract for every game screen in the j2d frontend.
 *
 * <p>Screens are owned by the {@link ScreenManager}. The game loop calls
 * {@link #update(float)} and {@link #render(Graphics2D)} every frame.
 * Input events are routed by {@link InputRouter} to the active screen.</p>
 *
 * <p>Lifecycle:</p>
 * <ol>
 *   <li>{@link #onEnter(Object)} — called when this screen becomes active.
 *       The transition data carries context from the previous screen
 *       (e.g., encounter enemy list, town feature record).</li>
 *   <li>{@link #update(float)} + {@link #render(Graphics2D)} — called every frame.</li>
 *   <li>{@link #onExit()} — called when transitioning away from this screen.</li>
 * </ol>
 *
 * <p>Screens must not hold references to the AWT Canvas or Frame.
 * All rendering goes through the {@link Graphics2D} passed to render().</p>
 */
public interface Screen {

    /**
     * Called when this screen becomes active.
     *
     * @param transitionData  context from the previous screen, or null.
     *                        The concrete type depends on the transition
     *                        (e.g., List&lt;Monster&gt; for encounter splash).
     */
    void onEnter(Object transitionData);

    /**
     * Fixed-timestep logic update.
     *
     * @param deltaSeconds  time since last update, in seconds (typically 1/60)
     */
    void update(float deltaSeconds);

    /**
     * Render this screen's contents.
     *
     * <p>The Graphics2D is pre-configured with rendering hints (LCD AA,
     * bicubic interpolation) and pre-scaled to the design resolution
     * (1920×1080). Screens paint as if the surface is always 1080p.</p>
     *
     * @param g  the graphics context to paint into
     */
    void render(Graphics2D g);

    /**
     * Called when this screen is about to be replaced or popped.
     * Release transient resources here.
     */
    void onExit();

    /**
     * Keyboard input — called on the game loop thread (not the EDT).
     *
     * @param e  the key event
     */
    void onKeyPressed(KeyEvent e);

    /**
     * Keyboard release.
     */
    default void onKeyReleased(KeyEvent e) {}

    /**
     * Mouse click input.
     *
     * @param e  the mouse event (coordinates are in design-resolution space)
     */
    void onMouseClicked(MouseEvent e);

    /**
     * Mouse movement — for hover effects on menus.
     * Default is a no-op; screens override if they need hover tracking.
     */
    default void onMouseMoved(MouseEvent e) {}
}