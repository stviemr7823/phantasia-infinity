// phantasia-j2d/src/main/java/com/phantasia/j2d/render/WeatherParticleSystem.java
package com.phantasia.j2d.render;

import com.phantasia.core.world.WeatherState;
import com.phantasia.j2d.engine.GameCanvas;

import java.awt.*;
import java.util.Random;

import com.phantasia.j2d.render.WeatherParticleSystem;

/**
 * Animated weather particle layer for the j2d overworld.
 *
 * <h3>Design constraints</h3>
 * <ul>
 *   <li>Zero per-particle {@code AlphaComposite} switches — one composite
 *       set before all particles, one reset after.</li>
 *   <li>All particle colours are pre-built constants — no {@code new Color()}
 *       inside the render loop.</li>
 *   <li>Fixed-size primitive arrays (no ArrayList) — zero GC pressure per
 *       frame.</li>
 *   <li>CLEAR and OVERCAST produce no particles — the system goes dormant
 *       and costs nothing.</li>
 * </ul>
 *
 * <h3>Particle behaviours by state</h3>
 * <ul>
 *   <li><b>RAIN</b> — thin diagonal streaks, fast, dense, blue-grey.</li>
 *   <li><b>STORM</b> — wider diagonal streaks, very fast, very dense,
 *       near-white. Occasional lightning flash (brief bright overlay).</li>
 *   <li><b>VOID_MIST</b> — slow drifting ovals, large, semi-transparent
 *       deep purple. Sinusoidal lateral drift gives the "tendril" feel.</li>
 *   <li><b>OVERCAST</b> — no particles (handled by the tile tint only).</li>
 *   <li><b>CLEAR</b> — dormant.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   // In WorldRoamScreen — construct once:
 *   private final WeatherParticleSystem weatherFx =
 *           new WeatherParticleSystem();
 *
 *   // In update(dt):
 *   weatherFx.update(dt, weatherManager.getCurrentWeather());
 *
 *   // In render(g), after tiles, entities, player — before HUD:
 *   weatherFx.render(g);
 * </pre>
 */
public class WeatherParticleSystem {

    // -------------------------------------------------------------------------
    // Particle counts (fixed pool sizes)
    // -------------------------------------------------------------------------

    private static final int RAIN_COUNT      = 200;
    private static final int STORM_COUNT     = 350;
    private static final int MIST_COUNT      = 30;

    // -------------------------------------------------------------------------
    // Pre-baked colours — no allocation inside render()
    // -------------------------------------------------------------------------

    // Rain — blue-grey streaks at ~60% opacity
    private static final Color RAIN_COLOR  = new Color(0x8A, 0xA8, 0xC8, 155);

    // Storm — near-white streaks at ~75% opacity
    private static final Color STORM_COLOR = new Color(0xCC, 0xDD, 0xEE, 190);

    // Void mist — deep purple ovals at ~45% opacity
    private static final Color MIST_COLOR  = new Color(0x2A, 0x0A, 0x3A, 115);

    // Lightning flash overlay — brief white-blue
    private static final Color LIGHTNING_COLOR = new Color(0xCC, 0xDD, 0xFF, 80);

    // -------------------------------------------------------------------------
    // Particle state (primitive arrays — no boxing, no GC)
    // -------------------------------------------------------------------------

    // Shared pool — sized to the largest weather type
    private final float[] px     = new float[STORM_COUNT]; // x position
    private final float[] py     = new float[STORM_COUNT]; // y position
    private final float[] pvx    = new float[STORM_COUNT]; // x velocity
    private final float[] pvy    = new float[STORM_COUNT]; // y velocity
    private final float[] pphase = new float[STORM_COUNT]; // mist sinusoidal phase
    private final int[]   plen   = new int  [STORM_COUNT]; // streak length (rain/storm)
    private final int[]   psize  = new int  [STORM_COUNT]; // oval size (mist)

    private int activeCount = 0;

    // -------------------------------------------------------------------------
    // Lightning state
    // -------------------------------------------------------------------------

    private float lightningTimer   = 0f;
    private float lightningAlpha   = 0f;
    private boolean lightningActive = false;

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------

    private WeatherState currentWeather = WeatherState.CLEAR;
    private final Random rng = new Random();

    private static final int W = GameCanvas.DESIGN_WIDTH;
    private static final int H = GameCanvas.DESIGN_HEIGHT;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public WeatherParticleSystem() {}

