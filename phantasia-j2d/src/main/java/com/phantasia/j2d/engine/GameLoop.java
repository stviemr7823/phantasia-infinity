// phantasia-j2d/src/main/java/com/phantasia/j2d/engine/GameLoop.java
package com.phantasia.j2d.engine;

import java.awt.Graphics2D;

/**
 * The game loop — fixed-timestep update at 60 Hz, uncapped render.
 *
 * <p>Runs on a dedicated daemon thread. The update tick is fixed at
 * 1/60th of a second for deterministic game logic. Render calls happen
 * as fast as the display allows (or the BufferStrategy vsync caps).</p>
 *
 * <p>Clean shutdown: call {@link #stop()}, which sets the running flag
 * and the thread exits on the next iteration. The shutdown hook in
 * {@code GameMain} calls this.</p>
 *
 * <h3>Architecture</h3>
 * <pre>
 *   GameLoop owns: GameCanvas, ScreenManager
 *   GameLoop calls: ScreenManager.update(dt), ScreenManager.render(g)
 *   GameLoop does NOT call: any Screen directly
 * </pre>
 */
public class GameLoop implements Runnable {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Logic update rate. */
    private static final int    TARGET_UPS   = 60;
    private static final double NS_PER_UPDATE = 1_000_000_000.0 / TARGET_UPS;
    private static final float  DT_SECONDS   = 1.0f / TARGET_UPS;

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private final GameCanvas    canvas;
    private final ScreenManager screenManager;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private volatile boolean running;
    private Thread           thread;

    // Performance counters (visible for debug overlay)
    private int fps;
    private int ups;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public GameLoop(GameCanvas canvas, ScreenManager screenManager) {
        this.canvas        = canvas;
        this.screenManager = screenManager;
    }

    // -------------------------------------------------------------------------
    // Start / stop
    // -------------------------------------------------------------------------

    /**
     * Starts the game loop on a new daemon thread.
     */
    public void start() {
        if (running) return;
        running = true;
        thread  = new Thread(this, "GameLoop");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Signals the loop to stop. Returns immediately;
     * the thread will exit within one frame.
     */
    public void stop() {
        running = false;
    }

    /**
     * Blocks until the game loop thread has terminated.
     */
    public void awaitShutdown() {
        if (thread == null) return;
        try {
            thread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // The loop
    // -------------------------------------------------------------------------

    @Override
    public void run() {
        long previousTime = System.nanoTime();
        double accumulator = 0;

        int frameCount  = 0;
        int updateCount = 0;
        long counterTime = System.nanoTime();

        while (running) {
            long currentTime = System.nanoTime();
            long elapsed     = currentTime - previousTime;
            previousTime     = currentTime;

            accumulator += elapsed;

            // ── Fixed-timestep updates ───────────────────────────────────
            while (accumulator >= NS_PER_UPDATE) {
                screenManager.update(DT_SECONDS);
                accumulator -= NS_PER_UPDATE;
                updateCount++;
            }

            // ── Render ───────────────────────────────────────────────────
            Graphics2D g = canvas.beginFrame();
            if (g != null) {
                try {
                    screenManager.render(g,
                            GameCanvas.DESIGN_WIDTH,
                            GameCanvas.DESIGN_HEIGHT);
                } finally {
                    g.dispose();
                    canvas.endFrame();
                }
                frameCount++;
            }

            // ── FPS / UPS counter (once per second) ─────────────────────
            if (currentTime - counterTime >= 1_000_000_000L) {
                fps = frameCount;
                ups = updateCount;
                frameCount  = 0;
                updateCount = 0;
                counterTime = currentTime;
            }

            // Yield to avoid burning 100% CPU when render is very fast
            Thread.yield();
        }

        System.out.println("[GameLoop] Stopped. Final FPS=" + fps + " UPS=" + ups);
    }

    // -------------------------------------------------------------------------
    // Debug accessors
    // -------------------------------------------------------------------------

    public int getFps() { return fps; }
    public int getUps() { return ups; }
    public boolean isRunning() { return running; }
}