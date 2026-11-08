// phantasia-j2d/src/main/java/com/phantasia/j2d/screen/ShopOverlay.java
package com.phantasia.j2d.screen;

import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.LocalSession;
import com.phantasia.core.data.ShopEntry;
import com.phantasia.core.data.ShopInventory;
import com.phantasia.core.model.Job;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.model.item.LocalInventory;
import com.phantasia.core.model.item.ItemCategory;
import com.phantasia.core.model.item.ItemDefinition;
import com.phantasia.core.model.item.ItemTable;
import com.phantasia.j2d.engine.GameState;
import com.phantasia.j2d.engine.Screen;
import com.phantasia.j2d.engine.ScreenManager;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.phantasia.j2d.render.ParchmentRenderer.*;

/**
 * Shop overlay — pushed on top of WorldRoamScreen (or TownRoamScreen in future)
 * when the player interacts with a MERCHANT NPC or shop counter.
 *
 * <h3>Layout (1920×1080)</h3>
 * <pre>
 *   ┌──────────────────────────────────────────────────────────┐  y=190
 *   │  SHOP NAME                                   GOLD: XXXX │
 *   ├──────────────────────────────────────────────────────────┤
 *   │  SHOP STOCK               │  YOUR PACK                  │
 *   │  [WPN] Short Sword  +8   │  ◄ Aldric Lv5 FIGHTER ►     │
 *   │  [ARM] Chain Mail   DEF  │  1. Short Sword              │
 *   │  ...                      │  2. — empty —               │
 *   │                           │  ...                         │
 *   ├──────────────────────────────────────────────────────────┤
 *   │  [ENTER] Buy  [TAB] Switch to Pack  [ESC] Close          │
 *   └──────────────────────────────────────────────────────────┘  y=890
 * </pre>
 *
 * <h3>Interaction model</h3>
 * <ul>
 *   <li>UP/DOWN — navigate shop list (buy mode) or inventory slots (sell mode)</li>
 *   <li>LEFT/RIGHT — cycle party member shown on right panel</li>
 *   <li>TAB — toggle focus between shop list and party inventory</li>
 *   <li>ENTER — buy selected shop item, or sell selected inventory item</li>
 *   <li>ESC — pop overlay, return to underlying screen</li>
 * </ul>
 *
 * <h3>Transition data</h3>
 * Caller passes a {@link ShopData} record containing the {@link ShopInventory}
 * and the live {@link GameSession}.
 *
 * <h3>Gold handling</h3>
 * Buy: {@code session.getLedger().spendGold(price)} — returns false if insufficient.
 * Sell: half the item's base gold value, added via {@code ledger.addGold()}.
 * Quest items ({@link ItemDefinition#isQuestItem()}) cannot be sold.
 *
 * <h3>Stock quantity</h3>
 * Native campaigns track finite stock and one-time purchases in campaign state.
 * The legacy comparison route retains its prior unlimited runtime behavior.
 */
public class ShopOverlay implements Screen {

    // ─────────────────────────────────────────────────────────────────────
    // Transition data
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Data passed via {@code ScreenManager.push(GameState.SHOP, shopData)}.
     *
     * @param inventory the shop's item listing
     * @param session   the live game session (provides ledger and party)
     */
    public record ShopData(ShopInventory inventory, LocalSession session, String catalog) {
        public ShopData(ShopInventory inventory, LocalSession session) { this(inventory, session, com.phantasia.core.logic.NativeShopState.registered(inventory)); }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Layout constants (design resolution 1920×1080)
    // ─────────────────────────────────────────────────────────────────────

    // Outer panel
    private static final int OX = 160,  OY = 160;
    private static final int OW = 1600, OH = 760;

    // Content vertical start (below title bar)
    private static final int CONTENT_Y  = OY + 90;
    private static final int CONTENT_H  = OH - 130;

    // Left panel — shop stock
    private static final int LEFT_X = OX + 24;
    private static final int LEFT_W = 740;

    // Right panel — party inventory
    private static final int RIGHT_X = OX + 836;
    private static final int RIGHT_W = 700;

    // Rows
    private static final int ROW_H       = 42;
    private static final int MAX_VISIBLE = 13;  // rows visible in shop list

    // Error flash
    private static final float ERROR_DURATION = 1.8f;

    // ─────────────────────────────────────────────────────────────────────
    // Dependencies
    // ─────────────────────────────────────────────────────────────────────

