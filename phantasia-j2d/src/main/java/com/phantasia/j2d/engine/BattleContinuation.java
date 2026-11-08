package com.phantasia.j2d.engine;

import com.phantasia.core.data.*;
import com.phantasia.core.logic.LocalEncounterFlow;
import com.phantasia.core.model.Monster;
import java.util.List;

/** The same route/receipt is carried through every round and result screen. */
public final class BattleContinuation {
    private final GameState target;
    private final Object data;
    private final CombatRewards rewards;
    private final LocalEncounterFlow.Battle local;
    private boolean returned;
    public BattleContinuation(GameSession session,List<Monster> enemies) { target=GameState.WORLD_ROAM;data=null;rewards=new CombatRewards(session,enemies);local=null; }
    public BattleContinuation(LocalEncounterFlow.Battle local,Object data) { this.local=local;this.data=data;target=GameState.LOCAL_MAP;rewards=null; }
    public boolean isLocal() { return local != null; }
    public CombatRewards.Receipt victory() { return local==null ? rewards.victory() : local.victory(); }
    public void returnVictory(ScreenManager manager) { if(returned)return; if(local!=null)local.resumeVictory();returned=true;manager.fadeTo(target,data); }
    public void returnEscape(ScreenManager manager) { if(returned)return; if(local!=null)local.escape();returned=true;manager.fadeTo(target,data); }
    public void returnDefeat(ScreenManager manager) { if(returned)return; if(local!=null)local.defeat();returned=true;manager.fadeTo(target,data); }
}
