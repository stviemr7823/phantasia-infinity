// phantasia-core/src/main/java/com/phantasia/core/world/WeatherState.java
package com.phantasia.core.world;

/**
 * The current atmospheric condition.
 *
 * <p>Frontends react to each state differently:
 * <ul>
 *   <li>j2d — tint modifier over the sky overlay; future: particle layer</li>
 *   <li>JME — camera-attached particle emitters for rain; fog density changes</li>
 *   <li>libGDX — full-screen GLSL moving-noise shader for VOID_MIST;
 *       rain sprites for RAIN/STORM</li>
 * </ul>
 * Core defines the state; frontends own the visual response.</p>
 */
public enum WeatherState {

    /** Clear skies — no additional effect. Most common state. */
    CLEAR,

    /**
     * Heavy cloud cover. Reduces ambient brightness by ~15%.
     * No precipitation.
     */
    OVERCAST,

    /**
     * Steady rain. Reduces ambient brightness by ~25%.
     * Encounter rate slightly increased (animals stirred by weather).
     */
    RAIN,

    /**
     * Signature "Void Mist" — tendrils of primordial void energy
     * drift across the landscape. Associated with the Sundering.
     * Reduces brightness; adds a deep-purple chromatic tint.
     * More common near dungeon entrances and corrupted terrain.
     */
    VOID_MIST,

    /**
     * Thunderstorm — heavy rain plus lightning flashes.
     * Maximum darkness; frontends may strobe ambient light.
     */
    STORM;

    /**
     * Returns the ambient brightness multiplier for this weather state.
     * Applied on top of the sky-light brightness from {@link WorldClock}.
     *
     * @return a scalar in [0.0, 1.0] — multiply against
     *         {@link WorldClock.SkyLight#ambientBrightness()}
     */
    public float ambientMultiplier() {
        return switch (this) {
            case CLEAR     -> 1.00f;
            case OVERCAST  -> 0.82f;
            case RAIN      -> 0.70f;
            case VOID_MIST -> 0.65f;
            case STORM     -> 0.45f;
        };
    }

    /**
     * Returns a tint colour modifier as a packed RGB int (0xRRGGBB) that
     * the frontend blends into the sky colour at low opacity.
     * Returns 0 (black / no tint) for CLEAR and OVERCAST.
     */
    public int tintRgb() {
        return switch (this) {
            case CLEAR, OVERCAST -> 0x000000;
            case RAIN            -> 0x1A2A4A; // cold blue-grey
            case VOID_MIST       -> 0x2A0A3A; // deep void-purple
            case STORM           -> 0x0A0A1A; // near-black with blue shift
        };
    }

    /**
     * Returns the opacity of the weather tint in [0, 80] (0–255 alpha scale).
     * Combined with {@link #tintRgb()} to produce the weather overlay.
     */
    public int tintAlpha() {
        return switch (this) {
            case CLEAR, OVERCAST -> 0;
            case RAIN            -> 40;
            case VOID_MIST       -> 60;
            case STORM           -> 35;
        };
    }
}