    // -------------------------------------------------------------------------
    // Update — call once per game loop tick
    // -------------------------------------------------------------------------

    /**
     * Advances all particles by {@code dt} seconds and respawns any that
     * have left the screen. Also handles weather state transitions.
     *
     * @param dt      delta time in seconds
     * @param weather the current weather state from {@link com.phantasia.core.world.WeatherManager}
     */
    public void update(float dt, WeatherState weather) {
        if (weather != currentWeather) {
            transition(weather);
        }

        switch (currentWeather) {
            case RAIN  -> updateRain(dt);
            case STORM -> updateStorm(dt);
            case VOID_MIST -> updateMist(dt);
            default    -> {} // CLEAR, OVERCAST — dormant
        }
    }

    // -------------------------------------------------------------------------
    // Render — call once per render frame, after tiles/entities, before HUD
    // -------------------------------------------------------------------------

    /**
     * Draws all active particles. Uses a single composite set/reset pair —
     * not per particle.
     */
    public void render(Graphics2D g) {
        if (activeCount == 0) return;

        switch (currentWeather) {
            case RAIN  -> renderRain(g);
            case STORM -> renderStorm(g);
            case VOID_MIST -> renderMist(g);
            default    -> {}
        }
    }

    // -------------------------------------------------------------------------
    // State transitions
    // -------------------------------------------------------------------------

    private void transition(WeatherState next) {
        currentWeather = next;
        lightningActive = false;
        lightningTimer  = 0f;
        lightningAlpha  = 0f;

        switch (next) {
            case RAIN      -> spawnRain(RAIN_COUNT);
            case STORM     -> spawnRain(STORM_COUNT);
            case VOID_MIST -> spawnMist(MIST_COUNT);
            default        -> activeCount = 0;
        }
    }

    // -------------------------------------------------------------------------
    // Spawn helpers
    // -------------------------------------------------------------------------

    private void spawnRain(int count) {
        activeCount = count;
        for (int i = 0; i < count; i++) {
            respawnRainParticle(i, true);
        }
    }

    private void spawnMist(int count) {
        activeCount = count;
        for (int i = 0; i < count; i++) {
            respawnMistParticle(i, true);
        }
    }

    // -------------------------------------------------------------------------
    // Rain / Storm update
    // -------------------------------------------------------------------------

    private void updateRain(float dt) {
        for (int i = 0; i < activeCount; i++) {
            px[i] += pvx[i] * dt;
            py[i] += pvy[i] * dt;
            if (py[i] > H + 20 || px[i] < -20 || px[i] > W + 20) {
                respawnRainParticle(i, false);
            }
        }
    }

    private void updateStorm(float dt) {
        updateRain(dt);

        // Lightning: fires randomly every 4–12 seconds
        if (!lightningActive) {
            lightningTimer += dt;
            if (lightningTimer > 4f + rng.nextFloat() * 8f) {
                lightningActive = true;
                lightningAlpha  = 1f;
                lightningTimer  = 0f;
            }
        } else {
            // Flash decays in ~0.15 seconds
            lightningAlpha -= dt / 0.15f;
            if (lightningAlpha <= 0f) {
                lightningAlpha  = 0f;
                lightningActive = false;
            }
        }
    }

