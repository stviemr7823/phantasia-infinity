package com.phantasia.baseline;

import com.phantasia.core.data.*;
import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;
import com.phantasia.j2d.diagnostic.*;
import com.phantasia.j2d.engine.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public final class NativeProgressionSmokeTest {
    private static int checks;
    private static void check(boolean ok, String text) { checks++; if (!ok) throw new AssertionError(text); }
    private static void same(Object a, Object b, String text) { check(Objects.equals(a,b), text + ": " + a + " != " + b); }
    private static void rejects(Runnable action, String text) {
        try { action.run(); throw new AssertionError(text); }
        catch (IllegalArgumentException | IllegalStateException expected) { check(true,text); }
    }
    private static PlayerStateV5 player(String name, long id) { return PlayerCreationV5.create(name,id,Job.FIGHTER,0,20,20,15,100,10); }
    private static List<NativeCampaignV5.TrainingStep> curriculum() { return List.of(
            new NativeCampaignV5.TrainingStep(Job.FIGHTER,2,20,10,4,1,List.of(Action.THRUST)),
            new NativeCampaignV5.TrainingStep(Job.FIGHTER,3,40,20,4,1,List.of(Action.SLASH))); }
    private static NativeCampaignV5 campaign(int gold) { return new NativeCampaignV5(List.of(player("Native A",1),player("Native B",2)),gold,1,curriculum()); }
    private static Monster enemy() { return EncounterFactory.buildMonster("Native foe",12,1,40); }
    public static void main(String[] args) throws Exception { training(); lifecycle(); boundaries(); targeting(); screen(); System.out.println("NATIVE_PROGRESSION checks="+checks+" PASS"); }
    private static void targeting() {
        var c=campaign(0); var a=c.party().get(0);var b=c.party().get(1);var first=enemy();var second=enemy();
        var battle=c.startBattle(List.of(first,second),ignored->10);
        battle.select(a,Action.ATTACK,null);same(first,battle.rows().get(0).order().enemyTarget(),"automatic target chooses first reachable living foe");
        battle.select(a,Action.ATTACK,second);same(second,battle.rows().get(0).order().enemyTarget(),"optional override chooses another encounter foe");
        battle.select(b,Action.PARRY,null);second.setHp(0);check(!battle.canConfirm(),"defeated override requires review before next confirmation");
        rejects(battle::confirmRound,"stale target cannot execute");battle.select(a,Action.ATTACK,null);check(battle.canConfirm(),"automatic retry produces complete plan");same(first,battle.rows().get(0).order().enemyTarget(),"automatic retry selects remaining foe");
        rejects(()->battle.select(a,Action.RUN,null),"unsupported basic command cannot bypass technique gate");
    }
    private static void training() {
        var input=player("Source",1); var c=new NativeCampaignV5(List.of(input),40,1,curriculum()); var m=c.party().get(0);
        input.setHp(0); same(100,m.runtime().hp(),"campaign owns a detached native record");
        check(m.learned().isEmpty(),"fresh player has no trained techniques"); check(!c.canTrain(m),"XP required");
        var before=m.runtime().snapshot().toBytes(); rejects(()->c.train(m),"unqualified guild training rejected");
        check(Arrays.equals(before,m.runtime().snapshot().toBytes()),"failed training leaves native record unchanged"); same(40L,c.gold(),"failed training does not spend gold");
        rejects(()->c.ready(m,List.of(Action.THRUST)),"cannot ready unlearned move");
        m.runtime().awardExperience(40); same(1,m.level(),"XP cannot auto-level"); check(m.learned().isEmpty(),"XP cannot auto-learn");
        var trained=c.train(m); same(2,trained.level(),"guild advances one level"); same(30L,c.gold(),"training charges once");
        same(104,m.runtime().maxHp(),"training grows max HP"); same(104,m.runtime().hp(),"training grows current HP"); same(11,m.runtime().maxMp(),"training grows MP");
        same(List.of(Action.THRUST),trained.learned(),"technique learned at level-up"); check(m.readied().isEmpty(),"learning does not silently replace preparation");
        c.ready(m,List.of(Action.THRUST)); same(List.of(Action.THRUST),m.readied(),"learned move can be readied outside combat");
        rejects(()->c.ready(m,List.of(Action.THRUST,Action.THRUST)),"duplicate readied move rejected"); same(List.of(Action.THRUST),m.readied(),"invalid replacement is atomic");
        c.train(m); same(3,m.level(),"second training advances one more level"); check(m.learned().contains(Action.SLASH),"second curriculum unlock");
        rejects(()->c.ready(m,List.of(Action.THRUST,Action.SLASH)),"slot limit enforced");
        c.ready(m,List.of(Action.SLASH)); same(Set.of(Action.THRUST,Action.SLASH),m.learned(),"reconfiguration retains learned moves");
        c.ready(m,List.of()); check(m.readied().isEmpty(),"preparation may be cleared");
        same(null,c.nextTraining(m),"no invented next-level table"); rejects(()->c.train(m),"undefined progression stops explicitly");
    }
    private static void lifecycle() {
        var c=campaign(40); var a=c.party().get(0); var b=c.party().get(1); var e=enemy(); var battle=c.startBattle(List.of(e),ignored->10);
        check(!battle.canConfirm(),"all living members need orders"); rejects(battle::confirmRound,"missing orders cannot run");
        rejects(battle::settle,"ongoing battle cannot award"); rejects(()->c.train(a),"no training in battle"); rejects(()->c.ready(a,List.of()),"no reconfiguration in battle");
        rejects(()->c.startBattle(List.of(enemy()),ignored->10),"no overlapping battle");
        rejects(()->battle.select(a,Action.THRUST,null),"unreadied move cannot enter plan");
        battle.select(a,Action.PARRY,null); check(!battle.canConfirm(),"partial party plan incomplete"); battle.select(b,Action.PARRY,null); check(battle.canConfirm(),"complete plan ready");
        var round=battle.confirmRound(); var header=(CombatEvent.RoundHeader)round.events().get(0);
        same(List.of("Native A","Native B","Native foe"),header.initiativeOrder(),"Agility with stable ties"); same(CombatOutcome.ONGOING,round.outcome(),"defending does not damage enemy");
        battle.select(a,Action.ATTACK,e); same(Action.ATTACK,battle.rows().get(0).order().action(),"explicit target override accepted");
        rejects(()->battle.select(a,Action.ATTACK,enemy()),"foreign target rejected"); same(e,battle.rows().get(0).order().enemyTarget(),"failed target keeps valid plan");
        e.setHp(0); var reward=battle.settle(); same(40L,reward.experiencePool(),"XP pool captured at start"); same(20L,a.runtime().experience(),"living survivor share"); same(20L,b.runtime().experience(),"other survivor share");
        same(60L,c.gold(),"native campaign receives battle gold"); same(1,a.level(),"battle XP does not level"); check(a.learned().isEmpty(),"victory does not grant techniques");
        same(reward,battle.settle(),"same receipt on result re-entry"); same(60L,c.gold(),"no duplicate gold"); same(20L,a.runtime().experience(),"no duplicate XP");
        rejects(()->c.train(a),"terminal result still owns preparation until acknowledged"); battle.acknowledge(); same(null,c.activeBattle(),"acknowledgement returns campaign");
        c.train(a); c.ready(a,List.of(Action.THRUST)); var next=c.startBattle(List.of(enemy()),ignored->10);
        next.select(a,Action.THRUST,null); same(Action.THRUST,next.rows().get(0).order().action(),"learned/readied technique reaches native resolver plan");
        rejects(()->next.select(b,Action.THRUST,null),"learning belongs to individual member");
        battle.acknowledge(); same(next,c.activeBattle(),"old acknowledgement cannot clear new battle"); rejects(()->battle.select(a,Action.ATTACK,null),"stale battle cannot alter orders");
        a.runtime().damage(65535); b.runtime().damage(65535); next.acknowledge(); same(50L,c.gold(),"defeat gives no reward after training cost");
        same(20L,b.runtime().experience(),"defeat no XP"); rejects(()->c.startBattle(List.of(enemy()),ignored->10),"defeated party cannot start another battle");
    }
    private static void boundaries() {
        var poor=campaign(0); var member=poor.party().get(0); member.runtime().awardExperience(20);
        rejects(()->poor.train(member),"training requires gold"); same(1,member.level(),"insufficient gold no partial level"); check(member.learned().isEmpty(),"insufficient gold no partial unlock");
        var max=player("Max vitals",1); max.setMaxHp(65535); max.setHp(65535);
        var capped=new NativeCampaignV5(List.of(max),40,1,curriculum()); var m=capped.party().get(0); m.runtime().awardExperience(20);
        rejects(()->capped.train(m),"vital overflow rejected before commit"); same(40L,capped.gold(),"overflow costs no gold"); same(1,m.level(),"overflow no level");
        var duplicate=player("Duplicate",1); rejects(()->new NativeCampaignV5(List.of(duplicate,duplicate),0,1,curriculum()),"duplicate player object rejected");
        rejects(()->new NativeCampaignV5(List.of(player("A",1),player("B",1)),0,1,curriculum()),"duplicate persistent ID rejected");
        rejects(()->new NativeCampaignV5(List.of(player("A",1)),0,1,List.of(curriculum().get(1))),"curriculum cannot skip level");
        rejects(()->new NativeCampaignV5(List.of(player("A",1)),0,1,List.of(curriculum().get(0),curriculum().get(0))),"duplicate curriculum step rejected");
        rejects(()->new NativeCampaignV5.TrainingStep(Job.FIGHTER,2,20,10,1,1,List.of(Action.RUN)),"unsupported technique rejected");
        var full=campaign(Integer.MAX_VALUE); var e=enemy(); var battle=full.startBattle(List.of(e),ignored->10); e.setHp(0); check(!battle.settle().goldClaimed(),"overflow treasure deferred");
        same(Integer.MAX_VALUE,(int)full.gold(),"purse never wraps"); same(20L,full.pendingGold(),"pending treasure retained"); battle.acknowledge(); full.train(full.party().get(0));
        same(10L,full.pendingGold(),"training space retries deferred gold"); same(Integer.MAX_VALUE,(int)full.gold(),"retry fills purse safely");
        var overflow=campaign(0); var alive=overflow.party().get(0); var dead=overflow.party().get(1); alive.runtime().awardExperience(0xFFFFFFFFL-1); dead.runtime().damage(65535);
        var foe=enemy();var last=overflow.startBattle(List.of(foe),ignored->10);foe.setHp(0);last.settle(); same(0xFFFFFFFFL,alive.runtime().experience(),"XP saturates native unsigned limit"); same(0L,dead.runtime().experience(),"dead member gets no XP");
        var fresh=campaign(0); rejects(()->fresh.startBattle(List.of(enemy()),ignored->256),"enemy Agility validated"); same(null,fresh.activeBattle(),"invalid enemy speed starts no battle");
    }
    private static void key(Screen s,int code) { var source=new Canvas();s.onKeyPressed(new KeyEvent(source,KeyEvent.KEY_PRESSED,0,0,code,KeyEvent.CHAR_UNDEFINED));s.onKeyReleased(new KeyEvent(source,KeyEvent.KEY_RELEASED,0,0,code,KeyEvent.CHAR_UNDEFINED)); }
    private static void render(Screen s) throws Exception { var image=new BufferedImage(GameCanvas.DESIGN_WIDTH,GameCanvas.DESIGN_HEIGHT,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();try{s.render(g);}finally{g.dispose();}check(image.getRGB(80,80)!=0xff000000,"native screen renders"); javax.imageio.ImageIO.write(image,"png",Path.of(System.getProperty("java.io.tmpdir"),"native-progression-preview.png").toFile()); }
    private static void screen() throws Exception {
        var app=new DiagnosticApplication(DiagnosticContent.create(),()->{}); key(app.manager().getActiveScreen(),KeyEvent.VK_V); same(GameState.NATIVE_DIAGNOSTIC,app.manager().getActiveState(),"entry-menu native route");
        var screen=(NativeProgressionScreen)app.manager().getActiveScreen(); var c=screen.campaign(); var original=app.session(); render(screen);
        key(screen,KeyEvent.VK_B); check(c.activeBattle()!=null,"screen starts actual native battle"); check(c.activeBattle().canConfirm(),"default automatic targets produce reviewable plan");render(screen);
        c.activeBattle().enemies().forEach(e->e.setHp(0)); key(screen,KeyEvent.VK_ENTER);same(null,c.activeBattle(),"screen result acknowledges once");
        key(screen,KeyEvent.VK_G); same(2,c.party().get(0).level(),"screen invokes actual guild level-up");key(screen,KeyEvent.VK_1);same(List.of(Action.THRUST),c.party().get(0).readied(),"screen readies actual learned technique");render(screen);
        key(screen,KeyEvent.VK_B);key(screen,KeyEvent.VK_1);same(Action.THRUST,c.activeBattle().rows().get(0).order().action(),"screen readied command submits native order");
        key(screen,KeyEvent.VK_G);same(2,c.party().get(0).level(),"in-battle G cannot train");var source=new Canvas(); screen.onKeyPressed(new KeyEvent(source,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_ENTER,'\n'));
        int hp=c.party().get(0).runtime().hp(), foeHp=c.activeBattle().enemies().get(0).getHp();screen.onKeyPressed(new KeyEvent(source,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_ENTER,'\n'));
        same(hp,c.party().get(0).runtime().hp(),"held confirmation cannot rerun round");same(foeHp,c.activeBattle().enemies().get(0).getHp(),"held confirmation no extra attack");
        screen.onKeyReleased(new KeyEvent(source,KeyEvent.KEY_RELEASED,0,0,KeyEvent.VK_ENTER,'\n'));key(screen,KeyEvent.VK_R);check(c!=screen.campaign(),"native reset discards full native session");
        check(original==app.session(),"native test never mutates legacy session ownership");same(400,original.getLedger().getPartyGold(),"map-test gold independent");
        key(screen,KeyEvent.VK_ESCAPE);same(GameState.WORLD_ROAM,app.manager().getActiveState(),"outside battle returns to menu");
    }
}
