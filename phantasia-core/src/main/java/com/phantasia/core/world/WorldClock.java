// phantasia-core/src/main/java/com/phantasia/core/world/WorldClock.java
package com.phantasia.core.world;

/**
 * Tracks the passage of in-game time and provides sky-light data
 * for all frontends.
 *
 * <h3>Time model</h3>
 * One full game day = {@value #CYCLE_SECONDS} real seconds (24 minutes).
 * Time is represented internally as a float {@code 0.0 – 1.0} where
 * {@code 0.0} = midnight, {@code 0.5} = noon, {@code 1.0} = next midnight.
 *
 * <h3>Sky light</h3>
 * {@link #getSkyLight()} returns a {@link SkyLight} record containing
 * a renderer-agnostic sky colour (RGB ints 0–255) and an ambient
 * brightness scalar (0.0 = pitch black, 1.0 = full daylight).
 * Frontends convert this however they need — j2d makes it a
 * {@code java.awt.Color}, JME drives a DirectionalLight intensity,
 * libGDX drives a shader uniform.
 *
 * <h3>Usage</h3>
 * <pre>
 *   // In GameMain:
 *   WorldClock clock = new WorldClock();
 *
 *   // In the game loop update:
 *   clock.update(dt);
 *
 *   // In WorldRoamScreen.render():
 *   WorldClock.SkyLight light = clock.getSkyLight();
 *   // use light.r(), light.g(), light.b(), light.ambientBrightness()
 * </pre>
 */
public class WorldClock {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Real seconds per full game-day cycle (24 real minutes). */
    public static final float CYCLE_SECONDS = 24f * 60f;  // 1440 s

    // -------------------------------------------------------------------------
    // Sky light waypoints — one per key phase of the day.
    //
    // Each waypoint: { normalisedTime, r, g, b, ambientBrightness }
    //
    // Dark-western-high-fantasy palette — the sky is never fully bright.
    // The design doc specifies "dawn purples" and "bruised-orange dusk".
    // -------------------------------------------------------------------------

    private static final float[][] WAYPOINTS = {
            // time    r     g     b   ambient
            { 0.000f,  13,  13,  18,  0.15f },  // 00:00  void-black midnight
            { 0.167f,  13,  13,  18,  0.13f },  // 04:00  deepest dark (pre-dawn low)
            { 0.210f,  30,  12,  40,  0.22f },  // 05:00  purple pre-dawn lifts
            { 0.250f,  55,  22,  65,  0.35f },  // 06:00  dawn — bruised purple
            { 0.292f,  80,  38,  20,  0.52f },  // 07:00  sunrise — burnt orange
            { 0.375f,  22,  35,  75,  0.75f },  // 09:00  morning blue deepens
            { 0.500f,  14,  20,  52,  0.90f },  // 12:00  high noon — deep blue sky
            { 0.625f,  18,  24,  58,  0.85f },  // 15:00  afternoon
            { 0.708f,  70,  28,  14,  0.62f },  // 17:00  dusk — "bruised orange"
            { 0.750f,  55,  18,  40,  0.45f },  // 18:00  last sunset glow
            { 0.792f,  30,  10,  38,  0.30f },  // 19:00  evening purple descends
            { 0.875f,  18,   8,  24,  0.20f },  // 21:00  night closing in
            { 0.917f,  13,  13,  18,  0.15f },  // 22:00  back to void-black
            { 1.000f,  13,  13,  18,  0.15f },  // 24:00  = 00:00 (wrap anchor)
    };

    // -------------------------------------------------------------------------
    // Sky light record — renderer-agnostic
    // -------------------------------------------------------------------------

    /**
     * Snapshot of sky-lighting data at a specific moment in the day cycle.
     *
     * @param r                 sky tint red   (0–255)
     * @param g                 sky tint green (0–255)
     * @param b                 sky tint blue  (0–255)
     * @param ambientBrightness 0.0 = pitch black, 1.0 = full brightness
     */
    public record SkyLight(int r, int g, int b, float ambientBrightness) {

