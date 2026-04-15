// phantasia-j2d/src/main/java/com/phantasia/j2d/tour/CombatPanel.java
package com.phantasia.j2d.tour;

import com.phantasia.core.data.EncounterFactory;
import com.phantasia.core.data.GameSession;
import com.phantasia.core.data.SpellFactory;
import com.phantasia.core.logic.*;
import com.phantasia.core.model.*;
import com.phantasia.core.model.Action;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fully playable 2D combat screen.
 *
 * LAYOUT:
 *
 *   ┌──────────────────────┬────────────────────┐
 *   │  PARTY STATUS        │  ENEMY ROSTER      │
 *   ├──────────────────────┴────────────────────┤
 *   │  COMBAT LOG  (narrator text, scrolling)   │
 *   ├───────────────────────────────────────────┤
 *   │  ACTION MENU  (per-character buttons)     │
 *   └───────────────────────────────────────────┘
 *
 * FLOW:
 *   startRandom() or startScripted() → initCombat()
 *     → openMenuFor(party[0])
 *     → player clicks actions for each living PC
 *     → all assigned → executeRound()
 *     → narrate result → repeat or endCombat()
 *   ENTER shortcut = CombatAI auto-assigns all and fires round immediately.
 */
public class CombatPanel extends JPanel {

    private final GameSession session;
    private final TourFrame   frame;

    // Live combat state
    private CombatManager         manager;
    private List<PlayerCharacter> party;
    private List<Monster>         enemies;
    private int                   assignIdx = 0;

    // Sub-panels rebuilt each combat
    private JPanel    partyPane;
    private JPanel    enemyPane;
    private JTextArea log;
    private JPanel    menuPane;
    private JLabel    menuPrompt;

    // -------------------------------------------------------------------------

    public CombatPanel(GameSession session, TourFrame frame) {
        this.session = session;
        this.frame   = frame;
        setLayout(new BorderLayout(4, 4));
        setBackground(TourFrame.C_BG);
        setBorder(new EmptyBorder(8, 8, 8, 8));
        buildSkeleton();
    }

    // -------------------------------------------------------------------------
    // Entry points called by TourFrame
    // -------------------------------------------------------------------------

    public void startRandom() {
        party   = new ArrayList<>(session.getParty());
        enemies = EncounterFactory.generateEncounter();
        initCombat(FormulaEngine.rollEncounterCondition());
    }

