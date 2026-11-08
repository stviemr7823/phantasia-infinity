package com.phantasia.j2d.screen;

import com.phantasia.core.logic.NativeCampaignV5;
import com.phantasia.core.world.ServiceData;
import com.phantasia.j2d.engine.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Authored native inn/bank services over the retained local-map screen. */
public final class NativeServiceOverlay implements Screen {
    public record Data(NativeCampaignV5 campaign, String name, ServiceData.InnData inn, ServiceData.BankData bank,
                       BooleanSupplier available, Runnable completed, boolean oneShot) {
        public Data {
            Objects.requireNonNull(campaign); Objects.requireNonNull(name); Objects.requireNonNull(available); Objects.requireNonNull(completed);
            if ((inn == null) == (bank == null)) throw new IllegalArgumentException("Choose one service");
        }
    }
    private final ScreenManager manager;
    private final Set<Integer> pressed = new HashSet<>();
    private Data data;
    private boolean deposit, resolved;
    private String amount = "", message = "";
    public NativeServiceOverlay(ScreenManager manager) { this.manager = manager; }
    @Override public void onEnter(Object incoming) {
        data = (Data) incoming; deposit = true; resolved = false; amount = ""; message = ""; pressed.clear();
        if (data.inn() != null) data.campaign().quoteRest(data.inn()); else NativeCampaignV5.quoteBank(true, 1, data.bank());
    }
    @Override public void onExit() { pressed.clear(); }
    @Override public void update(float dt) {}
    @Override public void onMouseClicked(MouseEvent e) {}
    @Override public void onKeyReleased(KeyEvent e) { pressed.remove(e.getKeyCode()); }
    @Override public void onKeyPressed(KeyEvent e) {
        int key = e.getKeyCode(); if (!pressed.add(key)) return;
        if (key == KeyEvent.VK_ESCAPE) { manager.pop(); return; }
        if (resolved) return;
        try {
            if (data.bank() != null) {
                if (key == KeyEvent.VK_D || key == KeyEvent.VK_W) { deposit = key == KeyEvent.VK_D; amount = ""; return; }
                if (key == KeyEvent.VK_BACK_SPACE) { if (!amount.isEmpty()) amount = amount.substring(0, amount.length() - 1); return; }
                if (key >= KeyEvent.VK_0 && key <= KeyEvent.VK_9 && amount.length() < 10) { amount += (char)('0' + key - KeyEvent.VK_0); return; }
            }
            if (key != KeyEvent.VK_ENTER) return;
            if (!data.available().getAsBoolean()) throw new IllegalStateException("This service is no longer available.");
            if (data.inn() != null) {
                var receipt = data.campaign().rest(data.inn()); resolved = true;
                message = "Rested " + receipt.recipients() + " member(s) for " + receipt.cost() + " gold. Revived: " + receipt.revived() + ".";
            } else {
                var receipt = data.campaign().transferBank(deposit, parseAmount(), data.bank());
                resolved = data.oneShot(); amount = "";
                message = (deposit ? "Deposited " : "Withdrew ") + receipt.gross() + "; fee " + receipt.fee() + "; credited " + receipt.credited() + ".";
            }
            data.completed().run();
        } catch (IllegalArgumentException | IllegalStateException ex) { message = ex.getMessage(); }
    }
    private int parseAmount() {
        long value = amount.isEmpty() ? 0 : Long.parseLong(amount);
        if (value < 1 || value > Integer.MAX_VALUE) throw new IllegalArgumentException("Enter an amount from 1 to " + Integer.MAX_VALUE + ".");
        return (int) value;
    }
    @Override public void render(Graphics2D g) {
        int w = GameCanvas.DESIGN_WIDTH, h = GameCanvas.DESIGN_HEIGHT;
        g.setColor(new Color(10, 14, 18, 220)); g.fillRect(0, 0, w, h);
        g.setColor(new Color(35, 41, 45)); g.fillRoundRect(240, 170, w - 480, h - 340, 24, 24);
        g.setColor(new Color(240, 210, 125)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34)); g.drawString(data.name(), 290, 235);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 25)); g.setColor(Color.WHITE);
        var c = data.campaign();
        g.drawString("Purse " + c.gold() + "    Bank " + c.bankGold() + "    Pending earned gold " + c.pendingGold(), 290, 293);
        if (data.inn() != null) {
            var quote = c.quoteRest(data.inn());
            String greeting = data.inn().greeting();
            if (greeting != null) g.drawString(greeting.substring(0, Math.min(95, greeting.length())), 290, 350);
            g.drawString("Rate: " + data.inn().costPerHp() + " per HP; 1 per MP. Cost now: " + quote.cost(), 290, 410);
            g.drawString("Guests: " + quote.recipients() + "    Revival: " + (data.inn().restoredDead() ? "enabled" : "disabled"), 290, 460);
            for (int i = 0; i < c.party().size(); i++) {
                var m = c.party().get(i);
                g.drawString(m.getName() + "  HP " + m.getHp() + "/" + m.getMaxHp() + "  MP " + m.runtime().mp() + "/" + m.runtime().maxMp(),
                        290 + (i % 2) * 650, 500 + (i / 2) * 32);
            }
            g.drawString(resolved ? "Rest complete. Esc: return to area" : "Enter: pay and rest    Esc: leave", 290, 620);
        } else {
            g.drawString("D: deposit    W: withdraw    Current: " + (deposit ? "deposit" : "withdraw"), 290, 360);
            g.drawString("Gross amount: " + amount, 290, 420);
            g.drawString("Fees: deposit " + data.bank().depositFeePercent() + "% / withdraw " + data.bank().withdrawalFeePercent() + "% (rounded up)", 290, 470);
            try {
                var quote = NativeCampaignV5.quoteBank(deposit, parseAmount(), data.bank());
                g.drawString("Fee " + quote.fee() + "    Credited " + quote.credited(), 290, 520);
            } catch (IllegalArgumentException ignored) {}
            g.drawString(resolved ? "One-time service resolved. Esc: return" : "0-9: amount    Backspace: delete    Enter: confirm    Esc: leave", 290, 580);
        }
        g.setColor(new Color(240, 210, 125)); g.drawString(message, 290, 700);
    }
}