        /**
         * Linear interpolation between two SkyLight values.
         * Used internally by WorldClock; also available to frontends that
         * want to blend between clock readings.
         */
        public static SkyLight lerp(SkyLight a, SkyLight b, float t) {
            float u = 1f - t;
            return new SkyLight(
                    Math.round(a.r * u + b.r * t),
                    Math.round(a.g * u + b.g * t),
                    Math.round(a.b * u + b.b * t),
                    a.ambientBrightness * u + b.ambientBrightness * t
            );
        }

        /**
         * The overlay alpha this sky light implies for a j2d tint pass.
         *
         * <p>At full brightness (1.0) the overlay is nearly transparent.
         * At darkness (0.0) the overlay is nearly opaque.
         * Clamped to [0, 210] to ensure tiles are always faintly visible.</p>
         *
         * @return alpha value in [0, 210]
         */
        public int overlayAlpha() {
            // Cap to 160 (not 210) so even midnight never fully obscures tiles.
            // Midday (brightness=0.90) → 8/255 ≈ 3%  — barely visible
            // 07:00  (brightness=0.52) → 38/255 ≈ 15% — gentle morning warmth
            // Night  (brightness=0.15) → 72/255 ≈ 28% — clearly darker, readable
            // Midnight (brightness=0.13)→ 80/255 ≈ 31% — dark but still playable
            return Math.min(160, Math.round((1f - ambientBrightness) * 80f));
        }
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Normalised time: 0.0 = midnight, 0.5 = noon, wraps at 1.0. */
    private float normalisedTime;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /** Creates a clock starting at early morning (07:00). */
    public WorldClock() {
        this.normalisedTime = 0.292f; // 07:00 — just after dawn
    }

    /** Creates a clock at a specific normalised time (0.0 – 1.0). */
    public WorldClock(float startTime) {
        this.normalisedTime = startTime % 1.0f;
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    /**
     * Advances the clock by {@code dt} real seconds.
     * Call once per game-loop tick.
     */
    public void update(float dt) {
        normalisedTime = (normalisedTime + dt / CYCLE_SECONDS) % 1.0f;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the current normalised time in [0.0, 1.0).
     * 0.0 = midnight, 0.25 = 06:00, 0.5 = noon, 0.75 = 18:00.
     */
    public float getNormalisedTime() {
        return normalisedTime;
    }

    /** Returns the current named phase of the day. */
    public TimeOfDay getTimeOfDay() {
        return TimeOfDay.from(normalisedTime);
    }

    /**
     * Returns the current sky-light data, smoothly interpolated between
     * the nearest waypoints.
     */
    public SkyLight getSkyLight() {
        return interpolate(normalisedTime);
    }

    /**
     * Returns the in-game hour (0–23) as a display string, e.g. {@code "07:34"}.
     */
    public String getDisplayTime() {
        float totalMinutes = normalisedTime * 24f * 60f;
        int   hour   = (int)(totalMinutes / 60f) % 24;
        int   minute = (int)(totalMinutes % 60f);
        return String.format("%02d:%02d", hour, minute);
    }

    // -------------------------------------------------------------------------
    // Interpolation
    // -------------------------------------------------------------------------

    private static SkyLight interpolate(float t) {
        // Find the surrounding waypoints
        int lo = 0;
        for (int i = 0; i < WAYPOINTS.length - 1; i++) {
            if (t >= WAYPOINTS[i][0] && t < WAYPOINTS[i + 1][0]) {
                lo = i;
                break;
            }
        }
        int hi = lo + 1;

        float span = WAYPOINTS[hi][0] - WAYPOINTS[lo][0];
        float frac = (span > 0f) ? (t - WAYPOINTS[lo][0]) / span : 0f;

        SkyLight a = new SkyLight(
                (int) WAYPOINTS[lo][1], (int) WAYPOINTS[lo][2],
                (int) WAYPOINTS[lo][3], WAYPOINTS[lo][4]);
        SkyLight b = new SkyLight(
                (int) WAYPOINTS[hi][1], (int) WAYPOINTS[hi][2],
                (int) WAYPOINTS[hi][3], WAYPOINTS[hi][4]);

        return SkyLight.lerp(a, b, frac);
    }
}