    private final ScreenManager screenManager;

    // ─────────────────────────────────────────────────────────────────────
    // Per-open state
    // ─────────────────────────────────────────────────────────────────────

    private ShopData              data;
    private List<ShopEntry>       visibleEntries;
    private List<Integer> visibleRows;

    private int     shopScrollOffset;    // first visible row index
    private int     selectedShopRow;     // absolute index into visibleEntries
    private int     selectedPartyIndex;  // which party member is shown on right
    private int     selectedInventorySlot; // which of 8 pack slots is selected
    private boolean inventoryFocused;    // false = navigating shop; true = navigating pack

    private String  errorMessage;
    private float   errorTimer;

    // ─────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────

    public ShopOverlay(ScreenManager screenManager) {
        this.screenManager = screenManager;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Screen lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onEnter(Object transitionData) {
        this.data           = (ShopData) transitionData;
        refreshStock();

        shopScrollOffset     = 0;
        selectedShopRow      = 0;
        selectedPartyIndex   = 0;
        selectedInventorySlot = 0;
        inventoryFocused     = false;
        errorMessage         = null;
        errorTimer           = 0f;
    }

    @Override
    public void update(float dt) {
        if (errorTimer > 0f) {
            errorTimer -= dt;
            if (errorTimer <= 0f) errorMessage = null;
        }
    }

    @Override
    public void render(Graphics2D g) {
        // ── Dim the underlying WorldRoam ──────────────────────────────
        g.setColor(new Color(0x0D, 0x0D, 0x12, 200));
        g.fillRect(0, 0, 1920, 1080);

        // ── Outer panel ───────────────────────────────────────────────
        drawPanel(g, OX, OY, OW, OH);

        // ── Title bar ─────────────────────────────────────────────────
        String title = data.inventory().getName().toUpperCase();
        drawTextCentered(g, title, OX, OY + 50, OW, TextStyle.HEADER, GOLD_BRIGHT);

        int gold = data.session().getPartyGold();
        drawText(g, "GOLD:  " + gold, OX + OW - 210, OY + 50, TextStyle.DATA, GOLD_BRIGHT);

        drawDivider(g, OX + 24, OY + 64, OW - 48);

        // ── Vertical divider between panels ───────────────────────────
        g.setColor(BORDER_STR);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(OX + LEFT_W + 48, CONTENT_Y - 10,
                OX + LEFT_W + 48, OY + OH - 55);

        // ── Panels ────────────────────────────────────────────────────
        renderShopList(g);
        renderPartyPanel(g);
        renderBottomBar(g);
        renderErrorFlash(g);
    }

    @Override public void onExit() {}

    // ─────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onKeyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {

            case KeyEvent.VK_ESCAPE -> screenManager.pop();

            case KeyEvent.VK_TAB -> inventoryFocused = !inventoryFocused;

            case KeyEvent.VK_UP -> {
                if (!inventoryFocused) {
                    if (selectedShopRow > 0) {
                        selectedShopRow--;
                        if (selectedShopRow < shopScrollOffset)
                            shopScrollOffset--;
                    }
                } else {
                    if (selectedInventorySlot > 0) selectedInventorySlot--;
                }
            }

            case KeyEvent.VK_DOWN -> {
                if (!inventoryFocused) {
                    if (selectedShopRow < visibleEntries.size() - 1) {
                        selectedShopRow++;
                        if (selectedShopRow >= shopScrollOffset + MAX_VISIBLE)
                            shopScrollOffset++;
                    }
                } else {
                    if (selectedInventorySlot < currentPc().inventorySlots() - 1)
                        selectedInventorySlot++;
                }
            }

            case KeyEvent.VK_LEFT -> {
                int size = data.session().localMembers().size();
                selectedPartyIndex = (selectedPartyIndex - 1 + size) % size;
                selectedInventorySlot = 0;
            }

            case KeyEvent.VK_RIGHT -> {
                int size = data.session().localMembers().size();
                selectedPartyIndex = (selectedPartyIndex + 1) % size;
                selectedInventorySlot = 0;
            }

            case KeyEvent.VK_ENTER -> {
                if (!inventoryFocused) attemptBuy();
                else                  attemptSell();
            }
        }
    }

    @Override public void onMouseClicked(MouseEvent e) {}

    // ─────────────────────────────────────────────────────────────────────
    // Buy / sell logic
    // ─────────────────────────────────────────────────────────────────────

