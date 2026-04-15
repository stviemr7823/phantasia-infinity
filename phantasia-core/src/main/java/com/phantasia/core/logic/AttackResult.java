package com.phantasia.core.logic;

import com.phantasia.core.model.Entity;

public class AttackResult {

    private final Entity attacker;
    private final Entity target;
    private boolean hit;
    private boolean critical;
    private int baseDamage;
    private int finalDamage;
    private String limb;

    public AttackResult(Entity attacker, Entity target) {
        this.attacker = attacker;
        this.target   = target;
    }

    // --- Getters ---
    public Entity getAttacker() { return attacker; }
    public Entity getTarget() { return target; }
    public boolean isHit() { return hit; }
    public boolean isCritical() { return critical; }
    public int getBaseDamage() { return baseDamage; }
    public int getFinalDamage() { return finalDamage; }
    public String getLimb() { return limb; }

    // --- Setters ---
    public void setHit(boolean hit) {
        this.hit = hit;
    }
    public void setCritical(boolean critical) { this.critical = critical; }
    public void setBaseDamage(int baseDamage) { this.baseDamage = baseDamage; }
    public void setFinalDamage(int finalDamage) { this.finalDamage = finalDamage; }
    public void setLimb(String limb) { this.limb = limb; }
}