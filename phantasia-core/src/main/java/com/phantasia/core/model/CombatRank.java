package com.phantasia.core.model;

/**
 * Tactical positioning in the battle line.
 * Affects damage output and targeting priority.
 */
public enum CombatRank {
    FRONT(1, 1.0f),   // Rank 1: 100% Melee Output
    MIDDLE(2, 0.5f),  // Rank 2: 50% Melee Output / Reach
    BACK(3, 0.1f);    // Rank 3: 10% Melee Output / Magic Focused

    public final int position;
    public final float damageMod;

    CombatRank(int position, float damageMod) {
        this.position = position;
        this.damageMod = damageMod;
    }
}