    private void refreshStock() {
        visibleRows = java.util.stream.IntStream.range(0, data.inventory().getEntries().size())
                .filter(row -> {
                    var entry = data.inventory().getEntries().get(row);
                    if (data.session() instanceof com.phantasia.core.logic.NativeCampaignV5 nativeParty)
                        return nativeParty.shopState().quote(data.catalog(), data.inventory(), row, nativeParty::hasFlag).visible();
                    return entry.requiredFlag() == null || data.session().hasFlag(entry.requiredFlag());
                }).boxed().toList();
        visibleEntries = visibleRows.stream().map(row -> data.inventory().getEntries().get(row)).toList();
        selectedShopRow = Math.max(0, Math.min(selectedShopRow, visibleEntries.size() - 1));
        shopScrollOffset = Math.min(shopScrollOffset, selectedShopRow);
    }

    private void attemptBuy() {
        if (visibleEntries.isEmpty()) return;
        if (data.session() instanceof com.phantasia.core.logic.NativeCampaignV5 nativeParty) {
            try {
                nativeParty.buy(data.catalog(), data.inventory(), visibleRows.get(selectedShopRow), nativeParty.party().get(selectedPartyIndex));
                errorMessage = null; errorTimer = 0f; refreshStock();
            } catch (IllegalArgumentException | IllegalStateException ex) { showError(ex.getMessage()); }
            return;
        }

        ShopEntry      entry = visibleEntries.get(selectedShopRow);
        ItemDefinition item  = ItemTable.get(entry.itemId());
        int            price = buyPrice(entry, item);

        LocalSession.Member pc  = currentPc();
        LocalInventory  inv = new LocalInventory(pc);

        if (!data.session().spendGold(price)) {
            showError("Not enough gold!");
            return;
        }

        if (!inv.add(entry.itemId())) {
            // Refund — pack is full
            data.session().addGold(price);
            showError(pc.getName() + "'s pack is full!");
        }
        // NOTE: ShopEntry.quantity() depletion deferred to a future ShopRegistry
        //       that manages mutable per-shop stock state.
    }

