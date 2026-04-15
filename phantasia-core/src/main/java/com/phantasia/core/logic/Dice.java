// phantasia-core/src/main/java/com/phantasia/core/logic/Dice.java
package com.phantasia.core.logic;

import java.util.Random;

/**
 * Centralised dice roller for all combat, world, and judgment randomness.
 *
 * A single Random instance is shared across the entire engine —
 * eliminates scattered {@code new Random()} calls throughout the codebase.
 *
 * All methods are static — call Dice.d100(), Dice.d10(), etc.
 */
public final class Dice {

    private Dice() {}

    private static final Random RNG = new Random();

    /** Returns a value from 1 to 4. */
    public static int d4()   { return RNG.nextInt(4)   + 1; }

    /** Returns a value from 1 to 6. */
    public static int d6()   { return RNG.nextInt(6)   + 1; }

    /** Returns a value from 1 to 8. */
    public static int d8()   { return RNG.nextInt(8)   + 1; }

    /** Returns a value from 1 to 10. */
    public static int d10()  { return RNG.nextInt(10)  + 1; }

    /** Returns a value from 1 to 20. */
    public static int d20()  { return RNG.nextInt(20)  + 1; }

    /** Returns a value from 1 to 100. */
    public static int d100() { return RNG.nextInt(100) + 1; }

    /** Returns a value from 0 (inclusive) to bound (exclusive). */
    public static int nextInt(int bound) { return RNG.nextInt(bound); }

    /** Returns true with the given percentage chance (0–100). */
    public static boolean chance(int percent) {
        return RNG.nextInt(100) < percent;
    }
}