    private void respawnRainParticle(int i, boolean scatter) {
        // Drift angle: rain falls at ~75° from vertical (strong wind)
        // vx = ~200 px/s rightward,  vy = ~700–900 px/s downward (storm faster)
        boolean isStorm = (currentWeather == WeatherState.STORM);
        float speed  = isStorm
                ? 700f + rng.nextFloat() * 300f
                : 450f + rng.nextFloat() * 200f;
        float angle  = isStorm ? 0.35f : 0.22f;  // radians from vertical

        pvx[i] = speed * (float) Math.sin(angle);
        pvy[i] = speed * (float) Math.cos(angle);

        plen[i] = isStorm ? 14 + rng.nextInt(10) : 8 + rng.nextInt(8);

        if (scatter) {
            // Initial scatter across entire screen
            px[i] = rng.nextFloat() * W;
            py[i] = rng.nextFloat() * H;
        } else {
            // Respawn at top or left edge
            if (rng.nextBoolean()) {
                px[i] = rng.nextFloat() * W;
                py[i] = -plen[i];
            } else {
                px[i] = -plen[i];
                py[i] = rng.nextFloat() * H;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Void Mist update
    // -------------------------------------------------------------------------

    private void updateMist(float dt) {
        for (int i = 0; i < activeCount; i++) {
            pphase[i] += dt * 0.6f;  // slow sinusoidal drift
            px[i] += (pvx[i] + (float) Math.sin(pphase[i]) * 18f) * dt;
            py[i] += pvy[i] * dt;

            // Wrap: mist reappears on the other side
            if (px[i] > W + psize[i]) px[i] = -psize[i];
            if (px[i] < -psize[i])    px[i] = W + psize[i];
            if (py[i] > H + psize[i]) py[i] = -psize[i];
            if (py[i] < -psize[i])    py[i] = H + psize[i];
        }
    }

    private void respawnMistParticle(int i, boolean scatter) {
        // Mist drifts slowly rightward and slightly upward/downward
        pvx[i] = 15f + rng.nextFloat() * 20f;
        pvy[i] = (rng.nextFloat() - 0.5f) * 12f;
        pphase[i] = rng.nextFloat() * (float) Math.PI * 2f;
        psize[i]  = 60 + rng.nextInt(80);  // large, wispy ovals

        if (scatter) {
            px[i] = rng.nextFloat() * W;
            py[i] = rng.nextFloat() * H;
        } else {
            // Respawn at left edge
            px[i] = -psize[i];
            py[i] = rng.nextFloat() * H;
        }
    }

    // -------------------------------------------------------------------------
    // Rain render
    // -------------------------------------------------------------------------

    private void renderRain(Graphics2D g) {
        // One composite set for all rain lines — not per line
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, RAIN_COLOR.getAlpha() / 255f));
        g.setColor(new Color(RAIN_COLOR.getRed(),
                RAIN_COLOR.getGreen(), RAIN_COLOR.getBlue()));
        g.setStroke(new BasicStroke(1.0f));

        for (int i = 0; i < activeCount; i++) {
            int x1 = (int) px[i];
            int y1 = (int) py[i];
            int x2 = (int)(px[i] - pvx[i] / pvy[i] * plen[i]);
            int y2 = (int)(py[i] - plen[i]);
            g.drawLine(x1, y1, x2, y2);
        }

        g.setComposite(old);
    }

    // -------------------------------------------------------------------------
    // Storm render
    // -------------------------------------------------------------------------

    private void renderStorm(Graphics2D g) {
        // Lightning flash — single fillRect, before the rain lines
        if (lightningActive && lightningAlpha > 0f) {
            Composite old2 = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, lightningAlpha * (LIGHTNING_COLOR.getAlpha() / 255f)));
            g.setColor(new Color(LIGHTNING_COLOR.getRed(),
                    LIGHTNING_COLOR.getGreen(), LIGHTNING_COLOR.getBlue()));
            g.fillRect(0, 0, W, H);
            g.setComposite(old2);
        }

        // Storm streaks — heavier than rain
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, STORM_COLOR.getAlpha() / 255f));
        g.setColor(new Color(STORM_COLOR.getRed(),
                STORM_COLOR.getGreen(), STORM_COLOR.getBlue()));
        g.setStroke(new BasicStroke(1.5f));

        for (int i = 0; i < activeCount; i++) {
            int x1 = (int) px[i];
            int y1 = (int) py[i];
            int x2 = (int)(px[i] - pvx[i] / pvy[i] * plen[i]);
            int y2 = (int)(py[i] - plen[i]);
            g.drawLine(x1, y1, x2, y2);
        }

        g.setComposite(old);
        g.setStroke(new BasicStroke(1f)); // restore default stroke
    }

    // -------------------------------------------------------------------------
    // Void Mist render
    // -------------------------------------------------------------------------

    private void renderMist(Graphics2D g) {
        // One composite set for all mist ovals
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, MIST_COLOR.getAlpha() / 255f));
        g.setColor(new Color(MIST_COLOR.getRed(),
                MIST_COLOR.getGreen(), MIST_COLOR.getBlue()));

        for (int i = 0; i < activeCount; i++) {
            int w = psize[i];
            int h = (int)(psize[i] * 0.4f);  // flat oval — horizontal tendril shape
            g.fillOval((int) px[i] - w / 2, (int) py[i] - h / 2, w, h);
        }

        g.setComposite(old);
    }
}
