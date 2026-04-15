package com.phantasia.core.model;

/**
 * Defines the tactical actions available in the Phantasia III Combat Engine.
 */
public enum Action {
    // Standard Melee
    ATTACK,      // Basic single hit
    THRUST,      // Accuracy bonus, single hit
    SLASH,       // Multi-hit maneuver (Levels 1-3)
    LUNGE,       // Allows hitting Rank 2 from Rank 1

    // Advanced Melee
    AIM_BLOW,    // High damage/accuracy, slow speed
    PARRY,       // Defensive stance, boosts AC for the turn

    // Magic & Misc
    CAST,        // Triggers the SpellFactory/MagicBridge
    RUN,         // Escape attempt based on Speed/Dexterity
    NONE         // Default/Idle state
}