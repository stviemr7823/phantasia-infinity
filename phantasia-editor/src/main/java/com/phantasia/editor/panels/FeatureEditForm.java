package com.phantasia.editor.panels;

import com.phantasia.core.data.*;
import com.phantasia.core.world.*;
import com.phantasia.core.world.ServiceData.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed feature form. Reading a draft never mutates the map or original payload. */
public final class FeatureEditForm extends JPanel {
    private final JComboBox<PlacedFeatureType> type = new JComboBox<>(PlacedFeatureType.values());
    private final JComboBox<Object> required = flags(), consumed = flags();
    private final JTextField asset = new JTextField(20);
    private final JPanel payloadHost = new JPanel(new BorderLayout());
    private final Map<PlacedFeatureType, PayloadForm> drafts = new EnumMap<>(PlacedFeatureType.class);
    private final PlacedFeature original;
    private final int x, y;

    public FeatureEditForm(int x, int y, PlacedFeature original) {
        super(new BorderLayout(8, 8));
        this.x = x; this.y = y; this.original = original;
        JPanel common = new JPanel(new GridLayout(0, 2, 6, 6));
        type.setName("featureType"); required.setName("requiredFlag"); consumed.setName("consumedFlag"); asset.setName("assetId");
        common.add(new JLabel("Feature type")); common.add(type);
        common.add(new JLabel("Active after flag")); common.add(required);
        common.add(new JLabel("Set after interaction")); common.add(consumed);
        common.add(new JLabel("Visual override (optional)")); common.add(asset);
        if (original != null) {
            type.setSelectedItem(original.featureType());
            select(required, original.requiredFlag()); select(consumed, original.consumedFlag());
            asset.setText(original.assetId() == null ? "" : original.assetId());
        } else type.setSelectedItem(PlacedFeatureType.SIGN);
        add(common, BorderLayout.NORTH); add(payloadHost, BorderLayout.CENTER);
        if (original != null && !AuthoredContentValidator.payloadMatches(original.featureType(), original.serviceData())) {
            JTextArea warning = new JTextArea("Existing payload does not match this feature type. Updating replaces it with the fields shown.");
            warning.setLineWrap(true); warning.setWrapStyleWord(true); warning.setEditable(false);
            warning.setForeground(new Color(170, 65, 20)); add(warning, BorderLayout.SOUTH);
        }
        type.addActionListener(e -> showPayload());
        showPayload();
    }
    private static JComboBox<Object> flags() {
        JComboBox<Object> box = new JComboBox<>(); box.addItem("(none)");
        for (var flag : QuestFlag.values()) box.addItem(flag);
        return box;
    }
    private static void select(JComboBox<Object> box, QuestFlag flag) { box.setSelectedItem(flag == null ? "(none)" : flag); }
    private static QuestFlag flag(JComboBox<Object> box) { return box.getSelectedItem() instanceof QuestFlag f ? f : null; }
    private void showPayload() {
        PlacedFeatureType selected = (PlacedFeatureType) type.getSelectedItem();
        var draft = drafts.computeIfAbsent(selected, t -> new PayloadForm(t,
                original != null && original.featureType() == t ? original.serviceData() : null));
        payloadHost.removeAll(); payloadHost.add(draft.panel, BorderLayout.CENTER);
        payloadHost.revalidate(); payloadHost.repaint();
    }
    public PlacedFeature readFeature() {
        var selected = (PlacedFeatureType) type.getSelectedItem();
        String key = asset.getText().trim();
        // Preserve an untouched null/empty distinction on imported content.
        String visual = original != null && java.util.Objects.equals(key, original.assetId())
                ? original.assetId() : key.isEmpty() ? null : key;
        return new PlacedFeature(x, y, selected, drafts.get(selected).read(), flag(required), flag(consumed), visual);
    }

    private static final class PayloadForm {
        private final PlacedFeatureType type;
        private final Object original;
        private final JPanel panel = new JPanel(new BorderLayout(4, 4));
        private final JPanel rows = new JPanel(new GridLayout(0, 2, 6, 6));
        private final Map<String, JTextField> numbers = new LinkedHashMap<>();
        private final Map<String, JCheckBox> options = new LinkedHashMap<>();
        private JTextArea text;
        private JComboBox<Object> unlock;
        private JTable guaranteed, random;
        private JTextField entranceId;

