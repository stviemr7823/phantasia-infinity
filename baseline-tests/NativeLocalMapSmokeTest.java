package com.phantasia.baseline;

import com.phantasia.core.data.*;
import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;
import com.phantasia.core.model.item.*;
import com.phantasia.core.world.*;
import com.phantasia.core.world.ServiceData.*;
import com.phantasia.j2d.diagnostic.*;
import com.phantasia.j2d.engine.*;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public final class NativeLocalMapSmokeTest {
    private static int checks;
    private static final QuestFlag DONE=QuestFlag.MISC_FLAG_60;
    private static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    private static void same(Object a,Object b,String text){check(Objects.equals(a,b),text+": "+a+" != "+b);}
    private static void rejects(Runnable action,String text){try{action.run();throw new AssertionError(text);}catch(IllegalArgumentException|IllegalStateException expected){check(true,text);}}
    private static NativeCampaignV5 campaign(){return new NativeCampaignV5(List.of(
            PlayerCreationV5.create("Native A",1,Job.FIGHTER,0,20,20,10,100,10),
            PlayerCreationV5.create("Native B",2,Job.FIGHTER,0,20,18,10,100,10)),40,2,List.of(new NativeCampaignV5.TrainingStep(Job.FIGHTER,2,20,10,4,1,List.of(Action.THRUST))));}
    private static InteriorMap map(){var m=new InteriorMap(7,"Native map",InteriorMapType.DUNGEON,12,8,"dungeon_standard");m.addEntrance(new MapEntrance("entry",new WorldPosition(2,2),Direction.EAST));m.setDefaultEntranceId("entry");m.getSettings().setEncountersEnabled(true);m.getSettings().setEncounterInterval(2);return m;}
    private static LocalMapTraversal traversal(InteriorMap m,NativeCampaignV5 c){var r=new InteriorMapRegistry();r.register(m);return new LocalMapTraversal(r,c,7,null);}
    private static PlacedFeature chest(LootTable loot){return new PlacedFeature(3,2,PlacedFeatureType.CHEST,loot,null,DONE,null);}
    private static Monster enemy(){return EncounterFactory.buildMonster("Native foe",12,1,40);}
    private static final class NoRoll extends Random { @Override public long nextLong(long bound){throw new AssertionError("Unexpected reroll");} }
    public static void main(String[] args)throws Exception{effects();pacing();screenRoute();System.out.println("NATIVE_LOCAL_MAP checks="+checks+" PASS");}
    private static void effects(){
        var c=campaign();var a=c.party().get(0);var b=c.party().get(1);same(10,a.inventorySlots(),"native pack capacity uses v5 schema");
        var reward=chest(new LootTable(5,5).addGuaranteed(101,19));var m=map();m.addFeature(reward);var t=traversal(m,c);
        same(LocalMapTraversal.Kind.FEATURE,t.move(Direction.EAST).kind(),"native traversal reaches chest");var opened=t.interact(reward,new NoRoll());same(LocalFeatureEffects.Status.OPENED,opened.status(),"native chest opens");
        same(45L,c.gold(),"chest gold reaches campaign owner");for(int i=0;i<10;i++)same(101,a.runtime().snapshot().inventory(i),"first native pack slot "+i);
        for(int i=0;i<9;i++)same(101,b.runtime().snapshot().inventory(i),"second native pack slot "+i);same(0,b.getInventoryItemId(9),"one free native slot");
        same(0L,a.runtime().experience(),"inventory cannot corrupt native XP");check(c.hasFlag(DONE),"native flag consumes chest");same(LocalFeatureEffects.Status.UNAVAILABLE,t.interact(reward,new NoRoll()).status(),"native reward cannot repeat");same(45L,c.gold(),"no repeated gold");
        var full=campaign();for(var p:full.party())for(int i=0;i<p.inventorySlots();i++)p.setInventoryItem(i,101);
        var blocked=chest(new LootTable(7,7).addGuaranteed(102,1));var fm=map();fm.addFeature(blocked);var ft=traversal(fm,full);ft.move(Direction.EAST);
        same(LocalFeatureEffects.Status.INVENTORY_FULL,ft.interact(blocked,new NoRoll()).status(),"full native packs reject all transfer");same(40L,full.gold(),"blocked chest no partial gold");check(!full.hasFlag(DONE),"blocked chest not consumed");
        var key=new LocalInteractionState.Key(7,3,2);var pending=full.getLocalInteractions().pending(key);check(pending!=null,"pending native roll retained");
        full.party().get(1).setInventoryItem(9,0);var revisit=traversal(fm,full);revisit.move(Direction.EAST);same(LocalFeatureEffects.Status.OPENED,revisit.interact(blocked,new NoRoll()).status(),"revisit grants retained native roll");same(102,full.party().get(1).getInventoryItemId(9),"tenth native slot receives retry");same(47L,full.gold(),"cached gold granted once");
        var trap=new PlacedFeature(4,2,PlacedFeatureType.TRAP,new TrapData(3,3,"Native trap",0),null,QuestFlag.MISC_FLAG_59,null);m.addFeature(trap);t.move(Direction.EAST);var hit=t.interact(trap,new NoRoll());same(LocalFeatureEffects.Status.TRIGGERED,hit.status(),"native trap triggers");same(97,a.runtime().hp(),"trap mutates battle runtime directly");same(97,b.runtime().hp(),"trap damages both native members");same(a,hit.damage().get(0).member(),"damage receipt refers to native owner");
        var detect=new PlacedFeature(5,2,PlacedFeatureType.TRAP,new TrapData(20,20,"Avoided",20));m.addFeature(detect);t.move(Direction.EAST);same(LocalFeatureEffects.Status.AVOIDED,t.interact(detect,new NoRoll()).status(),"trap detection reads native Agility");same(97,a.runtime().hp(),"avoided trap leaves vitals");
        var door=new PlacedFeature(6,2,PlacedFeatureType.LOCKED_DOOR,LockData.byItem(102,"Need key"),null,QuestFlag.MISC_FLAG_58,null);m.addFeature(door);b.setInventoryItem(9,102);same(LocalMapTraversal.Kind.MOVED,t.move(Direction.EAST).kind(),"key in tenth slot unlocks native door");check(c.hasFlag(QuestFlag.MISC_FLAG_58),"native lock completion set");
        var overflow=campaign();overflow.addGold(Integer.MAX_VALUE-40);same(LocalFeatureEffects.Status.GOLD_LIMIT,LocalFeatureEffects.apply(overflow,7,chest(new LootTable(1,1).addGuaranteed(101,1)),new NoRoll()).status(),"native chest gold limit atomic");same(0,overflow.party().get(0).getInventoryItemId(0),"overflow chest grants no item");
        a.setHp(0);b.setHp(0);same(LocalMapTraversal.Kind.BLOCKED,t.move(Direction.SOUTH).kind(),"native defeated party cannot explore");same(LocalFeatureEffects.Status.DEFEATED,t.interact(door,new NoRoll()).status(),"native defeat prevents effects");
    }
    private static void pacing(){
        var c=campaign();c.setPosition(new WorldPosition(30,40));var m=map();var t=traversal(m,c);var flow=new NativeLocalEncounterFlow(t,c,()->List.of(enemy()),ignored->10);
        rejects(()->new NativeLocalEncounterFlow(t,campaign(),()->List.of(enemy()),ignored->10),"mismatched native traversal owner rejected");
        var first=t.move(Direction.EAST);same(null,flow.afterStep(first),"native first pace below interval");same(null,flow.afterStep(first),"duplicate movement notification ignored");
        var battle=flow.afterStep(t.move(Direction.EAST));check(battle==c.activeBattle(),"encounter belongs to the exploration campaign");var position=t.position();var facing=t.facing();
        same(LocalMapTraversal.Kind.BLOCKED,t.move(Direction.SOUTH).kind(),"native combat suspends map movement");rejects(()->flow.returnFromBattle(battle),"ongoing battle cannot release traversal");check(t.encounterSuspended(),"failed return preserves suspension");
        battle.enemies().get(0).setHp(0);battle.settle();flow.returnFromBattle(battle);same(position,t.position(),"native return keeps exact position");same(facing,t.facing(),"native return keeps facing");same(new WorldPosition(30,40),c.getWorldPosition(),"native return keeps world anchor");check(t.isExplored(position.x(),position.y()),"native return keeps fog");same(20L,c.party().get(0).runtime().experience(),"native encounter reward reaches exploration member");
        same(null,flow.afterStep(t.move(Direction.SOUTH)),"native pacing resets after result");var newer=flow.afterStep(t.move(Direction.EAST));check(newer!=null,"later movement starts next native battle");rejects(()->flow.returnFromBattle(battle),"stale local result cannot release new encounter");same(newer,flow.active(),"new native battle retained");
        for(var p:c.party())p.setHp(0);flow.returnFromBattle(newer);check(!t.encounterSuspended(),"defeat releases combat suspension");same(LocalMapTraversal.Kind.BLOCKED,t.move(Direction.SOUTH).kind(),"defeat still blocks exploration");
    }
    private static void key(DiagnosticApplication app,int key){var source=new Canvas();var screen=app.manager().getActiveScreen();screen.onKeyPressed(new KeyEvent(source,KeyEvent.KEY_PRESSED,0,0,key,KeyEvent.CHAR_UNDEFINED));app.manager().update(.2f);app.manager().update(.2f);app.manager().getActiveScreen().onKeyReleased(new KeyEvent(source,KeyEvent.KEY_RELEASED,0,0,key,KeyEvent.CHAR_UNDEFINED));}
    private static void render(DiagnosticApplication app,String name)throws Exception{var image=new BufferedImage(GameCanvas.DESIGN_WIDTH,GameCanvas.DESIGN_HEIGHT,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();try{app.manager().render(g,image.getWidth(),image.getHeight());}finally{g.dispose();}check(image.getRGB(80,80)!=0xff000000,"native route renders "+name);javax.imageio.ImageIO.write(image,"png",java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),"native-map-"+name+".png").toFile());}
    private static void screenRoute()throws Exception{
        var app=new DiagnosticApplication(DiagnosticContent.create(),()->{});var legacy=app.session();key(app,KeyEvent.VK_N);same(GameState.NATIVE_LOCAL_MAP,app.manager().getActiveState(),"N opens authored native maps");var c=app.nativeCampaign();check(c==app.nativeTraversal().session(),"map session is exact native campaign");render(app,"area");
        var t=app.nativeTraversal();walk(t,c,7,8);key(app,KeyEvent.VK_RIGHT);same(GameState.DIALOGUE,app.manager().getActiveState(),"native guide uses common dialogue screen");check(c.hasFlag(DiagnosticContent.PERMISSION),"dialogue grants native campaign flag");check(!legacy.hasFlag(DiagnosticContent.PERMISSION),"native dialogue cannot alter legacy flags");key(app,KeyEvent.VK_ENTER);if(app.manager().getActiveState()==GameState.DIALOGUE)key(app,KeyEvent.VK_ENTER);
        walk(t,c,10,1);same(DiagnosticContent.COURTYARD,t.map().getId(),"native map link enters courtyard");walk(t,c,8,6);check(c.hasFlag(DiagnosticContent.GATE_OPEN),"native dialogue permission opens authored gate");walk(t,c,8,1);same(DiagnosticContent.HALL,t.map().getId(),"native link enters castle hall");check(t.visibleNpcAt(6,3)!=null,"native flags reveal conditional NPC");walk(t,c,6,8);same(DiagnosticContent.COURTYARD,t.map().getId(),"native hall return");walk(t,c,8,10);same(DiagnosticContent.SETTLEMENT,t.map().getId(),"native courtyard returns settlement");
        walk(t,c,5,6);key(app,KeyEvent.VK_RIGHT);same(GameState.SHOP,app.manager().getActiveState(),"native merchant uses common shop screen");
        long gold=c.gold();key(app,KeyEvent.VK_ENTER);same(gold-ItemTable.get(101).gold(),c.gold(),"shop charges native purse");same(101,c.party().get(0).runtime().snapshot().inventory(0),"shop writes native inventory");same(400,legacy.getLedger().getPartyGold(),"native shop never charges legacy purse");key(app,KeyEvent.VK_ESCAPE);
        app.enterNative(DiagnosticContent.DUNGEON);t=app.nativeTraversal();for(int i=0;i<6;i++)key(app,KeyEvent.VK_RIGHT);same(GameState.NATIVE_DIAGNOSTIC,app.manager().getActiveState(),"dungeon movement opens native battle screen");check(c==((NativeProgressionScreen)app.manager().getActiveScreen()).campaign(),"battle screen has exact exploration owner");
        var pos=t.position();var facing=t.facing();render(app,"battle");var battle=c.activeBattle();battle.enemies().forEach(e->e.setHp(0));key(app,KeyEvent.VK_ENTER);same(GameState.NATIVE_LOCAL_MAP,app.manager().getActiveState(),"victory returns to native authored area");same(pos,t.position(),"screen return retains local position");same(facing,t.facing(),"screen return retains facing");same(20L,c.party().get(0).runtime().experience(),"screen battle awards native XP");
        app.enterNative(DiagnosticContent.SETTLEMENT);t=app.nativeTraversal();walk(t,c,6,9);key(app,KeyEvent.VK_DOWN);same(GameState.NATIVE_DIAGNOSTIC,app.manager().getActiveState(),"trainer opens native guild context");key(app,KeyEvent.VK_G);same(2,c.party().get(0).level(),"actual mapped trainer levels native member");check(c.party().get(0).learned().contains(Action.THRUST),"mapped guild teaches technique");key(app,KeyEvent.VK_1);same(List.of(Action.THRUST),c.party().get(0).readied(),"mapped preparation readies learned technique");render(app,"guild");key(app,KeyEvent.VK_ESCAPE);same(GameState.NATIVE_LOCAL_MAP,app.manager().getActiveState(),"guild returns to native map");same(new WorldPosition(6,9),t.position(),"guild preserves visit position");
        key(app,KeyEvent.VK_P);same(GameState.NATIVE_DIAGNOSTIC,app.manager().getActiveState(),"field preparation anywhere outside battle");key(app,KeyEvent.VK_G);same(2,c.party().get(0).level(),"field preparation cannot act as guild");key(app,KeyEvent.VK_R);check(c==app.nativeCampaign(),"field reset cannot orphan visit owner");key(app,KeyEvent.VK_ESCAPE);
        app.enterNative(DiagnosticContent.DUNGEON);for(int i=0;i<6;i++)key(app,KeyEvent.VK_RIGHT);key(app,KeyEvent.VK_1);same(Action.THRUST,c.activeBattle().rows().get(0).order().action(),"mapped battle offers same member's learned/readied move");var next=c.activeBattle();next.enemies().forEach(e->e.setHp(0));key(app,KeyEvent.VK_ENTER);same(40L,c.party().get(0).runtime().experience(),"second mapped reward updates same member");same(2,c.party().get(0).level(),"second reward does not auto-level");
        app.enterNative(DiagnosticContent.SETTLEMENT);t=app.nativeTraversal();walk(t,c,2,6);key(app,KeyEvent.VK_LEFT);app.manager().update(.31f);app.manager().update(.31f);same(GameState.WORLD_ROAM,app.manager().getActiveState(),"native world exit returns entry menu");same(new WorldPosition(0,0),c.getWorldPosition(),"native world exit preserves anchor");key(app,KeyEvent.VK_V);check(c==((NativeProgressionScreen)app.manager().getActiveScreen()).campaign(),"standalone preparation shares mapped native campaign");same(40L,c.party().get(0).runtime().experience(),"return to menu preserves native progress");key(app,KeyEvent.VK_ESCAPE);key(app,KeyEvent.VK_N);
        key(app,KeyEvent.VK_ESCAPE);same(GameState.PAUSE,app.manager().getActiveState(),"native map pause available");key(app,KeyEvent.VK_R);check(c!=app.nativeCampaign(),"whole-session reset replaces native owner");check(app.nativeTraversal()==null,"reset discards native visit");check(!app.nativeCampaign().hasFlag(DiagnosticContent.PERMISSION),"reset clears native flags");same(40L,app.nativeCampaign().gold(),"reset restores native test gold");
    }
    /** Fixture route setup uses shared traversal; screen steps above exercise actual pacing. */
    private static void walk(LocalMapTraversal t,NativeCampaignV5 c,int tx,int ty){
        var map=t.map();var start=t.position();var goal=new WorldPosition(tx,ty);var queue=new ArrayDeque<WorldPosition>();var paths=new HashMap<WorldPosition,List<Direction>>();queue.add(start);paths.put(start,List.of());
        while(!queue.isEmpty()&&!paths.containsKey(goal)){var p=queue.remove();for(Direction d:Direction.values()){
            int dx=d==Direction.EAST?1:d==Direction.WEST?-1:0,dy=d==Direction.SOUTH?1:d==Direction.NORTH?-1:0;var n=new WorldPosition(p.x()+dx,p.y()+dy);if(!map.inBounds(n.x(),n.y())||paths.containsKey(n))continue;
            var f=map.getFeatureAt(n.x(),n.y());var tile=AuthoredContentValidator.tileSet(map.getTileSetId()).getEntry(map.getTile(n.x(),n.y()));boolean door=f!=null&&f.featureType()==PlacedFeatureType.LOCKED_DOOR;
            if(!n.equals(goal)&&(t.visibleNpcAt(n.x(),n.y())!=null||f!=null&&!door))continue;if(!tile.passable()&&!(door&&c.hasFlag(DiagnosticContent.PERMISSION)))continue;
            var path=new ArrayList<>(paths.get(p));path.add(d);paths.put(n,path);queue.add(n);
        }}if(!paths.containsKey(goal))throw new AssertionError("No route to "+goal);for(var d:paths.get(goal))t.move(d);
    }
}