    public void startScripted(String monsterName, int count) {
        party   = new ArrayList<>(session.getParty());
        enemies = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String label = count > 1 ? monsterName + " " + (char)('A' + i) : monsterName;
            enemies.add(EncounterFactory.buildMonster(label, 20 + (int)(Math.random()*20), count, 50));
        }
        initCombat(EncounterCondition.NORMAL);
    }

    // -------------------------------------------------------------------------
    // Initialise a battle
    // -------------------------------------------------------------------------

    private void initCombat(EncounterCondition cond) {
        SpellFactory sf = new SpellFactory();
        try { sf.loadSpells(); }
        catch (IOException e) { frame.eventLog.log("COMBAT", "spells.dat missing — spells will fail."); }

        manager = new CombatManager(party, enemies, sf, cond);
        manager.setPartyInvincible(TourSettings.get().isGodMode());
        assignIdx  = 0;

        frame.eventLog.log("COMBAT", party.size() + " vs " + enemies.size()
                + "  [" + cond.name() + "]");

        clearLog();
        appendLog("══════════════════════════════════════");
        appendLog("  ENCOUNTER  —  " + cond.announcement());
        appendLog(rosterLine("Party", party));
        appendLog(rosterLine("Foes", enemies));
        appendLog("══════════════════════════════════════");

        rebuildRosters();
        openMenuFor(nextLivingPC());
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // Action menu
    // -------------------------------------------------------------------------

    private void openMenuFor(PlayerCharacter pc) {
        menuPane.removeAll();

        if (pc == null) {
            // All assigned — show Execute button
            menuPrompt.setText("All actions assigned.");
            JButton exec = btn("  ▶  EXECUTE ROUND  ", TourFrame.C_ACCENT, this::executeRound);
            exec.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            menuPane.add(menuPrompt, BorderLayout.NORTH);
            menuPane.add(exec,       BorderLayout.CENTER);
        } else {
            Job job = Job.fromValue(pc.getJob());
            menuPrompt.setText("  " + pc.getName() + "  (" + job + ")"
                    + "   HP: " + pc.getHp() + "/" + pc.getStat(Stat.MAX_HP)
                    + "   MP: " + pc.getStat(Stat.MAGIC_POWER));

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
            btns.setBackground(TourFrame.C_PANEL);

            // ATTACK — always
            btns.add(btn("ATTACK", TourFrame.C_ACCENT, () -> assign(pc, Action.ATTACK, false)));

            // Job-specific
            switch (job) {
                case FIGHTER -> btns.add(btn("SLASH ×3", TourFrame.C_ACCENT,
                        () -> assign(pc, Action.SLASH, false)));
                case THIEF   -> btns.add(btn("THRUST",   TourFrame.C_ACCENT,
                        () -> assign(pc, Action.THRUST, false)));
                case RANGER  -> btns.add(btn("AIM BLOW", TourFrame.C_ACCENT,
                        () -> assign(pc, Action.AIM_BLOW, false)));
                default -> {}
            }

            // CAST — wizard/priest with MP
            if ((job == Job.WIZARD || job == Job.PRIEST)
                    && pc.getStat(Stat.MAGIC_POWER) >= 3) {
                boolean heal = (job == Job.PRIEST);
                btns.add(btn(heal ? "CAST (Heal)" : "CAST (Spell)",
                        TourFrame.C_BLUE, () -> assign(pc, Action.CAST, heal)));
            }

            btns.add(btn("PARRY",  TourFrame.C_GREEN, () -> assign(pc, Action.PARRY, false)));
            btns.add(btn("RUN",    TourFrame.C_RED,   () -> assign(pc, Action.RUN, false)));
            btns.add(btn("AUTO ALL ↵", TourFrame.C_DIM, this::autoAll));

            menuPane.add(menuPrompt, BorderLayout.NORTH);
            menuPane.add(btns,       BorderLayout.CENTER);
        }

        menuPane.revalidate();
        menuPane.repaint();
    }

    private void assign(PlayerCharacter pc, Action action, boolean heal) {
        pc.setAction(action);
        if (action == Action.CAST) {
            int spellId = heal ? 2 : 1;
            pc.setSelectedSpellId(spellId);
            pc.setPrimaryTarget(heal ? mostWounded() : firstLivingEnemy());
        } else if (action == Action.PARRY || action == Action.RUN) {
            pc.setPrimaryTarget(null);
        } else {
            pc.setPrimaryTarget(firstLivingEnemy());
        }
        appendLog("  " + pc.getName() + " → " + action.name());
        assignIdx++;
        openMenuFor(nextLivingPC());
    }

    private void autoAll() {
        CombatAI.assignPartyActions(party, enemies);
        appendLog("  [AUTO] AI assigned all party actions.");
        executeRound();
    }

    // -------------------------------------------------------------------------
    // Round execution
    // -------------------------------------------------------------------------

    private void executeRound() {
        if (manager == null) return;

        CombatAI.assignMonsterActions(enemies, party);
        RoundResult result = manager.runRound();

        appendLog("");
        appendLog("── Round " + result.roundNumber() + " ─────────────────────");
        CombatNarrator narrator = new CombatNarrator();
        for (String line : narrator.narrateRound(result)) {
            if (line != null) appendLog("  " + line);
        }

        frame.eventLog.log("COMBAT", "Round " + result.roundNumber()
                + "  → " + result.outcome().name());

        rebuildRosters();

        if (result.isCombatOver()) {
            endCombat(result.outcome());
        } else {
            assignIdx = 0;
            openMenuFor(nextLivingPC());
        }
    }

    // -------------------------------------------------------------------------
    // End of battle
    // -------------------------------------------------------------------------

    private void endCombat(CombatOutcome outcome) {
        appendLog("");
        appendLog("══════════════════════════════════════");
        appendLog("  " + outcome.name());

        if (outcome == CombatOutcome.VICTORY) {
            LootManager.LootResult loot =
                    new LootManager(party, session.getLedger())
                            .distributeFrom(enemies);
            appendLog(loot.describe(session.getLedger()));
            int xpPool = enemies.stream().mapToInt(Entity::getExperience).sum();
            appendLog(ExperienceTable.awardCombatXp(party, xpPool));
            frame.eventLog.log("COMBAT", "Victory  " + loot.totalGold() + " gp looted.");
        } else {
            frame.eventLog.log("COMBAT", "Outcome: " + outcome.name());
        }

        appendLog("══════════════════════════════════════");
        frame.refreshStatus();

        // Replace menu with return button
        menuPane.removeAll();
        menuPrompt.setText("  Combat over: " + outcome.name());
        JButton ret = btn("  ▶  RETURN TO MAP  ", TourFrame.C_ACCENT,
                () -> frame.returnToMap("Combat ended: " + outcome.name()));
        ret.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        menuPane.add(menuPrompt, BorderLayout.NORTH);
        menuPane.add(ret,        BorderLayout.CENTER);
        menuPane.revalidate();
        menuPane.repaint();
    }

    // -------------------------------------------------------------------------
    // UI skeleton — built once, roster panels rebuilt per combat
    // -------------------------------------------------------------------------

    private void buildSkeleton() {
        // Top: party + enemy rosters side by side
        partyPane = rosterPanel("PARTY",   TourFrame.C_BLUE);
        enemyPane = rosterPanel("ENEMIES", TourFrame.C_RED);
        JPanel topRow = new JPanel(new GridLayout(1, 2, 6, 0));
        topRow.setBackground(TourFrame.C_BG);
        topRow.setPreferredSize(new Dimension(0, 160));
        topRow.add(partyPane);
        topRow.add(enemyPane);

        // Centre: combat log
        log = new JTextArea();
        log.setEditable(false);
        log.setBackground(new Color(10, 8, 15));
        log.setForeground(TourFrame.C_TEXT);
        log.setFont(TourFrame.F_BODY);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createLineBorder(TourFrame.C_BORDER));

        // Bottom: action menu
        menuPrompt = new JLabel("Preparing...");
        menuPrompt.setFont(TourFrame.F_BODY);
        menuPrompt.setForeground(TourFrame.C_ACCENT);
        menuPrompt.setBorder(new EmptyBorder(4, 8, 4, 8));

        menuPane = new JPanel(new BorderLayout(4, 4));
        menuPane.setBackground(TourFrame.C_PANEL);
        menuPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TourFrame.C_BORDER),
                new EmptyBorder(6, 8, 6, 8)));
        menuPane.setPreferredSize(new Dimension(0, 100));

        add(topRow,    BorderLayout.NORTH);
        add(logScroll, BorderLayout.CENTER);
        add(menuPane,  BorderLayout.SOUTH);

        // ENTER shortcut
        getInputMap(WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "auto");
        getActionMap().put("auto", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (manager != null && !manager.isCombatOver()) autoAll();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Roster panels
    // -------------------------------------------------------------------------

    private JPanel rosterPanel(String title, Color titleColor) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(TourFrame.C_PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TourFrame.C_BORDER),
                new EmptyBorder(6, 8, 6, 8)));
        JLabel lbl = new JLabel(title);
        lbl.setFont(TourFrame.F_TITLE);
        lbl.setForeground(titleColor);
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        p.add(lbl);
        p.add(Box.createVerticalStrut(4));
        return p;
    }

    private void rebuildRosters() {
        rebuildPartyPane();
        rebuildEnemyPane();
    }

    private void rebuildPartyPane() {
        partyPane.removeAll();
        JLabel t = new JLabel("PARTY");
        t.setFont(TourFrame.F_TITLE); t.setForeground(TourFrame.C_BLUE);
        t.setAlignmentX(LEFT_ALIGNMENT);
        partyPane.add(t);
        partyPane.add(Box.createVerticalStrut(4));
        if (party != null) {
            for (PlayerCharacter pc : party) {
                partyPane.add(entityRow(
                        pc.getName() + " (" + Job.fromValue(pc.getJob()) + ")",
                        pc.getHp(), pc.getStat(Stat.MAX_HP),
                        pc.getStat(Stat.MAGIC_POWER), pc.getStat(Stat.MAX_MAGIC),
                        pc.isAlive(), TourFrame.C_BLUE));
            }
        }
        partyPane.revalidate(); partyPane.repaint();
    }

    private void rebuildEnemyPane() {
        enemyPane.removeAll();
        JLabel t = new JLabel("ENEMIES");
        t.setFont(TourFrame.F_TITLE); t.setForeground(TourFrame.C_RED);
        t.setAlignmentX(LEFT_ALIGNMENT);
        enemyPane.add(t);
        enemyPane.add(Box.createVerticalStrut(4));
        if (enemies != null) {
            for (Monster m : enemies) {
                enemyPane.add(entityRow(m.getName(),
                        m.getHp(), m.getHp() + 1, 0, 0,
                        m.isAlive(), TourFrame.C_RED));
            }
        }
        enemyPane.revalidate(); enemyPane.repaint();
    }

    private JPanel entityRow(String name, int cur, int max,
                             int mp, int maxMp, boolean alive, Color c) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setBackground(TourFrame.C_PANEL);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel nl = new JLabel(String.format("%-14s", name));
        nl.setFont(TourFrame.F_SMALL);
        nl.setForeground(alive ? c : TourFrame.C_DIM);
        row.add(nl);

        if (alive) {
            row.add(hpBar(cur, max));
            if (maxMp > 0) row.add(mpBar(mp, maxMp));
        } else {
            JLabel dead = new JLabel("DEAD");
            dead.setFont(TourFrame.F_SMALL);
            dead.setForeground(TourFrame.C_DIM);
            row.add(dead);
        }
        return row;
    }

    // -------------------------------------------------------------------------
    // Inline bar components
    // -------------------------------------------------------------------------

    private static final Color HP_GREEN = new Color(55, 190, 70);
    private static final Color HP_RED   = new Color(200, 70, 50);
    private static final Color MP_BLUE  = new Color(70, 110, 210);
    private static final Color BAR_BG   = new Color(30, 28, 22);

    private JComponent hpBar(int cur, int max) {
        return bar(cur, max, 90, 9, cur * 100 / Math.max(1,max) < 30 ? HP_RED : HP_GREEN, "HP");
    }
    private JComponent mpBar(int cur, int max) {
        return bar(cur, max, 55, 9, MP_BLUE, "MP");
    }

    private JComponent bar(int cur, int max, int w, int h, Color fg, String label) {
        return new JComponent() {
            public Dimension getPreferredSize() { return new Dimension(w + 45, h + 4); }
            protected void paintComponent(Graphics g) {
                int fill = max > 0 ? (int)((long)cur * w / max) : 0;
                g.setColor(BAR_BG); g.fillRect(0, 2, w, h);
                g.setColor(fg);     g.fillRect(0, 2, fill, h);
                g.setColor(TourFrame.C_BORDER); g.drawRect(0, 2, w, h);
                g.setFont(TourFrame.F_SMALL);
                g.setColor(TourFrame.C_TEXT);
                g.drawString(label + ":" + cur + "/" + max, w + 3, h + 1);
            }
        };
    }

    // -------------------------------------------------------------------------
    // Log helpers
    // -------------------------------------------------------------------------

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            log.append(line + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    private void clearLog() {
        SwingUtilities.invokeLater(() -> log.setText(""));
    }

    // -------------------------------------------------------------------------
    // Button factory
    // -------------------------------------------------------------------------

    private JButton btn(String text, Color fg, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(TourFrame.F_BUTTON);
        b.setForeground(fg);
        b.setBackground(new Color(32, 28, 42));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TourFrame.C_BORDER),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        return b;
    }

    // -------------------------------------------------------------------------
    // Combat helpers
    // -------------------------------------------------------------------------

    private PlayerCharacter nextLivingPC() {
        while (assignIdx < party.size()) {
            if (party.get(assignIdx).isAlive()) return party.get(assignIdx);
            assignIdx++;
        }
        return null;   // all assigned
    }

    private Monster firstLivingEnemy() {
        if (enemies == null) return null;
        for (Monster m : enemies) if (m.isAlive()) return m;
        return null;
    }

    private PlayerCharacter mostWounded() {
        PlayerCharacter worst = null; int low = 101;
        for (PlayerCharacter pc : party) {
            if (!pc.isAlive()) continue;
            int maxHp = pc.getStat(Stat.MAX_HP);
            if (maxHp == 0) continue;
            int pct = pc.getHp() * 100 / maxHp;
            if (pct < low) { low = pct; worst = pc; }
        }
        return worst;
    }

    private String rosterLine(String label, List<?> list) {
        StringBuilder sb = new StringBuilder("  " + label + ": ");
        list.forEach(o -> {
            if (o instanceof PlayerCharacter pc)
                sb.append(pc.getName()).append("(").append(pc.getHp()).append("hp) ");
            else if (o instanceof Monster m)
                sb.append(m.getName()).append("(").append(m.getHp()).append("hp) ");
        });
        return sb.toString().trim();
    }
}