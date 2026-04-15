// phantasia-jme/phantasia-jme-world/src/main/java/com/phantasia/jme/world/DayNightCycle.java
package com.phantasia.jme.world;

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.filters.FogFilter;

/**
 * Drives a continuous day/night cycle by animating two DirectionalLights,
 * an AmbientLight, and a FogFilter each frame.
 *
 * TIME MODEL:
 *   t in [0.0, 1.0] represents one full 24-hour cycle.
 *   0.00 / 1.00 = midnight
 *   0.25         = sunrise
 *   0.50         = noon
 *   0.75         = sunset
 *
 * LIGHTS:
 *   sun   — DirectionalLight that travels a semicircle east→zenith→west.
 *           Fades to near-zero at night.
 *   moon  — DirectionalLight with dim blue tint on the inverse arc.
 *           Brightest at midnight, zero at noon.
 *   ambient — AmbientLight whose color shifts from warm daytime to cool night.
 *
 * FOG:
 *   FogFilter color shifts through the same keyframes for scene coherence.
 *
 * USAGE:
 *   // In WorldState.initialize():
 *   dayNight = new DayNightCycle(sunLight, moonLight, ambientLight, fogFilter);
 *   dayNight.setCycleDurationSeconds(60f); // one full day in 60 real seconds
 *
 *   // In WorldState.update(tpf):
 *   dayNight.update(tpf);
 */
public final class DayNightCycle {

    // -------------------------------------------------------------------------
    // Keyframe times
    // -------------------------------------------------------------------------

    private static final float T_MIDNIGHT = 0.00f;
    private static final float T_SUNRISE  = 0.25f;
    private static final float T_NOON     = 0.50f;
    private static final float T_SUNSET   = 0.75f;

    // -------------------------------------------------------------------------
    // Sun color keyframes
    // -------------------------------------------------------------------------

    private static final ColorRGBA SUN_MIDNIGHT = new ColorRGBA(0.00f, 0.00f, 0.00f, 1f);
    private static final ColorRGBA SUN_SUNRISE  = new ColorRGBA(1.00f, 0.55f, 0.20f, 1f);
    private static final ColorRGBA SUN_NOON     = new ColorRGBA(1.00f, 0.98f, 0.88f, 1f);
    private static final ColorRGBA SUN_SUNSET   = new ColorRGBA(1.00f, 0.35f, 0.10f, 1f);

    // -------------------------------------------------------------------------
    // Moon color (constant — only brightness varies)
    // -------------------------------------------------------------------------

    private static final ColorRGBA MOON_FULL = new ColorRGBA(0.08f, 0.10f, 0.20f, 1f);

    // -------------------------------------------------------------------------
    // Ambient color keyframes
    // -------------------------------------------------------------------------

    private static final ColorRGBA AMB_MIDNIGHT = new ColorRGBA(0.02f, 0.03f, 0.08f, 1f);
    private static final ColorRGBA AMB_SUNRISE  = new ColorRGBA(0.30f, 0.22f, 0.18f, 1f);
    private static final ColorRGBA AMB_NOON     = new ColorRGBA(0.40f, 0.42f, 0.50f, 1f);
    private static final ColorRGBA AMB_SUNSET   = new ColorRGBA(0.25f, 0.18f, 0.22f, 1f);

    // -------------------------------------------------------------------------
    // Fog color keyframes
    // -------------------------------------------------------------------------

    private static final ColorRGBA FOG_MIDNIGHT = new ColorRGBA(0.03f, 0.04f, 0.12f, 1f);
    private static final ColorRGBA FOG_SUNRISE  = new ColorRGBA(0.80f, 0.55f, 0.35f, 1f);
    private static final ColorRGBA FOG_NOON     = new ColorRGBA(0.65f, 0.70f, 0.80f, 1f);
    private static final ColorRGBA FOG_SUNSET   = new ColorRGBA(0.75f, 0.35f, 0.25f, 1f);

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final DirectionalLight sun;
    private final DirectionalLight moon;
    private final AmbientLight     ambient;
    private final FogFilter        fog;

