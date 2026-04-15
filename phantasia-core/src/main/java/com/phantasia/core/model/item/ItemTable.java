package com.phantasia.core.model.item;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The complete 1987 item manifest — all 180 items defined and indexed
 * by their single-byte ID. This is the authoritative item registry.
 *
 * ItemFactory and inventory code look items up here by ID.
 * Nothing is instantiated at runtime — items are stateless definitions.
 */
public final class ItemTable {

    private ItemTable() {}

    private static final Map<Integer, ItemDefinition> TABLE;

    static {
        Map<Integer, ItemDefinition> t = new HashMap<>();

        // ----------------------------------------------------------------
        // SHIELDS  (1–19)
        // ----------------------------------------------------------------
        t.put(  1, new ItemDefinition.Builder(  1, "Glove",            ItemCategory.SHIELD).defense(1).gold(10).build());
        t.put(  2, new ItemDefinition.Builder(  2, "Wooden Shield",    ItemCategory.SHIELD).defense(2).gold(20).build());
        t.put(  3, new ItemDefinition.Builder(  3, "Wooden Shield +1", ItemCategory.SHIELD).defense(2).enchant(1).gold(60).build());
        t.put(  4, new ItemDefinition.Builder(  4, "Small Shield",     ItemCategory.SHIELD).defense(3).gold(80).build());
        t.put(  5, new ItemDefinition.Builder(  5, "Small Shield +1",  ItemCategory.SHIELD).defense(3).enchant(1).gold(160).build());
        t.put(  6, new ItemDefinition.Builder(  6, "Small Shield +2",  ItemCategory.SHIELD).defense(3).enchant(2).gold(240).build());
        t.put(  7, new ItemDefinition.Builder(  7, "Small Shield +3",  ItemCategory.SHIELD).defense(3).enchant(3).gold(320).build());
        t.put(  8, new ItemDefinition.Builder(  8, "Medium Shield",    ItemCategory.SHIELD).defense(5).gold(200).build());
        t.put(  9, new ItemDefinition.Builder(  9, "Medium Shield +1", ItemCategory.SHIELD).defense(5).enchant(1).gold(400).build());
        t.put( 10, new ItemDefinition.Builder( 10, "Medium Shield +2", ItemCategory.SHIELD).defense(5).enchant(2).gold(600).build());
        t.put( 11, new ItemDefinition.Builder( 11, "Medium Shield +3", ItemCategory.SHIELD).defense(5).enchant(3).gold(800).build());
        t.put( 12, new ItemDefinition.Builder( 12, "Large Shield",     ItemCategory.SHIELD).defense(7).gold(400).build());
        t.put( 13, new ItemDefinition.Builder( 13, "Large Shield +1",  ItemCategory.SHIELD).defense(7).enchant(1).gold(800).build());
        t.put( 14, new ItemDefinition.Builder( 14, "Large Shield +2",  ItemCategory.SHIELD).defense(7).enchant(2).gold(1200).build());
        t.put( 15, new ItemDefinition.Builder( 15, "Large Shield +3",  ItemCategory.SHIELD).defense(7).enchant(3).gold(1600).build());
        t.put( 16, new ItemDefinition.Builder( 16, "Giant Shield",     ItemCategory.SHIELD).defense(10).gold(1000).build());
        t.put( 17, new ItemDefinition.Builder( 17, "Giant Shield +1",  ItemCategory.SHIELD).defense(10).enchant(1).gold(2000).build());
        t.put( 18, new ItemDefinition.Builder( 18, "Giant Shield +2",  ItemCategory.SHIELD).defense(10).enchant(2).gold(3000).build());
        t.put( 19, new ItemDefinition.Builder( 19, "Giant Shield +3",  ItemCategory.SHIELD).defense(10).enchant(3).gold(4000).build());

        // ----------------------------------------------------------------
        // ARMOR  (20–40)
        // ----------------------------------------------------------------
        t.put( 20, new ItemDefinition.Builder( 20, "God Shield",    ItemCategory.SHIELD).defense(15).enchant(5).gold(9999).build());
        t.put( 21, new ItemDefinition.Builder( 21, "Clothing",      ItemCategory.ARMOR).defense(1).gold(5).build());
        t.put( 22, new ItemDefinition.Builder( 22, "Robes",         ItemCategory.ARMOR).defense(2).gold(30).build());
        t.put( 23, new ItemDefinition.Builder( 23, "Leather",       ItemCategory.ARMOR).defense(4).gold(80).build());
        t.put( 24, new ItemDefinition.Builder( 24, "Hard Leather",  ItemCategory.ARMOR).defense(5).gold(150).build());
        t.put( 25, new ItemDefinition.Builder( 25, "Ring Mail",     ItemCategory.ARMOR).defense(6).gold(250).build());
        t.put( 26, new ItemDefinition.Builder( 26, "Scale Mail",    ItemCategory.ARMOR).defense(7).gold(400).build());
        t.put( 27, new ItemDefinition.Builder( 27, "Chain Mail",    ItemCategory.ARMOR).defense(8).gold(600).build());
        t.put( 28, new ItemDefinition.Builder( 28, "Splint Mail",   ItemCategory.ARMOR).defense(9).gold(900).build());
        t.put( 29, new ItemDefinition.Builder( 29, "Banded Mail",   ItemCategory.ARMOR).defense(10).gold(1200).build());
        t.put( 30, new ItemDefinition.Builder( 30, "Plate Mail",    ItemCategory.ARMOR).defense(12).gold(2000).build());
        t.put( 31, new ItemDefinition.Builder( 31, "Cloth +1",      ItemCategory.ARMOR).defense(1).enchant(1).gold(80).build());
        t.put( 32, new ItemDefinition.Builder( 32, "Robes +1",      ItemCategory.ARMOR).defense(2).enchant(1).gold(200).build());
        t.put( 33, new ItemDefinition.Builder( 33, "Leather +1",    ItemCategory.ARMOR).defense(4).enchant(1).gold(300).build());
        t.put( 34, new ItemDefinition.Builder( 34, "Leather +2",    ItemCategory.ARMOR).defense(4).enchant(2).gold(500).build());
        t.put( 35, new ItemDefinition.Builder( 35, "Ring Mail +1",  ItemCategory.ARMOR).defense(6).enchant(1).gold(600).build());
        t.put( 36, new ItemDefinition.Builder( 36, "Ring Mail +2",  ItemCategory.ARMOR).defense(6).enchant(2).gold(900).build());
        t.put( 37, new ItemDefinition.Builder( 37, "Chain Mail +1", ItemCategory.ARMOR).defense(8).enchant(1).gold(1200).build());
        t.put( 38, new ItemDefinition.Builder( 38, "Chain Mail +2", ItemCategory.ARMOR).defense(8).enchant(2).gold(1800).build());
        t.put( 39, new ItemDefinition.Builder( 39, "God Robes",     ItemCategory.ARMOR).defense(10).enchant(5).gold(9999).build());
        t.put( 40, new ItemDefinition.Builder( 40, "God Armour",    ItemCategory.ARMOR).defense(15).enchant(5).gold(9999).build());

        // ----------------------------------------------------------------
        // BOWS  (41–60)
        // ----------------------------------------------------------------
        t.put( 41, new ItemDefinition.Builder( 41, "Self Bow",       ItemCategory.BOW).attack(2).gold(50).build());
        t.put( 42, new ItemDefinition.Builder( 42, "Self Bow +1",    ItemCategory.BOW).attack(2).enchant(1).gold(150).build());
        t.put( 43, new ItemDefinition.Builder( 43, "Self Bow +2",    ItemCategory.BOW).attack(2).enchant(2).gold(250).build());
        t.put( 44, new ItemDefinition.Builder( 44, "Short Bow",      ItemCategory.BOW).attack(4).gold(150).build());
        t.put( 45, new ItemDefinition.Builder( 45, "Short Bow +1",   ItemCategory.BOW).attack(4).enchant(1).gold(350).build());
        t.put( 46, new ItemDefinition.Builder( 46, "Short Bow +2",   ItemCategory.BOW).attack(4).enchant(2).gold(550).build());
        t.put( 47, new ItemDefinition.Builder( 47, "Medium Bow",     ItemCategory.BOW).attack(6).gold(400).build());
        t.put( 48, new ItemDefinition.Builder( 48, "Medium Bow +1",  ItemCategory.BOW).attack(6).enchant(1).gold(800).build());
        t.put( 49, new ItemDefinition.Builder( 49, "Medium Bow +2",  ItemCategory.BOW).attack(6).enchant(2).gold(1200).build());
        t.put( 50, new ItemDefinition.Builder( 50, "Compound Bow",   ItemCategory.BOW).attack(8).gold(800).build());
        t.put( 51, new ItemDefinition.Builder( 51, "Compound Bow +1",ItemCategory.BOW).attack(8).enchant(1).gold(1600).build());
        t.put( 52, new ItemDefinition.Builder( 52, "Compound Bow +2",ItemCategory.BOW).attack(8).enchant(2).gold(2400).build());
        t.put( 53, new ItemDefinition.Builder( 53, "Gnome Bow",      ItemCategory.BOW).attack(5).gold(300).build());
        t.put( 54, new ItemDefinition.Builder( 54, "Long Bow",       ItemCategory.BOW).attack(10).gold(1500).build());
        t.put( 55, new ItemDefinition.Builder( 55, "Long Bow +1",    ItemCategory.BOW).attack(10).enchant(1).gold(3000).build());
        t.put( 56, new ItemDefinition.Builder( 56, "Long Bow +2",    ItemCategory.BOW).attack(10).enchant(2).gold(4500).build());
        t.put( 57, new ItemDefinition.Builder( 57, "Crossbow",       ItemCategory.BOW).attack(9).gold(1200).build());
        t.put( 58, new ItemDefinition.Builder( 58, "Old Bow",        ItemCategory.BOW).attack(3).gold(20).build());
        t.put( 59, new ItemDefinition.Builder( 59, "Crossbow +2",    ItemCategory.BOW).attack(9).enchant(2).gold(3600).build());
        t.put( 60, new ItemDefinition.Builder( 60, "God Bow",        ItemCategory.BOW).attack(15).enchant(5).gold(9999).build());

        // ----------------------------------------------------------------
        // WEAPONS  (61–80)
        // ----------------------------------------------------------------
        t.put( 61, new ItemDefinition.Builder( 61, "Knife",       ItemCategory.WEAPON).attack(1).gold(10).build());
        t.put( 62, new ItemDefinition.Builder( 62, "Dagger",      ItemCategory.WEAPON).attack(2).gold(25).build());
        t.put( 63, new ItemDefinition.Builder( 63, "Club",        ItemCategory.WEAPON).attack(2).gold(15).build());
        t.put( 64, new ItemDefinition.Builder( 64, "Mace",        ItemCategory.WEAPON).attack(3).gold(60).build());
        t.put( 65, new ItemDefinition.Builder( 65, "Small Axe",   ItemCategory.WEAPON).attack(3).gold(50).build());
        t.put( 66, new ItemDefinition.Builder( 66, "Staff",       ItemCategory.WEAPON).attack(2).gold(20).build());
        t.put( 67, new ItemDefinition.Builder( 67, "Short Sword", ItemCategory.WEAPON).attack(4).gold(100).build());
        t.put( 68, new ItemDefinition.Builder( 68, "Flail",       ItemCategory.WEAPON).attack(4).gold(120).build());
        t.put( 69, new ItemDefinition.Builder( 69, "Hammer",      ItemCategory.WEAPON).attack(4).gold(110).build());
        t.put( 70, new ItemDefinition.Builder( 70, "Spear",       ItemCategory.WEAPON).attack(5).gold(150).build());
        t.put( 71, new ItemDefinition.Builder( 71, "Axe",         ItemCategory.WEAPON).attack(5).gold(180).build());
        t.put( 72, new ItemDefinition.Builder( 72, "Sword",       ItemCategory.WEAPON).attack(6).gold(250).build());
        t.put( 73, new ItemDefinition.Builder( 73, "Heavy Mace",  ItemCategory.WEAPON).attack(6).gold(280).build());
        t.put( 74, new ItemDefinition.Builder( 74, "Trident",     ItemCategory.WEAPON).attack(6).gold(300).build());
        t.put( 75, new ItemDefinition.Builder( 75, "Large Spear", ItemCategory.WEAPON).attack(7).gold(350).build());
        t.put( 76, new ItemDefinition.Builder( 76, "Large Axe",   ItemCategory.WEAPON).attack(7).gold(400).build());
        t.put( 77, new ItemDefinition.Builder( 77, "Pike",        ItemCategory.WEAPON).attack(8).gold(500).build());
        t.put( 78, new ItemDefinition.Builder( 78, "Long Sword",  ItemCategory.WEAPON).attack(9).gold(700).build());
        t.put( 79, new ItemDefinition.Builder( 79, "Bardiche",    ItemCategory.WEAPON).attack(10).gold(900).build());
        t.put( 80, new ItemDefinition.Builder( 80, "Halberd",     ItemCategory.WEAPON).attack(11).gold(1200).build());

        // ----------------------------------------------------------------
        // MAGIC WEAPONS  (81–100)
        // ----------------------------------------------------------------
        t.put( 81, new ItemDefinition.Builder( 81, "Dagger +1",   ItemCategory.WEAPON).attack(2).enchant(1).gold(200).build());
        t.put( 82, new ItemDefinition.Builder( 82, "Dagger +2",   ItemCategory.WEAPON).attack(2).enchant(2).gold(400).build());
        t.put( 83, new ItemDefinition.Builder( 83, "Club +1",     ItemCategory.WEAPON).attack(2).enchant(1).gold(180).build());
        t.put( 84, new ItemDefinition.Builder( 84, "Club +2",     ItemCategory.WEAPON).attack(2).enchant(2).gold(360).build());
        t.put( 85, new ItemDefinition.Builder( 85, "Flail +1",    ItemCategory.WEAPON).attack(4).enchant(1).gold(400).build());
        t.put( 86, new ItemDefinition.Builder( 86, "Flail +2",    ItemCategory.WEAPON).attack(4).enchant(2).gold(800).build());
        t.put( 87, new ItemDefinition.Builder( 87, "Spear +1",    ItemCategory.WEAPON).attack(5).enchant(1).gold(500).build());
        t.put( 88, new ItemDefinition.Builder( 88, "Sword +1",    ItemCategory.WEAPON).attack(6).enchant(1).gold(800).build());
        t.put( 89, new ItemDefinition.Builder( 89, "Sword +2",    ItemCategory.WEAPON).attack(6).enchant(2).gold(1200).build());
        t.put( 90, new ItemDefinition.Builder( 90, "Sword +4",    ItemCategory.WEAPON).attack(6).enchant(4).gold(2000).build());
        t.put( 91, new ItemDefinition.Builder( 91, "Sword +5",    ItemCategory.WEAPON).attack(6).enchant(5).gold(2500).build());
        t.put( 92, new ItemDefinition.Builder( 92, "Sword +6",    ItemCategory.WEAPON).attack(6).enchant(6).gold(3000).build());
        t.put( 93, new ItemDefinition.Builder( 93, "Halberd +1",  ItemCategory.WEAPON).attack(11).enchant(1).gold(2000).build());
        t.put( 94, new ItemDefinition.Builder( 94, "Halberd +2",  ItemCategory.WEAPON).attack(11).enchant(2).gold(3000).build());
        t.put( 95, new ItemDefinition.Builder( 95, "Sword +10",   ItemCategory.WEAPON).attack(6).enchant(10).gold(9000).build());
        t.put( 96, new ItemDefinition.Builder( 96, "Halberd +5",  ItemCategory.WEAPON).attack(11).enchant(5).gold(6000).build());
        t.put( 97, new ItemDefinition.Builder( 97, "Halberd +6",  ItemCategory.WEAPON).attack(11).enchant(6).gold(7000).build());
        t.put( 98, new ItemDefinition.Builder( 98, "God Knife",   ItemCategory.WEAPON).attack(8).enchant(5).gold(9999).build());
        t.put( 99, new ItemDefinition.Builder( 99, "God Mace",    ItemCategory.WEAPON).attack(10).enchant(5).gold(9999).build());
        t.put(100, new ItemDefinition.Builder(100, "God Sword",   ItemCategory.WEAPON).attack(15).enchant(10).gold(9999).build());

        // ----------------------------------------------------------------
        // HEALING POTIONS  (101–110)  — formula: X² HP
        // ----------------------------------------------------------------
        for (int x = 1; x <= 10; x++) {
            int id = 100 + x;
            t.put(id, new ItemDefinition.Builder(id,
                    "Healing Potion " + x, ItemCategory.HEALING_POTION)
                    .potionRank(x)
                    .gold(x * x * 10)
                    .build());
        }

        // ----------------------------------------------------------------
        // MAGIC POTIONS  (111–120)  — formula: 3X MP
        // ----------------------------------------------------------------
        for (int x = 1; x <= 10; x++) {
            int id = 110 + x;
            t.put(id, new ItemDefinition.Builder(id,
                    "Magic Potion " + x, ItemCategory.MAGIC_POTION)
                    .potionRank(x)
                    .gold(x * 3 * 8)
                    .build());
        }

        // ----------------------------------------------------------------
        // SCROLLS  (121–140)
        // scrollId identifies the text entry to display when the player
        // reads the scroll.  These IDs will map to entries in the scroll
        // content table once that data file is authored.
        // ----------------------------------------------------------------
        int[] scrollIds = {
                1,  2,  3,  3,  3,   // 121–125  Scroll I–III (III appears 3x)
                4,  5,  6,  7,  7,   // 126–130  Scroll IV–VII (VII appears 2x)
                7,  8,  9, 10, 11,   // 131–135  Scroll VII–XI
                12, 13, 14, 15, 16    // 136–140  Scroll XII–XVI
        };
        String[] scrollNames = {
                "Scroll I",   "Scroll II",   "Scroll III",  "Scroll III",  "Scroll III",
                "Scroll IV",  "Scroll V",    "Scroll VI",   "Scroll VII",  "Scroll VII",
                "Scroll VII", "Scroll VIII", "Scroll IX",   "Scroll X",    "Scroll XI",
                "Scroll XII", "Scroll XIII", "Scroll XIV",  "Scroll XV",   "Scroll XVI"
        };

        for (int i = 0; i < 20; i++) {
            int id = 121 + i;
            t.put(id, new ItemDefinition.Builder(id,
                    scrollNames[i], ItemCategory.SCROLL)
                    .scrollId(scrollIds[i])
                    .gold(100 + (i * 25))
                    .build());
        }


        // ----------------------------------------------------------------
        // TREASURES  (141–180)
        // Quest items flagged explicitly. Gold values are estimates —
        // vendor trash sells low, quest items are priceless.
        // ----------------------------------------------------------------
        Object[][] treasures = {
                {141, "Quartz",          false, 50},
                {142, "Pearl",           false, 120},
                {143, "Crystal",         false, 80},
                {144, "Gold Ore",        false, 200},
                {145, "Statuette",       false, 150},
                {146, "Jade",            false, 180},
                {147, "Onyx",            false, 160},
                {148, "Statue",          false, 300},
                {149, "Sapphire",        false, 400},
                {150, "Azurite",         false, 100},
                {151, "Coral",           false, 90},
                {152, "Turquoise",       false, 110},
                {153, "Topaz",           false, 250},
                {154, "Jacinth",         false, 350},
                {155, "Burnt Gem",       false, 5},
                {156, "Sweet Bottle",    false, 40},
                {157, "Foul Bottle",     false, 10},
                {158, "Black Jar",       true,  0},
                {159, "Ruby",            false, 500},
                {160, "Nice Gem",        false, 220},
                {161, "Opal",            false, 280},
                {162, "Star Ruby",       false, 700},
                {163, "Emerald",         false, 600},
                {164, "Blue Opal",       false, 350},
                {165, "Diamond",         false, 900},
                {166, "Amethyst",        false, 320},
                {167, "Fire Opal",       false, 450},
                {168, "Gem of Light",    true,  0},
                {169, "Black Urn",       true,  0},
                {170, "Dark Key",        true,  0},
                {171, "Black Vase",      true,  0},
                {172, "Light Key",       true,  0},
                {173, "Golden Chalice",  true,  0},
                {174, "Silk Tapestry",   false, 400},
                {175, "Painting",        false, 350},
                {176, "Golden Belt",     false, 500},
                {177, "Old Crown",       true,  0},
                {178, "Old Coin",        false, 60},
                {179, "Old Wine",        false, 80},
                {180, "Wand of Nikademus", true, 0},
        };

        for (Object[] row : treasures) {
            int     id        = (int)     row[0];
            String  name      = (String)  row[1];
            boolean questItem = (boolean) row[2];
            int     gold      = (int)     row[3];

            ItemDefinition.Builder b =
                    new ItemDefinition.Builder(id, name, ItemCategory.TREASURE)
                            .gold(gold)
                            .questItem(questItem);
            t.put(id, b.build());
        }

        TABLE = Collections.unmodifiableMap(t);
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public static ItemDefinition get(int id) {
        ItemDefinition def = TABLE.get(id);
        if (def == null) throw new IllegalArgumentException(
                "No item defined for ID: " + id);
        return def;
    }

    public static boolean exists(int id) {
        return TABLE.containsKey(id);
    }

    public static int size() { return TABLE.size(); }
}