        PayloadForm(PlacedFeatureType type, Object original) {
            this.type = type; this.original = original;
            panel.add(rows, BorderLayout.NORTH);
            switch (type) {
                case SIGN, ALTAR -> text(original instanceof String s ? s : "", "Text");
                case SHOP_COUNTER -> {
                    number("shopId", "Shop ID", original instanceof Integer i ? i : original instanceof ShopInventory s ? s.getShopId() : 1);
                    if (original instanceof ShopInventory shop) {
                        option("useReference", "Replace inline inventory with shop reference", false);
                        panel.add(new JLabel("Inline inventory: " + shop.getName() + " (preserved unless replaced)"), BorderLayout.CENTER);
                    }
                }
                case INN_COUNTER -> {
                    var d = original instanceof InnData v ? v : InnData.standard();
                    number("costPerHp", "Gold per HP", d.costPerHp()); option("restoredDead", "Revive dead party members", d.restoredDead());
                    text(d.greeting(), "Greeting");
                }
                case GUILD_COUNTER -> {
                    var d = original instanceof GuildData v ? v : GuildData.standard();
                    number("trainingCostBase", "Base training cost", d.trainingCostBase());
                    number("trainingCostScale", "Cost per current level", d.trainingCostScale());
                    number("maxLevel", "Maximum level (0 = uncapped)", d.maxLevel());
                }
                case BANK_COUNTER -> {
                    var d = original instanceof BankData v ? v : BankData.standard();
                    number("depositFeePercent", "Deposit fee (%)", d.depositFeePercent());
                    number("withdrawalFeePercent", "Withdrawal fee (%)", d.withdrawalFeePercent());
                }
                case LOCKED_DOOR -> {
                    var d = original instanceof LockData v ? v : LockData.byItem(0, "The door is locked.");
                    number("requiredItemId", "Key item ID (0 = none)", d.requiredItemId());
                    unlock = flags(); unlock.setName("unlockFlag"); select(unlock, d.requiredFlag());
                    rows.add(new JLabel("Unlock when flag is set")); rows.add(unlock);
                    text(d.lockedMessage(), "Locked message");
                }
                case STAIRS_LINK -> {
                    var d = original instanceof StairsData v ? v : new StairsData(0, 0, new WorldPosition(0, 0));
                    var link = original instanceof MapLinkData v ? v : null;
                    option("namedEntrance", "Use named entrance", !(original instanceof StairsData));
                    number("targetMapId", "Destination map ID", link == null ? d.targetMapId() : link.targetMapId());
                    entranceId = new JTextField(link == null ? "entry" : link.entranceId(), 16); entranceId.setName("entranceId");
                    rows.add(new JLabel("Entrance name")); rows.add(entranceId);
                    number("targetFloor", "Floor (coordinate links only)", d.targetFloor());
                    position(d.targetPosition());
                    Runnable mode = () -> {
                        boolean named = option("namedEntrance"); entranceId.setEnabled(named);
                        numbers.get("targetFloor").setEnabled(!named); numbers.get("arrivalX").setEnabled(!named); numbers.get("arrivalY").setEnabled(!named);
                    };
                    options.get("namedEntrance").addActionListener(e -> mode.run()); mode.run();
                }
                case EXIT -> {
                    var p = original instanceof ExitData v ? v.returnPosition() : null;
                    option("entryPoint", "Return to world entry point", p == null); position(p);
                }
                case TRAP -> {
                    var d = original instanceof TrapData v ? v : TrapData.standard();
                    number("damageMin", "Minimum damage", d.damageMin()); number("damageMax", "Maximum damage", d.damageMax());
                    number("detectSkill", "Detection threshold (0 = undetectable)", d.detectSkill()); text(d.message(), "Message");
                }
                case CHEST -> {
                    var d = original instanceof LootTable v ? v : new LootTable(0, 0);
                    number("goldMin", "Minimum gold", d.getGoldMin()); number("goldMax", "Maximum gold", d.getGoldMax());
                    number("rollCount", "Random draws", d.getRollCount());
                    guaranteed = table("guaranteed", new String[]{"Item ID", "Quantity"});
                    random = table("random", new String[]{"Item ID", "Quantity", "Weight"});
                    for (var e : d.getGuaranteed()) ((DefaultTableModel) guaranteed.getModel()).addRow(new Object[]{e.itemId(), e.quantity()});
                    for (var e : d.getRandomPool()) ((DefaultTableModel) random.getModel()).addRow(new Object[]{e.itemId(), e.quantity(), e.weight()});
                    JPanel tables = new JPanel(new GridLayout(2, 1, 4, 4));
                    tables.add(tablePanel("Guaranteed items", guaranteed)); tables.add(tablePanel("Random pool", random));
                    panel.add(tables, BorderLayout.CENTER);
                }
            }
        }
        private void number(String key, String label, int value) {
            JTextField input = new JTextField(Integer.toString(value), 8); input.setName(key);
            numbers.put(key, input); rows.add(new JLabel(label)); rows.add(input);
        }
        private void option(String key, String label, boolean value) {
            JCheckBox input = new JCheckBox(label, value); input.setName(key); options.put(key, input);
            rows.add(new JLabel("")); rows.add(input);
        }
        private void position(WorldPosition p) { number("arrivalX", "Arrival X", p == null ? 0 : p.x()); number("arrivalY", "Arrival Y", p == null ? 0 : p.y()); }
        private void text(String initial, String label) {
            text = new JTextArea(initial == null ? "" : initial, 5, 25); text.setName("text");
            text.setLineWrap(true); text.setWrapStyleWord(true);
            JPanel body = new JPanel(new BorderLayout(4, 4)); body.add(new JLabel(label), BorderLayout.NORTH); body.add(new JScrollPane(text));
            panel.add(body, BorderLayout.CENTER);
        }
        private int number(String key) { return integer(numbers.get(key).getText(), key, 0); }
        private static int integer(Object value, String label, int minimum) {
            try {
                int n = Integer.parseInt(String.valueOf(value).trim());
                if (n < minimum) throw new NumberFormatException();
                return n;
            } catch (NumberFormatException e) { throw new IllegalArgumentException(label + " must be a whole number of at least " + minimum); }
        }
        private boolean option(String key) { return options.get(key).isSelected(); }
        private WorldPosition position() { return new WorldPosition(number("arrivalX"), number("arrivalY")); }
        private static JTable table(String name, String[] columns) {
            JTable table = new JTable(new DefaultTableModel(columns, 0)); table.setName(name);
            table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE); return table;
        }
        private static JPanel tablePanel(String label, JTable table) {
            JPanel p = new JPanel(new BorderLayout(4, 4)); p.add(new JLabel(label), BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(table); scroll.setPreferredSize(new Dimension(360, 75)); p.add(scroll);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton add = new JButton("Add item"), remove = new JButton("Remove selected");
            add.addActionListener(e -> ((DefaultTableModel) table.getModel()).addRow(
                    table.getColumnCount() == 2 ? new Object[]{1, 1} : new Object[]{1, 1, 1}));
            remove.addActionListener(e -> { if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                int row = table.getSelectedRow(); if (row >= 0) ((DefaultTableModel) table.getModel()).removeRow(row); });
            buttons.add(add); buttons.add(remove); p.add(buttons, BorderLayout.SOUTH); return p;
        }
        private static void commit(JTable t) {
            if (t.isEditing() && !t.getCellEditor().stopCellEditing()) throw new IllegalArgumentException("Finish editing the item row");
        }
        private LootTable loot() {
            commit(guaranteed); commit(random);
            LootTable loot = new LootTable(number("goldMin"), number("goldMax")).rolls(number("rollCount"));
            if (loot.getGoldMax() < loot.getGoldMin()) throw new IllegalArgumentException("Maximum gold must be at least minimum gold");
            for (int r = 0; r < guaranteed.getRowCount(); r++) loot.addGuaranteed(
                    integer(guaranteed.getValueAt(r, 0), "Item ID", 1), integer(guaranteed.getValueAt(r, 1), "Quantity", 1));
            for (int r = 0; r < random.getRowCount(); r++) loot.addRandom(
                    integer(random.getValueAt(r, 0), "Item ID", 1), integer(random.getValueAt(r, 1), "Quantity", 1), integer(random.getValueAt(r, 2), "Weight", 1));
            return loot;
        }
        Object read() {
            return switch (type) {
                case SIGN, ALTAR -> text.getText();
                case SHOP_COUNTER -> original instanceof ShopInventory && !option("useReference") ? original : number("shopId");
                case INN_COUNTER -> new InnData(number("costPerHp"), optionalText(original instanceof InnData d ? d.greeting() : null), option("restoredDead"));
                case GUILD_COUNTER -> new GuildData(number("trainingCostBase"), number("trainingCostScale"), number("maxLevel"));
                case BANK_COUNTER -> {
                    int deposit = number("depositFeePercent"), withdrawal = number("withdrawalFeePercent");
                    if (deposit > 100 || withdrawal > 100) throw new IllegalArgumentException("Bank fees must be 0–100 percent");
                    yield new BankData(deposit, withdrawal);
                }
                case LOCKED_DOOR -> new LockData(number("requiredItemId"), flag(unlock), optionalText(original instanceof LockData d ? d.lockedMessage() : null));
                case STAIRS_LINK -> {
                    if (option("namedEntrance")) {
                        String key = entranceId.getText().trim();
                        if (key.isEmpty()) throw new IllegalArgumentException("Entrance name is required");
                        yield new MapLinkData(number("targetMapId"), key);
                    }
                    yield new StairsData(number("targetMapId"), number("targetFloor"), position());
                }
                case EXIT -> new ExitData(option("entryPoint") ? null : position());
                case TRAP -> {
                    int min = number("damageMin"), max = number("damageMax");
                    if (max < min) throw new IllegalArgumentException("Maximum damage must be at least minimum damage");
                    yield new TrapData(min, max, optionalText(original instanceof TrapData d ? d.message() : null), number("detectSkill"));
                }
                case CHEST -> loot();
            };
        }
        private String optionalText(String old) { return old == null && text.getText().isEmpty() ? null : text.getText(); }
    }
}
