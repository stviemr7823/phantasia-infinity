// Entity.java — getStat/setStat removed from abstract contract
package com.phantasia.core.model;

public abstract class Entity {

    public enum Limb { HEAD, L_ARM, R_ARM, TORSO, L_LEG, R_LEG }
    public enum ActionType { SWING, THRUST, CAST }

    protected String name;
    protected boolean asleep;
    protected boolean isParrying;
    private Action currentAction;
    private int selectedSpellId;
    private Entity primaryTarget;
    private CombatRank combatRank = CombatRank.FRONT;

    public Entity(String name) {
        this.name = name;
        this.currentAction = Action.NONE;
    }

    public String getName()  { return name; }
    public boolean isAsleep() { return asleep; }
    public void setAsleep(boolean status) { this.asleep = status; }
    public void setParrying(boolean status) { this.isParrying = status; }
    public void setAction(Action action)  { this.currentAction = action; }

    // Every entity has HP and can die — that's the shared contract
    public abstract int getHp();
    public abstract void setHp(int value);
    public abstract boolean isAlive();
    public abstract int getExperience();
    public abstract int getLimbStatus(Limb limb);
    public abstract void setLimbStatus(Limb limb, int status);
    public abstract boolean canPerform(ActionType type);
    public abstract DataCore getDataCore();

    public void takeDamage(int amount) {
        if (isParrying) amount /= 2;
        setHp(Math.max(0, getHp() - amount));
    }

    public void applyDamage(int amount) {
        setHp(Math.max(0, getHp() - amount));
    }

    public Action getCurrentAction()           { return currentAction; }
    public int getSelectedSpellId()            { return selectedSpellId; }
    public void setSelectedSpellId(int id)     { this.selectedSpellId = id; }
    public Entity getPrimaryTarget()           { return primaryTarget; }
    public void setPrimaryTarget(Entity target){ this.primaryTarget = target; }
    public CombatRank getCombatRank()              { return combatRank; }
    public void setCombatRank(CombatRank rank)     { this.combatRank = rank; }

}