    /** Current time in [0.0, 1.0]. Start just before sunrise so the
     *  player immediately sees the lighting shift when the game opens. */
    private float time = 0.22f;

    /** How many real seconds one full day takes. */
    private float cycleDurationSeconds = 60f;

    // Scratch ColorRGBA to avoid per-frame allocation
    private final ColorRGBA scratch = new ColorRGBA();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public DayNightCycle(DirectionalLight sun,
                         DirectionalLight moon,
                         AmbientLight     ambient,
                         FogFilter        fog) {
        this.sun     = sun;
        this.moon    = moon;
        this.ambient = ambient;
        this.fog     = fog;
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    public void setCycleDurationSeconds(float seconds) {
        this.cycleDurationSeconds = seconds;
    }

    /** Jump to a specific time. 0=midnight, 0.25=sunrise, 0.5=noon, 0.75=sunset. */
    public void setTime(float t) {
        this.time = t % 1.0f;
    }

    public float getTime() { return time; }

    // -------------------------------------------------------------------------
    // Update — call every frame from WorldState.update(tpf)
    // -------------------------------------------------------------------------

    public void update(float tpf) {
        time += tpf / cycleDurationSeconds;
        if (time >= 1.0f) time -= 1.0f;

        updateSun();
        updateMoon();
        updateAmbient();
        updateFog();
    }

    // -------------------------------------------------------------------------
    // Sun
    // -------------------------------------------------------------------------

    private void updateSun() {
        // Color — interpolate through keyframes
        sun.setColor(sunColor());

        // Direction — semicircle from east horizon (sunrise) to west horizon (sunset).
        // Outside that window (night) the sun is below the horizon; color is already
        // near-zero so direction doesn't matter visually, but we keep it moving.
        float angle = (time - T_SUNRISE) / (T_SUNSET - T_SUNRISE) * FastMath.PI;
        // angle goes 0→PI over sunrise→sunset; sin peaks at 1.0 (noon)
        float sinA = FastMath.sin(angle);
        float cosA = FastMath.cos(angle);  // -1 at sunrise, +1 at sunset (E→W)

        // Sun travels roughly north-south overhead, east-to-west horizontally
        Vector3f dir = new Vector3f(-cosA * 0.4f, -sinA, -0.3f).normalizeLocal();
        sun.setDirection(dir);
    }

    private ColorRGBA sunColor() {
        if (time < T_SUNRISE) {
            // Midnight → sunrise
            float f = time / T_SUNRISE;
            return lerpColor(SUN_MIDNIGHT, SUN_SUNRISE, smoothstep(f), scratch);
        } else if (time < T_NOON) {
            // Sunrise → noon
            float f = (time - T_SUNRISE) / (T_NOON - T_SUNRISE);
            return lerpColor(SUN_SUNRISE, SUN_NOON, smoothstep(f), scratch);
        } else if (time < T_SUNSET) {
            // Noon → sunset
            float f = (time - T_NOON) / (T_SUNSET - T_NOON);
            return lerpColor(SUN_NOON, SUN_SUNSET, smoothstep(f), scratch);
        } else {
            // Sunset → midnight
            float f = (time - T_SUNSET) / (1.0f - T_SUNSET);
            return lerpColor(SUN_SUNSET, SUN_MIDNIGHT, smoothstep(f), scratch);
        }
    }

    // -------------------------------------------------------------------------
    // Moon — inverse arc of the sun, dim blue
    // -------------------------------------------------------------------------

    private void updateMoon() {
        // Moon brightness peaks at midnight (t=0/1), zero at noon (t=0.5)
        // Use a cosine so it fades smoothly
        float brightness = (FastMath.cos(time * FastMath.TWO_PI) + 1f) * 0.5f;

        scratch.set(
                MOON_FULL.r * brightness,
                MOON_FULL.g * brightness,
                MOON_FULL.b * brightness,
                1f);
        moon.setColor(scratch.clone());

        // Moon travels the opposite arc — below horizon during day
        float angle = (time - T_SUNRISE) / (T_SUNSET - T_SUNRISE) * FastMath.PI;
        float sinA  =  FastMath.sin(angle);
        float cosA  =  FastMath.cos(angle);
        // Opposite direction to sun
        Vector3f dir = new Vector3f(cosA * 0.4f, sinA, 0.3f).normalizeLocal();
        moon.setDirection(dir);
    }

    // -------------------------------------------------------------------------
    // Ambient
    // -------------------------------------------------------------------------

    private void updateAmbient() {
        ambient.setColor(ambientColor());
    }

    private ColorRGBA ambientColor() {
        if (time < T_SUNRISE) {
            float f = time / T_SUNRISE;
            return lerpColor(AMB_MIDNIGHT, AMB_SUNRISE, smoothstep(f), scratch);
        } else if (time < T_NOON) {
            float f = (time - T_SUNRISE) / (T_NOON - T_SUNRISE);
            return lerpColor(AMB_SUNRISE, AMB_NOON, smoothstep(f), scratch);
        } else if (time < T_SUNSET) {
            float f = (time - T_NOON) / (T_SUNSET - T_NOON);
            return lerpColor(AMB_NOON, AMB_SUNSET, smoothstep(f), scratch);
        } else {
            float f = (time - T_SUNSET) / (1.0f - T_SUNSET);
            return lerpColor(AMB_SUNSET, AMB_MIDNIGHT, smoothstep(f), scratch);
        }
    }

    // -------------------------------------------------------------------------
    // Fog
    // -------------------------------------------------------------------------

    private void updateFog() {
        if (fog == null) return;
        fog.setFogColor(fogColor());
    }

    private ColorRGBA fogColor() {
        if (time < T_SUNRISE) {
            float f = time / T_SUNRISE;
            return lerpColor(FOG_MIDNIGHT, FOG_SUNRISE, smoothstep(f), scratch);
        } else if (time < T_NOON) {
            float f = (time - T_SUNRISE) / (T_NOON - T_SUNRISE);
            return lerpColor(FOG_SUNRISE, FOG_NOON, smoothstep(f), scratch);
        } else if (time < T_SUNSET) {
            float f = (time - T_NOON) / (T_SUNSET - T_NOON);
            return lerpColor(FOG_NOON, FOG_SUNSET, smoothstep(f), scratch);
        } else {
            float f = (time - T_SUNSET) / (1.0f - T_SUNSET);
            return lerpColor(FOG_SUNSET, FOG_MIDNIGHT, smoothstep(f), scratch);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Smoothstep easing — makes transitions feel organic rather than linear. */
    private static float smoothstep(float t) {
        t = FastMath.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Lerps between two colors into dst to avoid allocation. Returns dst. */
    private static ColorRGBA lerpColor(ColorRGBA a, ColorRGBA b,
                                       float t, ColorRGBA dst) {
        dst.r = a.r + (b.r - a.r) * t;
        dst.g = a.g + (b.g - a.g) * t;
        dst.b = a.b + (b.b - a.b) * t;
        dst.a = 1f;
        return dst;
    }

    /**
     * Returns true when the sun is above the horizon — used by WorldState
     * to enable/disable the shadow filter so moonlit nights don't cast
     * harsh PSSM shadows from the wrong light source.
     * A small buffer around the exact sunrise/sunset times prevents
     * rapid enable/disable flickering at the horizon threshold.
     */
    public boolean isDaytime() {
        // Daytime = between sunrise and sunset with a small buffer
        return time >= (T_SUNRISE + 0.02f) && time <= (T_SUNSET - 0.02f);
    }
}