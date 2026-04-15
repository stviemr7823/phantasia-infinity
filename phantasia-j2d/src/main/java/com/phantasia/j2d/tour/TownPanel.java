// phantasia-j2d/src/main/java/com/phantasia/j2d/tour/TownPanel.java
package com.phantasia.j2d.tour;

import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.town.GuildService;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.model.Stat;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

/**
 * Town screen — fully functional inn, guild eligibility check, shop stub.
 * Confirms town trigger fires with the correct id/name and all services work.
 */
public class TownPanel extends JPanel {

    private final GameSession session;
    private final TourFrame   frame;

    private int    townId;
    private String townName;

    private JLabel  titleLbl;
    private JLabel  idLbl;
    private JLabel  goldLbl;
    private JButton innBtn;
    private JPanel  partyStatus;

    // -------------------------------------------------------------------------

    public TownPanel(GameSession session, TourFrame frame) {
        this.session = session;
        this.frame   = frame;
        setLayout(new BorderLayout(10, 10));
        setBackground(new Color(18, 14, 8));
        setBorder(new EmptyBorder(20, 28, 20, 28));
        buildLayout();
    }

    public void open(int id, String name) {
        this.townId   = id;
        this.townName = name;
        frame.eventLog.log("TOWN", "Opened " + name + "  id=" + id);
        refresh();
    }

    // -------------------------------------------------------------------------

