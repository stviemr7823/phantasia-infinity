package com.phantasia.j2d.screen;

import com.phantasia.core.data.*;
import com.phantasia.core.logic.Direction;
import com.phantasia.core.logic.LocalMapTraversal;
import com.phantasia.core.logic.LocalFeatureEffects;
import com.phantasia.core.model.item.Inventory;
import com.phantasia.core.model.item.ItemTable;
import com.phantasia.core.model.*;
import com.phantasia.core.world.*;
import com.phantasia.j2d.engine.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Placeholder 2D presentation shared by all authored local areas. */
public final class LocalMapScreen implements Screen {
    public record Visit(LocalMapTraversal traversal, GameState returnState, com.phantasia.core.logic.LocalEncounterFlow encounters) {
        public Visit(LocalMapTraversal traversal, GameState returnState) { this(traversal, returnState, null); }
    }
    private final ScreenManager manager;
    private final GameSession session;
    private final NpcRegistry npcs;
    private final ShopRegistry shops;
    private final java.util.function.Supplier<List<Monster>> encounterFactory;
    private final Set<Integer> held = new HashSet<>();
    private Visit visit;
    private float cooldown;
    private String message = "";
    private static final int CELL = 48;

    public LocalMapScreen(ScreenManager manager, GameSession session, NpcRegistry npcs, ShopRegistry shops) {
        this(manager, session, npcs, shops, () -> EncounterFactory.generateEncounter(session.getParty()));
    }
    public LocalMapScreen(ScreenManager manager, GameSession session, NpcRegistry npcs, ShopRegistry shops,
                          java.util.function.Supplier<List<Monster>> encounterFactory) {
        this.manager = manager; this.session = session; this.npcs = npcs; this.shops = shops; this.encounterFactory = encounterFactory;
    }
    @Override public void onEnter(Object data) {
        var incoming = (Visit) data;
        visit = incoming.encounters() == null ? new Visit(incoming.traversal(), incoming.returnState(),
                new com.phantasia.core.logic.LocalEncounterFlow(incoming.traversal(), session, encounterFactory)) : incoming;
        held.clear(); cooldown = 0;
        message = visit.encounters().retryPendingRewards() > 0 ? "Pending battle gold received." : "";
    }
    @Override public void onExit() { held.clear(); }
    @Override public void onKeyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) manager.push(GameState.PAUSE, null);
        else held.add(e.getKeyCode());
    }
    @Override public void onKeyReleased(KeyEvent e) { held.remove(e.getKeyCode()); }
    @Override public void onMouseClicked(MouseEvent e) {}
    @Override public void update(float dt) {
        if (LocalFeatureEffects.partyDefeated(session)) {
            held.clear(); message = "The party has fallen. Esc: pause."; return;
        }
        if (cooldown > 0) { cooldown = Math.max(0, cooldown - dt); return; }
        Direction direction = held.contains(KeyEvent.VK_W) || held.contains(KeyEvent.VK_UP) ? Direction.NORTH
                : held.contains(KeyEvent.VK_S) || held.contains(KeyEvent.VK_DOWN) ? Direction.SOUTH
                : held.contains(KeyEvent.VK_A) || held.contains(KeyEvent.VK_LEFT) ? Direction.WEST
                : held.contains(KeyEvent.VK_D) || held.contains(KeyEvent.VK_RIGHT) ? Direction.EAST : null;
        if (direction == null) return;
        cooldown = 0.13f;
        var result = visit.traversal().move(direction); message = result.message();
        switch (result.kind()) {
            case NPC -> interactNpc(result.npc());
            case FEATURE -> interactFeature(result.feature(), result.message());
            case CHANGED_MAP -> held.clear(); // arrival never repeats a held input or triggers its own tile
            case EXIT -> manager.fadeTo(visit.returnState(), null);
            default -> {}
        }
        if (result.kind() == LocalMapTraversal.Kind.MOVED) {
            try {
                var battle = visit.encounters().afterStep(result);
                if (battle != null) {
                    held.clear();
                    manager.fadeTo(GameState.ENCOUNTER_SPLASH, new EncounterSplashScreen.EncounterData(
                            battle.enemies(), com.phantasia.core.logic.EncounterCondition.NORMAL, session,
                            new BattleContinuation(battle, visit)));
                }
            } catch (IllegalArgumentException ex) { message = "Cannot start encounter: " + ex.getMessage(); }
        }
    }
    private void interactNpc(PlacedNpc placed) {
        NpcDefinition npc = npcs == null ? null : npcs.get(placed.npcId());
        if (npc == null) { message = "Missing NPC definition #" + placed.npcId(); return; }
        switch (npc.getRole()) {
            case MERCHANT -> openShop(shops == null ? null : shops.get(npc.getShopId()));
            case INNKEEPER -> manager.push(GameState.INN, new InnOverlay.InnData(npc.getName() + "'s Inn", session));
            case TRAINER -> manager.push(GameState.GUILD, new GuildOverlay.GuildData(npc.getName() + "'s Guild", session));
            case BANKER -> manager.push(GameState.BANK, new BankOverlay.BankData(npc.getName() + "'s Bank", session));
            default -> dialogue(npc);
        }
    }
    private void dialogue(NpcDefinition npc) {
        manager.push(GameState.DIALOGUE, new DialogueScreen.DialogueData(npc, session,
                DialogueContext.freeze(npc.getName(), session), List.of()));
    }
    private void openShop(ShopInventory shop) {
        if (shop == null) message = "Missing shop definition.";
        else manager.push(GameState.SHOP, new ShopOverlay.ShopData(shop, session));
    }
    private void interactFeature(PlacedFeature f, String text) {
        if (f.featureType() == PlacedFeatureType.LOCKED_DOOR) { message = text; return; }
        if (!visit.traversal().featureAvailable(f)) return;
        switch (f.featureType()) {
            case SIGN, ALTAR -> {
                if (!(f.serviceData() instanceof String content)) { message = "Missing text."; return; }
                var sign = new NpcDefinition(0, f.featureType() == PlacedFeatureType.SIGN ? "Sign" : "Altar", NpcRole.INFORMANT, null);
                sign.getDialogue().addNode(new DialogueNode(content, null, null, null));
                dialogue(sign); visit.traversal().completeInteraction(f);
            }
            case SHOP_COUNTER -> openShop(f.serviceData() instanceof ShopInventory shop ? shop
                    : f.serviceData() instanceof Integer id && shops != null ? shops.get(id) : null);
            case CHEST, TRAP -> showEffect(visit.traversal().interact(f));
            case INN_COUNTER, GUILD_COUNTER, BANK_COUNTER -> message = "Service counter — custom settings pending.";
            default -> message = "Interaction unavailable.";
        }
    }
    private void showEffect(LocalFeatureEffects.Result effect) {
        message = effect.message();
        if (effect.status() == LocalFeatureEffects.Status.OPENED) {
            var items = new java.util.LinkedHashMap<Integer, Integer>();
            for (var entry : effect.items()) items.merge(entry.itemId(), entry.quantity(), Integer::sum);
            var names = items.entrySet().stream().limit(3)
                    .map(e -> ItemTable.get(e.getKey()).name() + " x" + e.getValue()).toList();
            message = "Found " + effect.gold() + " gold" + (names.isEmpty() ? "." : "; " + String.join(", ", names))
                    + (items.size() > 3 ? " (+" + (items.size() - 3) + " more item types)." : "");
        } else if (!effect.damage().isEmpty()) {
            message += " " + String.join(", ", effect.damage().stream().map(d -> d.member().getName() + " -" + d.amount() + " HP").toList());
            if (effect.status() == LocalFeatureEffects.Status.DEFEATED) message += " The party has fallen. Esc: pause.";
        }
    }

    @Override public void render(Graphics2D g) {
        int w = GameCanvas.DESIGN_WIDTH, h = GameCanvas.DESIGN_HEIGHT;
        g.setColor(new Color(20, 23, 28)); g.fillRect(0, 0, w, h);
        var traversal = visit.traversal(); var map = traversal.map();
        var set = AuthoredContentValidator.tileSet(map.getTileSetId());
        int cameraX = traversal.position().x() * CELL - w / 2 + CELL / 2;
        int cameraY = traversal.position().y() * CELL - h / 2 + CELL / 2;
        int x0 = Math.max(0, Math.floorDiv(cameraX, CELL)), y0 = Math.max(0, Math.floorDiv(cameraY, CELL));
        int x1 = Math.min(map.getWidth(), Math.floorDiv(cameraX + w, CELL) + 1), y1 = Math.min(map.getHeight(), Math.floorDiv(cameraY + h, CELL) + 1);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            if (!traversal.isExplored(x, y)) continue;
            boolean visible = traversal.isVisible(x, y);
            int px = x * CELL - cameraX, py = y * CELL - cameraY;
            var tile = set.getEntry(map.getTile(x, y));
            Color color = tile != null && tile.passable() ? new Color(108, 116, 105) : new Color(48, 53, 65);
            g.setColor(visible ? color : color.darker().darker()); g.fillRect(px, py, CELL - 1, CELL - 1);
            var f = map.getFeatureAt(x, y);
            if (visible && traversal.featureAvailable(f)) {
                g.setColor(new Color(230, 190, 80)); g.drawRect(px + 3, py + 3, CELL - 7, CELL - 7);
                g.drawString(f.featureType() == PlacedFeatureType.STAIRS_LINK ? "LINK" : f.featureType() == PlacedFeatureType.EXIT ? "EXIT" : f.featureType().name().substring(0, 3), px + 5, py + 17);
            }
            var npc = visible ? traversal.visibleNpcAt(x, y) : null;
            if (npc != null) {
                g.setColor(new Color(100, 175, 240)); g.fillOval(px + 9, py + 9, CELL - 18, CELL - 18);
                g.setColor(Color.WHITE);
                var definition = npcs == null ? null : npcs.get(npc.npcId());
                g.drawString(definition == null ? "#" + npc.npcId() : definition.getName(), px + 3, py - 4);
            }
        }
        int cx = w / 2, cy = h / 2;
        g.setColor(new Color(250, 220, 110)); g.fillOval(cx - 14, cy - 14, 28, 28);
        int vx = traversal.facing() == Direction.EAST ? 1 : traversal.facing() == Direction.WEST ? -1 : 0;
        int vy = traversal.facing() == Direction.SOUTH ? 1 : traversal.facing() == Direction.NORTH ? -1 : 0;
        g.setStroke(new BasicStroke(4)); g.setColor(Color.WHITE); g.drawLine(cx, cy, cx + vx * 23, cy + vy * 23);
        g.setColor(new Color(18, 20, 25, 235)); g.fillRect(18, 15, w - 36, 76); g.fillRect(18, h - 105, w - 36, 88);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24)); g.setColor(Color.WHITE); g.drawString(map.getName(), 35, 47);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        g.drawString("Arrows / WASD: move     Esc: pause", 35, 76);
        g.drawString(message == null ? "" : message, 35, h - 69);
        int carried = session.getParty().stream().mapToInt(p -> new Inventory(p).count()).sum();
        String hp = String.join(", ", session.getParty().stream().map(p -> p.getHp() + "/" + p.getStat(Stat.MAX_HP)).toList());
        g.drawString("Map " + map.getId() + "   " + traversal.position() + "   " + traversal.facing()
                + "   Gold " + session.getLedger().getPartyGold() + "   HP " + hp + "   Packs " + carried + "/" + session.getParty().size() * Inventory.MAX_ITEMS, 35, h - 35);
    }
}