    private void attemptSell() {
        LocalSession.Member pc  = currentPc();
        LocalInventory  inv = new LocalInventory(pc);

        int itemId = inv.getItemId(selectedInventorySlot);
        if (itemId == 0) return; // empty slot

        ItemDefinition item = ItemTable.get(itemId);
        if (item.isQuestItem()) {
            showError("Cannot sell quest items!");
            return;
        }

        int sellPrice = Math.max(1, item.gold() / 2);
        if ((long) data.session().getPartyGold() + sellPrice > Integer.MAX_VALUE) {
            showError("The party cannot carry this much gold."); return;
        }
        inv.removeAt(selectedInventorySlot);
        data.session().addGold(sellPrice);

        // Keep selection in bounds after removal
        if (selectedInventorySlot >= currentPc().inventorySlots())
            selectedInventorySlot = currentPc().inventorySlots() - 1;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Rendering helpers
    // ─────────────────────────────────────────────────────────────────────

    private void renderShopList(Graphics2D g) {
        int headerY = CONTENT_Y + 24;

        // Panel sub-header
        String panelTitle = inventoryFocused ? "SHOP STOCK" : "SHOP STOCK  ◄ ACTIVE";
        drawText(g, panelTitle, LEFT_X, headerY,
                TextStyle.LABEL, inventoryFocused ? BORDER_STR : GOLD);
        drawDivider(g, LEFT_X, headerY + 8, LEFT_W);

        int rowY = headerY + 28;

        if (visibleEntries.isEmpty()) {
            drawText(g, "— No items available —",
                    LEFT_X + 16, rowY + 20, TextStyle.BODY, BORDER_STR);
            return;
        }

        int endIdx = Math.min(shopScrollOffset + MAX_VISIBLE, visibleEntries.size());

        for (int i = shopScrollOffset; i < endIdx; i++) {
            int     row      = i - shopScrollOffset;
            int     ry       = rowY + row * ROW_H;
            boolean selected = !inventoryFocused && (i == selectedShopRow);

            ShopEntry      entry = visibleEntries.get(i);
            ItemDefinition item  = ItemTable.get(entry.itemId());
            var nativeQuote = data.session() instanceof com.phantasia.core.logic.NativeCampaignV5 nativeParty
                    ? nativeParty.shopState().quote(data.catalog(), data.inventory(), visibleRows.get(i), nativeParty::hasFlag) : null;
            int price = nativeQuote == null ? buyPrice(entry, item) : nativeQuote.price();
            int remaining = nativeQuote == null ? entry.quantity() : nativeQuote.remaining();
            boolean canAfford = data.session().getPartyGold() >= price && remaining != 0 && (nativeQuote == null || nativeQuote.priceInRange());

            // Selection highlight
            if (selected) {
                g.setColor(new Color(0xBF, 0xA3, 0x4C, 35));
                g.fillRoundRect(LEFT_X - 4, ry - 20, LEFT_W + 4, ROW_H - 2, 6, 6);
                g.setColor(new Color(0xBF, 0xA3, 0x4C, 90));
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(LEFT_X - 4, ry - 20, LEFT_W + 4, ROW_H - 2, 6, 6);
            }

            Color nameColor = selected ? GOLD_BRIGHT : TEXT;

            // Category badge
            drawText(g, categoryBadge(item.category()),
                    LEFT_X + 2, ry, TextStyle.DATA, BORDER_STR);

            // Item name
            drawText(g, item.name(), LEFT_X + 66, ry, TextStyle.BODY, nameColor);

            // Stat summary
            drawText(g, statSummary(item),
                    LEFT_X + 390, ry, TextStyle.DATA, BORDER_STR);

            // Stock count (if limited)
            if (remaining != -1) {
                drawText(g, remaining == 0 ? "OUT" : "×" + remaining,
                        LEFT_X + LEFT_W - 120, ry, TextStyle.DATA, BORDER_STR);
            }

            // Price — red if unaffordable
            Color priceColor = canAfford ? GOLD : DANGER;
            drawText(g, nativeQuote != null && !nativeQuote.priceInRange() ? "LIMIT" : price + "g",
                    LEFT_X + LEFT_W - 54, ry, TextStyle.DATA, priceColor);
        }

        // Scroll arrows
        if (shopScrollOffset > 0)
            drawTextCentered(g, "▲", LEFT_X, rowY - 16, LEFT_W,
                    TextStyle.DATA, BORDER_STR);
        if (endIdx < visibleEntries.size())
            drawTextCentered(g, "▼", LEFT_X,
                    rowY + MAX_VISIBLE * ROW_H + 4, LEFT_W,
                    TextStyle.DATA, BORDER_STR);
    }

    private void renderPartyPanel(Graphics2D g) {
        List<? extends LocalSession.Member> party = data.session().localMembers();
        if (party.isEmpty()) return;

        LocalSession.Member pc  = currentPc();
        LocalInventory  inv = new LocalInventory(pc);

        int headerY = CONTENT_Y + 24;

        // Panel sub-header
        String panelTitle = inventoryFocused ? "YOUR PACK  ◄ ACTIVE" : "YOUR PACK";
        drawText(g, panelTitle, RIGHT_X, headerY,
                TextStyle.LABEL, inventoryFocused ? GOLD : BORDER_STR);
        drawDivider(g, RIGHT_X, headerY + 8, RIGHT_W);

        // Party member selector
        int selectorY = headerY + 32;
        drawText(g, "◄", RIGHT_X, selectorY, TextStyle.LABEL, GOLD);
        String memberLabel = pc.getName()
                + "   Lv" + pc.getLevel()
                + "  " + Job.fromValue(pc.getJob()).name();
        drawTextCentered(g, memberLabel, RIGHT_X + 20, selectorY,
                RIGHT_W - 40, TextStyle.LABEL, GOLD_BRIGHT);
        drawText(g, "►", RIGHT_X + RIGHT_W - 18, selectorY,
                TextStyle.LABEL, GOLD);
        drawDivider(g, RIGHT_X, selectorY + 8, RIGHT_W);

        // Inventory slots
        int slotY = selectorY + 26;
        for (int slot = 0; slot < currentPc().inventorySlots(); slot++) {
            int     ry       = slotY + slot * ROW_H;
            boolean selected = inventoryFocused && (slot == selectedInventorySlot);
            int     itemId   = inv.getItemId(slot);

            // Selection highlight
            if (selected) {
                g.setColor(new Color(0xBF, 0xA3, 0x4C, 35));
                g.fillRoundRect(RIGHT_X - 4, ry - 20, RIGHT_W + 4, ROW_H - 2, 6, 6);
                g.setColor(new Color(0xBF, 0xA3, 0x4C, 90));
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(RIGHT_X - 4, ry - 20, RIGHT_W + 4, ROW_H - 2, 6, 6);
            }

            // Slot number
            drawText(g, (slot + 1) + ".", RIGHT_X + 2, ry,
                    TextStyle.DATA, BORDER_STR);

            if (itemId == 0) {
                drawText(g, "— empty —", RIGHT_X + 40, ry,
                        TextStyle.BODY, BORDER_STR);
            } else {
                ItemDefinition item      = ItemTable.get(itemId);
                Color          nameColor = selected ? GOLD_BRIGHT : TEXT;
                drawText(g, item.name(), RIGHT_X + 40, ry,
                        TextStyle.BODY, nameColor);

                // Show sell price on the right
                int sellPrice = Math.max(1, item.gold() / 2);
                String sellLabel = item.isQuestItem() ? "NO SELL"
                        : sellPrice + "g";
                Color sellColor = item.isQuestItem() ? BORDER_STR : GOLD;
                drawText(g, sellLabel, RIGHT_X + RIGHT_W - 80, ry,
                        TextStyle.DATA, sellColor);
            }
        }
    }

    private void renderBottomBar(Graphics2D g) {
        int barY = OY + OH - 40;
        drawDivider(g, OX + 24, barY - 12, OW - 48);

        String hint = inventoryFocused
                ? "[ENTER] Sell Item    [←/→] Switch Member"
                  + "    [TAB] Switch to Shop    [ESC] Close"
                : "[ENTER] Buy Item    [←/→] Switch Member"
                  + "    [TAB] Switch to Pack    [ESC] Close";

        drawTextCentered(g, hint, OX, barY + 6, OW,
                TextStyle.DATA, BORDER_STR);
    }

    private void renderErrorFlash(Graphics2D g) {
        if (errorMessage == null || errorTimer <= 0f) return;

        // Fade in quickly, hold, fade out
        float alpha = Math.min(1f, errorTimer > ERROR_DURATION * 0.8f
                ? (ERROR_DURATION - errorTimer) / (ERROR_DURATION * 0.2f)
                : errorTimer / (ERROR_DURATION * 0.2f));
        alpha = Math.max(0.1f, Math.min(1f, alpha));

        int ew = 560, eh = 52;
        int ex = (1920 - ew) / 2;
        int ey = OY + OH / 2 - eh / 2;

        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        drawPanel(g, ex, ey, ew, eh, DANGER);
        drawTextCentered(g, errorMessage, ex, ey + 33, ew,
                TextStyle.LABEL, DANGER);
        g.setComposite(old);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Utility helpers
    // ─────────────────────────────────────────────────────────────────────

    private LocalSession.Member currentPc() {
        List<? extends LocalSession.Member> party = data.session().localMembers();
        int idx = Math.min(selectedPartyIndex, party.size() - 1);
        return party.get(idx);
    }

    private static int buyPrice(ShopEntry entry, ItemDefinition item) {
        return Math.max(1, (int)(item.gold() * entry.priceMultiplier()));
    }

    private void showError(String message) {
        this.errorMessage = message;
        this.errorTimer   = ERROR_DURATION;
    }

    private static String categoryBadge(ItemCategory cat) {
        return switch (cat) {
            case WEAPON         -> "[WPN]";
            case SHIELD         -> "[SHD]";
            case ARMOR          -> "[ARM]";
            case BOW            -> "[BOW]";
            case HEALING_POTION -> "[POT]";
            case MAGIC_POTION   -> "[MNA]";
            case SCROLL         -> "[SCR]";
            case TREASURE       -> "[TRE]";
        };
    }

    private static String statSummary(ItemDefinition item) {
        StringBuilder sb = new StringBuilder();
        return switch (item.category()) {
            case WEAPON, BOW -> {
                sb.append("ATK +").append(item.attack());
                if (item.enchant() > 0) sb.append("  +").append(item.enchant()).append(" enc");
                yield sb.toString();
            }
            case SHIELD, ARMOR -> {
                sb.append("DEF +").append(item.defense());
                if (item.enchant() > 0) sb.append("  +").append(item.enchant()).append(" enc");
                yield sb.toString();
            }
            case HEALING_POTION ->
                    "Restores " + (item.potionRank() * item.potionRank()) + " HP";
            case MAGIC_POTION ->
                    "Restores " + (3 * item.potionRank()) + " MP";
            case SCROLL ->
                    "Readable";
            case TREASURE ->
                    "Quest item";
        };
    }
}
