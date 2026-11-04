// phantasia-core/src/main/java/com/phantasia/core/world/TimeOfDay.java
package com.phantasia.core.world;

/**
 * The named phase of the 24-hour game day.
 *
 * <p>One full game day lasts {@link WorldClock#CYCLE_SECONDS} real seconds
 * (24 minutes). {@link WorldClock#getTimeOfDay()} returns which phase the
 * world is currently in.</p>
 *
 * <p>Phases are used by frontends for:
 * <ul>
 *   <li>Sky colour selection and ambient light calculation</li>
 *   <li>NPC schedule events (future — shopkeepers close at night)</li>
 *   <li>Encounter table weighting (stronger enemies at night)</li>
 *   <li>Weather probability weighting (mist at dawn/dusk)</li>
 * </ul>
 */
public enum TimeOfDay {

    // ── Normalised time ranges (0.0 = midnight, 1.0 = next midnight) ─────────

    /** 00:00 – 04:00  The deep watch. */
    MIDNIGHT    (0.000f, 0.167f),

    /** 04:00 – 06:00  Sky lightens to bruised purple before dawn. */
    PRE_DAWN    (0.167f, 0.250f),

    /** 06:00 – 08:00  Sunrise — brief warm-orange window. */
    DAWN        (0.250f, 0.333f),

    /** 08:00 – 12:00  Full morning light. */
    MORNING     (0.333f, 0.500f),

    /** 12:00 – 16:00  High sun — deepest blue sky in dark-fantasy palette. */
    NOON        (0.500f, 0.667f),

    /** 16:00 – 18:00  Afternoon light fades. */
    AFTERNOON   (0.667f, 0.750f),

    /** 18:00 – 20:00  "Bruised orange" dusk as specified in design doc. */
    DUSK        (0.750f, 0.833f),

    /** 20:00 – 22:00  Deep purple twilight. */
    EVENING     (0.833f, 0.917f),

    /** 22:00 – 00:00  Night closes in. */
    NIGHT       (0.917f, 1.000f);

    // -------------------------------------------------------------------------

    public final float start;   // inclusive
    public final float end;     // exclusive (wraps at 1.0)

    TimeOfDay(float start, float end) {
        this.start = start;
        this.end   = end;
    }

    /**
     * Returns the TimeOfDay phase for the given normalised time (0.0 – 1.0).
     */
    public static TimeOfDay from(float normalisedTime) {
        float t = normalisedTime % 1.0f;
        for (TimeOfDay tod : values()) {
            if (t >= tod.start && t < tod.end) return tod;
        }
        return MIDNIGHT; // fallback for t == 1.0 exactly
    }

    /**
     * Returns true if it is dark enough to affect overworld visibility
     * (MIDNIGHT, PRE_DAWN, EVENING, NIGHT).
     */
    public boolean isDark() {
        return this == MIDNIGHT || this == PRE_DAWN
                || this == EVENING || this == NIGHT;
    }

    /**
     * Returns true during the transitional glow periods (DAWN, DUSK).
     */
    public boolean isGoldenHour() {
        return this == DAWN || this == DUSK;
    }
}
