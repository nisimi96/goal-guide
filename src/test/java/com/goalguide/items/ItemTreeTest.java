package com.goalguide.items;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import net.runelite.api.Quest;
import com.goalguide.items.ItemData.Drop;
import com.goalguide.items.ItemData.Recipe;
import com.goalguide.items.ItemData.Shop;
import com.goalguide.items.ItemData.Spawn;
import com.goalguide.items.ItemTree.Account;
import com.goalguide.items.ItemTree.Kind;
import com.goalguide.items.ItemTree.Node;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.*;

public class ItemTreeTest
{
    /** A small hand-made slice: steel bar from ores, a rare boss drop that must not win, a loop. */
    private static ItemData small()
    {
        ItemData d = new ItemData();
        Recipe bar = new Recipe("Steel bar", 1, "Furnace", null, null, true);
        bar.materials.add(new String[]{"Iron ore", "1"});
        bar.materials.add(new String[]{"Coal", "2"});
        bar.skills.put("Smithing", 30);
        d.add(bar);
        d.add(new Drop("Iron ore", "Iron rocks", "Always", "15", "mining", 1, 1));
        d.add(new Drop("Coal", "Coal rocks", "Always", "30", "mining", 1, 1));
        d.add(new Shop("Coal", "Ore seller", "Somewhere", "Coins", 45, true));
        d.add(new Drop("Steel bar", "Big boss", "1/5,000", "500", "combat", 1, 1));
        d.add(new Drop("Steel bar", "Supply crate", "1/2", "", "reward", 1, 1));
        d.add(new Spawn("Iron ore", "a rock pile", false));
        // a loop: A from B, B from A
        Recipe a = new Recipe("Thing A", 1, null, null, null, true);
        a.materials.add(new String[]{"Thing B", "1"});
        d.add(a);
        Recipe b = new Recipe("Thing B", 1, null, null, null, true);
        b.materials.add(new String[]{"Thing A", "1"});
        d.add(b);
        d.add(new Shop("Thing B", "B shop", "x", "Coins", 10, true));
        return d;
    }

    private static Account skilled(int lvl)
    {
        Account a = new Account();
        for (String s : new String[]{"SMITHING", "MINING", "MAGIC", "CRAFTING", "WOODCUTTING"}) a.skills.put(s, lvl);
        return a;
    }

    @Test public void picksTheRecipeAndEndsAtRoots()
    {
        Node n = ItemTree.trace(small(), skilled(70), "Steel bar");
        assertEquals(Kind.RECIPE, n.best.kind);
        Map<String, Double> roots = ItemTree.roots(n);
        assertTrue(roots.toString(), roots.keySet().stream().allMatch(k -> k.startsWith("FREE") || k.startsWith("COINS")));
        assertEquals(2.0, roots.entrySet().stream().filter(e -> e.getKey().contains("Coal")).findFirst().get().getValue(), 1e-9);
        assertTrue(ItemTree.playerNeeds(n).isEmpty());
    }

    @Test public void rareBossDropsAndCratesAreNotThePlan()
    {
        Node n = ItemTree.trace(small(), skilled(70), "steel bar");
        for (ItemTree.Way w : n.ways) assertFalse("1/5,000 boss drop listed", w.label.contains("Big boss"));
        assertNotEquals(Kind.REWARD, n.best.kind);
    }

    @Test public void lowLevelSaysWhatToTrain()
    {
        Node n = ItemTree.trace(small(), skilled(10), "Steel bar");
        // the crate (a reward) is open but costly; the recipe needs Smithing 30: either way the gap is reported
        assertTrue(ItemTree.lines(n, 3).toString(), ItemTree.lines(n, 3).toString().contains("Smithing 30"));
    }

    @Test public void ownedItemsAreUsedFirst()
    {
        Account a = skilled(70);
        a.owned.put("steel bar", 50);
        assertEquals(Kind.OWNED, ItemTree.trace(small(), a, "Steel bar").best.kind);
    }

    @Test public void loopsAreCut()
    {
        Node n = ItemTree.trace(small(), skilled(70), "Thing A");
        assertNotNull(n.best);
        assertTrue(ItemTree.roots(n).toString(), ItemTree.roots(n).keySet().stream().anyMatch(k -> k.contains("B shop")));
    }

    @Test public void dosesFoldTogether()
    {
        assertEquals(ItemData.key("Super restore"), ItemData.key("Super restore(4)"));
        assertEquals(1 / 128.0 * 3, ItemData.chance("3/128"), 1e-12);
        assertEquals(1 / 145728.0, ItemData.chance("1/145,728"), 1e-12);
    }

    @Test public void questGatesAreRealQuests()
    {
        for (String q : ItemTree.QUEST_GATES.values()) Quest.valueOf(q);
    }

    /** The real database, when it is on this machine (the cloud test kit downloads it): famous trees reach roots. */
    @Test public void realDataReachesRoots() throws Exception
    {
        File f = new File(System.getProperty("itemroots.db", "/tmp/ner/db/data.sqlite"));
        Assume.assumeTrue("no item database here", f.isFile());
        ItemData d;
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath()))
        {
            d = ItemData.load(c);
        }
        Account a = new Account();
        for (net.runelite.api.Skill s : net.runelite.api.Skill.values()) a.skills.put(s.name(), 70);
        for (String item : List.of("Bracelet of slaughter", "Steel bar", "Mahogany plank", "Prayer potion(4)", "Teak plank"))
        {
            Node n = ItemTree.trace(d, a, item);
            assertNotNull(item + " has no way", n.best);
            for (String root : ItemTree.roots(n).keySet())
                assertFalse(item + " ends at " + root, root.startsWith("no known way") || root.startsWith("loop"));
        }
        Node steel = ItemTree.trace(d, a, "Steel bar");
        assertTrue(ItemTree.roots(steel).toString(), ItemTree.roots(steel).keySet().stream().anyMatch(k -> k.contains("Coal rocks")));
        Node prayer = ItemTree.trace(d, a, "Prayer potion(4)");
        assertEquals(Kind.RECIPE, prayer.best.kind);
        // user 2026-10-23: a new iron makes prayer potions (farm, pick up, mix, train on the way), never farms a 1/7 drop
        Node fresh = ItemTree.trace(d, Account.fresh(), "Prayer potion(4)");
        assertEquals(Kind.RECIPE, fresh.best.kind);
        assertEquals(Kind.RECIPE, ItemTree.trace(d, Account.fresh(), "Steel bar").best.kind);
    }
}
