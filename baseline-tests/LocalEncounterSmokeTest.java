package com.phantasia.baseline;

import com.phantasia.core.data.*;
import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;
import com.phantasia.core.world.*;
import com.phantasia.j2d.engine.*;
import com.phantasia.j2d.screen.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class LocalEncounterSmokeTest {
    private static int checks;
    private static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    private static void same(Object a,Object b,String text){check(Objects.equals(a,b),text+": "+a+" != "+b);}
    private static PlayerCharacter player(String name,int hp){return PlayerCharacter.fromLegacyCreation(name,Job.WIZARD,hp,20,20,20,20,20,1);}
    private static GameSession session(){return new GameSession(List.of(player("Test",200)),PartyLedger.newGame(),new WorldPosition(30,40),0);}
    private static InteriorMap map(){var m=new InteriorMap(7,"Encounter test",InteriorMapType.DUNGEON,12,8,"dungeon_standard");m.addEntrance(new MapEntrance("entry",new WorldPosition(2,2),Direction.EAST));m.setDefaultEntranceId("entry");m.getSettings().setEncounterInterval(2);return m;}
    private static LocalMapTraversal traversal(InteriorMap m,GameSession s){var r=new InteriorMapRegistry();r.register(m);return new LocalMapTraversal(r,s,7,null);}
    private static Monster monster(){return EncounterFactory.buildMonster("Test creature",12,1,20);}
    public static void main(String[] args){pacing();rewards();screens();System.out.println("LOCAL_ENCOUNTERS checks="+checks+" PASS");}
    private static void pacing(){
        var s=session();var m=map();var t=traversal(m,s);var calls=new AtomicInteger();
        var flow=new LocalEncounterFlow(t,s,()->{calls.incrementAndGet();return List.of(monster());});
        m.getSettings().setEncountersEnabled(false);
        for(int i=0;i<4;i++)flow.afterStep(t.move(i%2==0?Direction.EAST:Direction.WEST));
        same(0,calls.get(),"peaceful maps never roll encounters");m.getSettings().setEncountersEnabled(true);
        var step=t.move(Direction.EAST);same(null,flow.afterStep(step),"first pace below interval");same(null,flow.afterStep(step),"replaying same pace does not count");
        m.setTile(4,2,1);same(null,flow.afterStep(t.move(Direction.EAST)),"wall bump does not count");m.setTile(4,2,0);
        var battle=flow.afterStep(t.move(Direction.EAST));check(battle!=null,"successful movement reaches interval");same(1,calls.get(),"factory called once");check(t.encounterSuspended(),"traversal suspended during battle");
        var pos=t.position();same(LocalMapTraversal.Kind.BLOCKED,t.move(Direction.SOUTH).kind(),"cannot move behind combat UI");same(pos,t.position(),"suspension preserves position");
        same(null,flow.afterStep(step),"no second encounter while active");
        try{battle.victory();throw new AssertionError("live enemy rewarded");}catch(IllegalStateException expected){check(true,"victory requires defeated enemies");}
        battle.escape();check(!t.encounterSuspended(),"escape resumes traversal");same(400,s.getLedger().getPartyGold(),"escape has no gold");same(0,s.getParty().get(0).getExperience(),"escape has no XP");
        same(null,flow.afterStep(t.move(Direction.SOUTH)),"breathing room after escape");var second=flow.afterStep(t.move(Direction.EAST));check(second!=null,"later paces can trigger next battle");
        battle.escape();same(second,flow.active(),"stale escape cannot clear newer battle");
        second.enemies().get(0).setHp(0);var receipt=second.victory();check(receipt.goldClaimed(),"victory claims rewards");check(t.encounterSuspended(),"victory waits for result acknowledgement");
        int gold=s.getLedger().getPartyGold(),xp=s.getParty().get(0).getExperience();second.victory();same(gold,s.getLedger().getPartyGold(),"repeated win no gold duplication");same(xp,s.getParty().get(0).getExperience(),"repeated win no XP duplication");second.resumeVictory();check(!t.encounterSuspended(),"acknowledgement resumes traversal");
        try{second.escape();throw new AssertionError("victory changed to escape");}catch(IllegalStateException expected){check(true,"terminal outcome cannot change");}
        same(null,flow.afterStep(t.move(Direction.SOUTH)),"no instant refight after victory");
        var lost=flow.afterStep(t.move(Direction.WEST));check(lost!=null,"third battle begins");s.getParty().get(0).setHp(0);lost.defeat();same(LocalMapTraversal.Kind.BLOCKED,t.move(Direction.WEST).kind(),"defeated party cannot explore");
        same(gold,s.getLedger().getPartyGold(),"defeat gives no gold");
    }
    private static void rewards(){
        var s=session();var survivor=s.getParty().get(0);var dead=player("Dead",0);s.getParty().add(dead);
        var enemy=EncounterFactory.buildMonster("Reward test",1,1,3000);var rewards=new CombatRewards(s,List.of(enemy));
        enemy.setHp(0);var receipt=rewards.victory();same(3000,receipt.experience(),"reward pool captured");same(3000,survivor.getExperience(),"XP goes to surviving members");same(0,dead.getExperience(),"dead member receives no XP");same(1,survivor.getStat(Stat.LEVEL),"XP does not auto-level outside guild");
        same(1900,s.getLedger().getPartyGold(),"treasure granted");rewards.victory();same(1900,s.getLedger().getPartyGold(),"reward receipt idempotent");
        var pendingSession=session();pendingSession.getLedger().addGold(Integer.MAX_VALUE-400);var pendingEnemy=monster();var pending=new CombatRewards(pendingSession,List.of(pendingEnemy));pendingEnemy.setHp(0);
        check(!pending.victory().goldClaimed(),"full purse defers gold");same(20,pendingSession.getParty().get(0).getExperience(),"XP awarded while gold pending");pending.victory();same(20,pendingSession.getParty().get(0).getExperience(),"pending retry does not repeat XP");same(1,pendingSession.getPendingCombatRewards().size(),"pending receipt queued once");
        pendingSession.getLedger().spendGold(10);var flow=new LocalEncounterFlow(traversal(map(),pendingSession),pendingSession,()->List.of(monster()));same(1,flow.retryPendingRewards(),"pending gold claimed after room is made");same(Integer.MAX_VALUE,pendingSession.getLedger().getPartyGold(),"deferred gold restored without overflow");check(pendingSession.getPendingCombatRewards().isEmpty(),"successful receipt removed");same(0,flow.retryPendingRewards(),"deferred reward does not repeat");
        var reentrant=session();var e=monster();var reward=new CombatRewards(reentrant,List.of(e));e.setHp(0);var notifications=new AtomicInteger();
        var subscription=GameEventBus.get().subscribe(event->{if(event instanceof GameEvent.PartyGoldChanged){notifications.incrementAndGet();reward.victory();}});
        try{reward.victory();}finally{subscription.cancel();}same(1,notifications.get(),"gold notification cannot re-award");same(20,reentrant.getParty().get(0).getExperience(),"notification no duplicate XP");same(410,reentrant.getLedger().getPartyGold(),"notification no duplicate gold");
    }
    private static final class Capture implements Screen {
        Object data;public void onEnter(Object d){data=d;}public void onExit(){}public void update(float dt){}public void render(Graphics2D g){}public void onKeyPressed(KeyEvent e){}public void onKeyReleased(KeyEvent e){}public void onMouseClicked(MouseEvent e){}
    }
    private static void advance(ScreenManager manager){manager.update(.31f);manager.update(.31f);}
    private static void key(ScreenManager manager,int code){manager.getActiveScreen().onKeyPressed(new KeyEvent(new Canvas(),KeyEvent.KEY_PRESSED,0,0,code,KeyEvent.CHAR_UNDEFINED));}
    private static void render(ScreenManager manager){var image=new BufferedImage(GameCanvas.DESIGN_WIDTH,GameCanvas.DESIGN_HEIGHT,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();try{manager.render(g,image.getWidth(),image.getHeight());check(true,"battle screen renders headlessly");}finally{g.dispose();}}
    private static void screens(){
        var s=session();var m=map();m.getSettings().setEncounterInterval(1);var t=traversal(m,s);var enemies=new ArrayList<Monster>();var factory=(java.util.function.Supplier<List<Monster>>)()->{var e=monster();enemies.add(e);return List.of(e);};
        var flow=new LocalEncounterFlow(t,s,factory);var visit=new LocalMapScreen.Visit(t,GameState.WORLD_ROAM,flow);var manager=new ScreenManager();var splash=new Capture();var world=new Capture();
        manager.register(GameState.LOCAL_MAP,new LocalMapScreen(manager,s,new NpcRegistry(),new ShopRegistry(),factory));manager.register(GameState.WORLD_ROAM,world);manager.register(GameState.ENCOUNTER_SPLASH,splash);
        manager.switchTo(GameState.LOCAL_MAP,visit);key(manager,KeyEvent.VK_RIGHT);manager.update(.2f);advance(manager);
        same(GameState.ENCOUNTER_SPLASH,manager.getActiveState(),"screen movement triggers encounter handoff");var data=(EncounterSplashScreen.EncounterData)splash.data;check(data.continuation().isLocal(),"local continuation attached");var origin=t.position();var facing=t.facing();
        // First planning round forwards the exact continuation to execution.
        var capture=new Capture();manager.register(GameState.COMBAT_PLANNING,new CombatPlanningScreen(manager,new SpellFactory()));manager.register(GameState.COMBAT_EXECUTION,capture);manager.switchTo(GameState.COMBAT_PLANNING,data);render(manager);key(manager,KeyEvent.VK_E);advance(manager);
        var executed=(CombatExecutionScreen.ExecutionData)capture.data;same(data.continuation(),executed.continuation(),"planning forwards original route");
        // Replay an ongoing round to verify subsequent-round routing independently of dice.
        manager.register(GameState.COMBAT_EXECUTION,new CombatExecutionScreen(manager));manager.register(GameState.COMBAT_PLANNING,capture);
        var ongoing=new CombatExecutionScreen.ExecutionData(s.getParty(),data.enemies(),new RoundResult(1,List.of(),CombatOutcome.ONGOING),new CombatNarrator(),executed.manager(),1,s,data.continuation());
        manager.switchTo(GameState.COMBAT_EXECUTION,ongoing);render(manager);key(manager,KeyEvent.VK_ENTER);key(manager,KeyEvent.VK_ENTER);advance(manager);
        same(data.continuation(),((CombatPlanningScreen.PlanningResult)capture.data).continuation(),"subsequent round retains route");
        // Defeat the actual referenced enemies, then show the real result and victory screens.
        data.enemies().forEach(e->e.setHp(0));var victory=new CombatExecutionScreen.ExecutionData(s.getParty(),data.enemies(),new RoundResult(2,List.of(),CombatOutcome.VICTORY),new CombatNarrator(),executed.manager(),2,s,data.continuation());
        manager.register(GameState.VICTORY,new VictoryScreen(manager));manager.switchTo(GameState.COMBAT_EXECUTION,victory);key(manager,KeyEvent.VK_ENTER);key(manager,KeyEvent.VK_ENTER);advance(manager);same(GameState.VICTORY,manager.getActiveState(),"execution reaches victory");render(manager);
        int gold=s.getLedger().getPartyGold(),xp=s.getParty().get(0).getExperience();manager.switchTo(GameState.VICTORY,victory);same(gold,s.getLedger().getPartyGold(),"victory re-entry no extra gold");same(xp,s.getParty().get(0).getExperience(),"victory re-entry no extra XP");
        manager.update(.6f);key(manager,KeyEvent.VK_ENTER);advance(manager);same(GameState.LOCAL_MAP,manager.getActiveState(),"victory returns to local map");same(origin,t.position(),"exact map position retained");same(facing,t.facing(),"facing retained");same(new WorldPosition(30,40),s.getWorldPosition(),"world anchor retained");check(t.isExplored(origin.x(),origin.y()),"fog memory retained");check(!t.encounterSuspended(),"combat suspension released");
        // Escape and defeat use the same route without rewards.
        var fleeBattle=flow.afterStep(t.move(Direction.SOUTH));var fleeRoute=new BattleContinuation(fleeBattle,visit);var escape=new CombatExecutionScreen.ExecutionData(s.getParty(),fleeBattle.enemies(),new RoundResult(1,List.of(),CombatOutcome.ESCAPED),new CombatNarrator(),null,1,s,fleeRoute);
        manager.switchTo(GameState.COMBAT_EXECUTION,escape);key(manager,KeyEvent.VK_ENTER);key(manager,KeyEvent.VK_ENTER);advance(manager);same(GameState.LOCAL_MAP,manager.getActiveState(),"escape returns locally");same(gold,s.getLedger().getPartyGold(),"escape has no rewards");
        var lost=flow.afterStep(t.move(Direction.WEST));var defeatRoute=new BattleContinuation(lost,visit);s.getParty().forEach(p->p.setHp(0));var defeat=new CombatExecutionScreen.ExecutionData(s.getParty(),lost.enemies(),new RoundResult(1,List.of(),CombatOutcome.DEFEAT),new CombatNarrator(),null,1,s,defeatRoute);
        var quit=new AtomicInteger();manager.register(GameState.DEFEAT,new DefeatScreen(manager,quit::incrementAndGet));manager.switchTo(GameState.DEFEAT,defeat);render(manager);manager.update(2f);key(manager,KeyEvent.VK_ENTER);advance(manager);same(GameState.LOCAL_MAP,manager.getActiveState(),"defeat returns to local fallen party");same(0,quit.get(),"local defeat does not quit diagnostic");same(LocalMapTraversal.Kind.BLOCKED,t.move(Direction.SOUTH).kind(),"fallen party cannot continue exploring");
    }
}
