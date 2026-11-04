// phantasia-core/src/main/java/com/phantasia/core/world/WeatherManager.java
package com.phantasia.core.world;

import java.util.Random;

/**
 * Manages the current weather state and drives autonomous transitions.
 *
 * <h3>Transition model</h3>
 * Each weather state has a random duration in real seconds. When the
 * duration expires, the manager draws the next state from a weighted
 * table that is biased by the current {@link TimeOfDay}:
 * <ul>
 *   <li>VOID_MIST is more likely during {@link TimeOfDay#isDark() dark} hours
 *       and near the golden hour (dawn/dusk).</li>
 *   <li>STORM is more likely at night.</li>
 *   <li>CLEAR is always the most common outcome.</li>
 * </ul>
 *
 * <h3>Manual override</h3>
 * {@link #setWeather(WeatherState)} triggers an immediate state change —
 * useful for quest-driven weather events ("as you enter the valley, a
 * void-mist closes in").
 *
 * <h3>Usage</h3>
 * <pre>
 *   // In GameMain:
 *   WeatherManager weather = new WeatherManager();
 *
 *   // In the game-loop update:
 *   weather.update(dt, clock);
 *
 *   // In a frontend renderer:
 *   WeatherState ws = weather.getCurrentWeather();
 * </pre>
 */
public class WeatherManager {

    // -------------------------------------------------------------------------
    // Duration ranges (real seconds) per state
    // -------------------------------------------------------------------------

    /** Min real seconds a weather state lasts before possibly changing. */
    private static final float DURATION_MIN = 90f;   // 1.5 real minutes
    /** Max real seconds. */
    private static final float DURATION_MAX = 300f;  // 5 real minutes

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private WeatherState currentWeather  = WeatherState.CLEAR;
    private float        timeInState     = 0f;    // seconds elapsed this state
    private float        stateDuration;           // seconds until possible change

    private final Random rng;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public WeatherManager() {
        this.rng = new Random();
        this.stateDuration = randomDuration();
    }

    public WeatherManager(long seed) {
        this.rng = new Random(seed);
        this.stateDuration = randomDuration();
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    /**
     * Advances the weather simulation by {@code dt} real seconds.
     * May trigger a state transition.
     *
     * @param dt    delta time in real seconds
     * @param clock the world clock (used to bias transition probabilities)
     */
    public void update(float dt, WorldClock clock) {
        timeInState += dt;
        if (timeInState >= stateDuration) {
            transitionTo(rollNextWeather(clock.getTimeOfDay()));
        }
    }

    // -------------------------------------------------------------------------
    // Manual override
    // -------------------------------------------------------------------------

    /**
     * Forces an immediate weather change.
     * The duration timer resets so the new state persists for a full interval.
     */
    public void setWeather(WeatherState newWeather) {
        transitionTo(newWeather);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public WeatherState getCurrentWeather() {
        return currentWeather;
    }

    /**
     * Returns the fraction of the current state's duration that has elapsed,
     * in [0.0, 1.0]. Useful for frontends that want to fade weather effects
     * in and out smoothly.
     */
    public float getStateProgress() {
        return Math.min(1f, timeInState / stateDuration);
    }

    // -------------------------------------------------------------------------
    // Internal transition logic
    // -------------------------------------------------------------------------

    private void transitionTo(WeatherState next) {
        currentWeather = next;
        timeInState    = 0f;
        stateDuration  = randomDuration();
    }

    /**
     * Rolls the next weather state from a time-of-day biased probability table.
     *
     * WEIGHTS (higher = more likely):
     *
     *          CLEAR  OVERCAST  RAIN  VOID_MIST  STORM
     * Day      70     15        8     3          4
     * Night    50     18        12    12         8
     * Golden   50     15        8     18         9     (dawn/dusk favours mist)
     * Dark     45     20        12    16         7
     */
    private WeatherState rollNextWeather(TimeOfDay tod) {

        // Same state is allowed to repeat — weather can persist.
        // Weights: CLEAR, OVERCAST, RAIN, VOID_MIST, STORM
        int[] weights;

        if (tod.isGoldenHour()) {
            weights = new int[]{ 50, 15, 8, 18, 9 };
        } else if (tod.isDark()) {
            weights = new int[]{ 45, 20, 12, 16, 7 };
        } else if (tod == TimeOfDay.NOON || tod == TimeOfDay.MORNING) {
            weights = new int[]{ 70, 15, 8, 3, 4 };
        } else {
            weights = new int[]{ 58, 18, 10, 8, 6 };  // afternoon default
        }

        int total = 0;
        for (int w : weights) total += w;
        int roll = rng.nextInt(total);

        int cumulative = 0;
        WeatherState[] states = WeatherState.values();
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return states[i];
        }

        return WeatherState.CLEAR; // unreachable fallback
    }

    private float randomDuration() {
        return DURATION_MIN + rng.nextFloat() * (DURATION_MAX - DURATION_MIN);
    }
}
