// Monster.java — no Stat enum, typed accessors only
package com.phantasia.core.model;

public class Monster extends Entity {
    private final DataCore core;

    public Monster(DataCore core) {
        super(core.getName());
        this.core = core;
    }

    @Override public DataCore getDataCore() { return core; }

    @Override
    public int getHp()       { return core.getStat(DataLayout.MON_HP); }
    @Override
    public void setHp(int v) { core.setStat(DataLayout.MON_HP, v); }

    public int getMaxSpawn() { return core.getStat(DataLayout.MON_MAX_SPAWN); }
    public int getItem1()    { return core.getStat(DataLayout.MON_ITEM_1); }
    public int getItem2()    { return core.getStat(DataLayout.MON_ITEM_2); }

    public boolean isUndead() {
        return (core.getStat(DataLayout.MON_FLAGS) & DataLayout.MON_FLAG_UNDEAD) != 0;
    }
    public boolean isAmorphous() {
        return (core.getStat(DataLayout.MON_FLAGS) & DataLayout.MON_FLAG_AMORPHOUS) != 0;
    }

    @Override
    public int getExperience() { return core.getShort(DataLayout.MON_XP); }

    public int getTreasure()   { return core.getShort(DataLayout.MON_TREASURE); }

    @Override
    public boolean isAlive()   { return getHp() > 0; }

    @Override
    public int getLimbStatus(Limb limb)            { return core.getStat(24 + limb.ordinal()); }
    @Override
    public void setLimbStatus(Limb limb, int status) { core.setStat(24 + limb.ordinal(), status); }



    @Override
    public boolean canPerform(ActionType type) { return getLimbStatus(Limb.HEAD) < 3; }
}