    private void buildLayout() {
        // Header
        JPanel header = new JPanel(new BorderLayout(6, 4));
        header.setBackground(new Color(30, 24, 14));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 75, 38)),
                new EmptyBorder(12, 16, 12, 16)));

        titleLbl = new JLabel("TOWN");
        titleLbl.setFont(TourFrame.F_TITLE);
        titleLbl.setForeground(TourFrame.C_ACCENT);

        idLbl = new JLabel("id: —");
        idLbl.setFont(TourFrame.F_SMALL);
        idLbl.setForeground(TourFrame.C_DIM);

        goldLbl = new JLabel("Gold: — gp");
        goldLbl.setFont(TourFrame.F_BODY);
        goldLbl.setForeground(TourFrame.C_ACCENT);

        header.add(titleLbl, BorderLayout.WEST);
        header.add(idLbl,    BorderLayout.CENTER);
        header.add(goldLbl,  BorderLayout.EAST);

        // Centre — services left, party status right
        JPanel centre = new JPanel(new GridLayout(1, 2, 12, 0));
        centre.setBackground(new Color(18, 14, 8));

        centre.add(buildServicesPanel());
        centre.add(buildPartyStatusPanel());

        // Footer
        JButton leave = btn("LEAVE TOWN",
                () -> frame.returnToMap("Left " + townName));
        leave.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        footer.setBackground(new Color(18, 14, 8));
        footer.add(leave);

        add(header,  BorderLayout.NORTH);
        add(centre,  BorderLayout.CENTER);
        add(footer,  BorderLayout.SOUTH);
    }

    private JPanel buildServicesPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(18, 14, 8));

        // Inn
        JPanel inn = section("INN  — Rest and recover HP / MP");
        innBtn = btn("REST  (calculating…)", this::doRest);
        innBtn.setAlignmentX(LEFT_ALIGNMENT);
        inn.add(innBtn);
        p.add(inn);
        p.add(Box.createVerticalStrut(10));

        // Guild
        JPanel guild = section("GUILD  — Level up (training)");
        JButton check = btn("CHECK ELIGIBILITY", this::doGuildCheck);
        check.setAlignmentX(LEFT_ALIGNMENT);
        guild.add(check);
        JLabel gNote = note("Full training coming in a future build.");
        guild.add(Box.createVerticalStrut(3));
        guild.add(gNote);
        p.add(guild);
        p.add(Box.createVerticalStrut(10));

        // Shop stub
        JPanel shop = section("SHOP  — Buy equipment");
        shop.add(note("Shop not yet implemented."));
        p.add(shop);

        return p;
    }

    private JPanel buildPartyStatusPanel() {
        partyStatus = new JPanel();
        partyStatus.setLayout(new BoxLayout(partyStatus, BoxLayout.Y_AXIS));
        partyStatus.setBackground(new Color(28, 22, 14));
        partyStatus.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 68, 36)),
                new EmptyBorder(8, 12, 8, 12)));

        JLabel t = new JLabel("PARTY STATUS");
        t.setFont(new Font(Font.SERIF, Font.BOLD, 14));
        t.setForeground(TourFrame.C_ACCENT);
        t.setAlignmentX(LEFT_ALIGNMENT);
        partyStatus.add(t);
        partyStatus.add(Box.createVerticalStrut(6));
        return partyStatus;
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    private void refresh() {
        titleLbl.setText(townName.toUpperCase());
        idLbl.setText("  id=" + townId);
        goldLbl.setText("Gold: " + session.getLedger().getPartyGold() + " gp");

        int cost = innCost();
        if (cost == 0) {
            innBtn.setText("REST — Party already at full health");
            innBtn.setEnabled(false);
        } else {
            innBtn.setText("REST  (" + cost + " gp)");
            innBtn.setEnabled(session.getLedger().getPartyGold() >= cost);
        }

        // Rebuild party status rows
        while (partyStatus.getComponentCount() > 2) partyStatus.remove(2);
        for (PlayerCharacter pc : session.getParty()) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
            row.setBackground(new Color(28, 22, 14));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

            boolean alive = pc.isAlive();
            JLabel nl = new JLabel(String.format("%-10s", pc.getName())
                    + (alive ? "" : " [DEAD]"));
            nl.setFont(TourFrame.F_SMALL);
            nl.setForeground(alive ? TourFrame.C_TEXT : TourFrame.C_DIM);
            row.add(nl);

            if (alive) {
                JLabel stats = new JLabel(
                        "HP:" + pc.getHp() + "/" + pc.getStat(Stat.MAX_HP)
                                + "  MP:" + pc.getStat(Stat.MAGIC_POWER)
                                + "/" + pc.getStat(Stat.MAX_MAGIC));
                stats.setFont(TourFrame.F_SMALL);
                stats.setForeground(TourFrame.C_DIM);
                row.add(stats);
            }
            partyStatus.add(row);
        }
        partyStatus.revalidate();
        partyStatus.repaint();
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // Inn
    // -------------------------------------------------------------------------

    private void doRest() {
        int cost = innCost();
        if (cost == 0) return;
        if (!session.getLedger().spendGold(cost)) {
            JOptionPane.showMessageDialog(this,
                    "Not enough gold — need " + cost + " gp.",
                    "Inn", JOptionPane.WARNING_MESSAGE);
            return;
        }
        session.getParty().forEach(pc -> {
            if (!pc.isAlive()) return;
            pc.setStat(Stat.HP,          pc.getStat(Stat.MAX_HP));
            pc.setStat(Stat.MAGIC_POWER, pc.getStat(Stat.MAX_MAGIC));
        });
        frame.eventLog.log("TOWN", "Inn rest — " + cost + " gp, party restored.");
        frame.refreshStatus();
        refresh();
        JOptionPane.showMessageDialog(this,
                "The party rests.\nAll HP and MP restored.  (" + cost + " gp)",
                townName + " — Inn", JOptionPane.INFORMATION_MESSAGE);
    }

    private int innCost() {
        int missing = 0;
        for (PlayerCharacter pc : session.getParty())
            if (pc.isAlive())
                missing += Math.max(0, pc.getStat(Stat.MAX_HP) - pc.getHp());
        return missing == 0 ? 0 : Math.max(5, missing * 10);
    }

    // -------------------------------------------------------------------------
    // Guild
    // -------------------------------------------------------------------------

    private void doGuildCheck() {
        StringBuilder sb = new StringBuilder("=== GUILD ELIGIBILITY ===\n\n");
        GuildService g = GuildService.INSTANCE;
        for (PlayerCharacter pc : session.getParty()) {
            if (!pc.isAlive()) { sb.append(pc.getName()).append(": DEAD\n"); continue; }
            if (g.canTrain(pc))
                sb.append(pc.getName()).append(": READY — cost ")
                        .append(g.trainingCost(pc)).append(" gp\n");
            else
                sb.append(pc.getName()).append(": Not yet eligible\n");
        }
        frame.eventLog.log("TOWN", "Guild check.");
        JOptionPane.showMessageDialog(this, sb.toString(),
                townName + " — Guild", JOptionPane.INFORMATION_MESSAGE);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private JPanel section(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(28, 22, 14));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 68, 36)),
                new EmptyBorder(8, 12, 8, 12)));
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font(Font.SERIF, Font.BOLD, 13));
        lbl.setForeground(TourFrame.C_ACCENT);
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        p.add(lbl);
        p.add(Box.createVerticalStrut(5));
        return p;
    }

    private JLabel note(String text) {
        JLabel l = new JLabel(text);
        l.setFont(TourFrame.F_SMALL);
        l.setForeground(TourFrame.C_DIM);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JButton btn(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(TourFrame.F_BUTTON);
        b.setForeground(TourFrame.C_ACCENT);
        b.setBackground(new Color(42, 34, 16));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 75, 38)),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        return b;
    }
}