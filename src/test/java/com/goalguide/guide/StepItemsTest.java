package com.goalguide.guide;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.goalguide.goals.Planner;
import com.goalguide.items.ItemTree;
import com.goalguide.know.AccountState;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The step's item list: on you, in a storage, missing, or just a reminder. */
public class StepItemsTest
{
    @Test
    public void itemListSaysCarriedBankOrMissing()
    {
        Planner.Route r = GuideTest.route();
        Planner.Step quest = r.steps.get(0);
        assertEquals("Coins", quest.uses.get(0).item);
        Map<String, Map<String, Integer>> st = new LinkedHashMap<>();
        st.put(AccountState.BANK, Map.of("coins", 100, "rune pickaxe", 1));
        st.put(AccountState.INVENTORY, Map.of("dragon axe", 1));
        List<StepItems.Item> items = StepItems.of(quest, st, x -> "a shop");
        assertEquals(StepItems.Has.STORED, items.get(0).has);
        assertEquals("Coins: in the bank (100)", items.get(0).say());
        // A tool named by kind: any pickaxe counts, and "axe" is not a pickaxe
        assertTrue(StepItems.matches("rune pickaxe", "pickaxe"));
        assertFalse(StepItems.matches("rune pickaxe", "axe"));
        assertTrue(StepItems.matches("dragon axe", "axe"));
    }

    /** Every item a quest needs, or-groups and keys too. */
    @Test
    public void questItemsAreTheFullList()
    {
        com.goalguide.quests.QuestData q = com.goalguide.quests.QuestData.get();
        List<String> ds = new java.util.ArrayList<>();
        for (com.goalguide.quests.QuestData.Need n : q.needs("DRAGON_SLAYER_I")) if (n.kind.equals("item")) ds.add(n.what + " " + n.amount);
        for (String want : List.of("Unfired bowl 1", "Planks 3", "Steel nails 90", "Coins 2000", "Combat equipment 1")) assertTrue(ds.toString(), ds.contains(want));
        // An "or" group is one row, a key from the keyring is an item
        boolean boots = false, key = false;
        for (com.goalguide.quests.QuestData.Need n : q.needs("TROLL_STRONGHOLD")) boots |= n.what.equals("Climbing boots or 12 coins");
        for (com.goalguide.quests.QuestData.Need n : q.needs("SCORPION_CATCHER")) key |= n.what.toLowerCase().contains("dusty key");
        assertTrue(boots);
        assertTrue(key);
        // "Combat equipment" is a reminder, never a Get step
        ItemTree.Account a = ItemTree.Account.fresh();
        a.skills.put("ATTACK", 99);
        Planner.Route r = Planner.plan(q, null, a, 300, List.of(com.goalguide.goals.Goal.quest("DRAGON_SLAYER_I")));
        for (Planner.Step s : r.steps) assertFalse(s.toString(), s.what.contains("Combat equipment"));
        Planner.Step ds1 = null;
        for (Planner.Step s : r.steps) if ("DRAGON_SLAYER_I".equals(s.quest)) ds1 = s;
        boolean reminder = false;
        for (Planner.Use u : ds1.uses) reminder |= u.reminder && u.item.equals("Combat equipment");
        assertTrue(ds1.uses.toString(), reminder);
        List<StepItems.Item> items = StepItems.of(ds1, Map.of(), x -> null);
        boolean bring = false;
        for (StepItems.Item i : items) bring |= i.has == StepItems.Has.REMINDER && i.say().equals("Bring: Combat equipment");
        assertTrue(bring);
    }

    /** A cat following you counts for the cat quests. */
    @Test
    public void aCatFollowingYouCounts()
    {
        com.goalguide.quests.QuestData q = com.goalguide.quests.QuestData.get();
        Map<String, Map<String, Integer>> st = new LinkedHashMap<>();
        st.put(AccountState.FOLLOWER, Map.of("kitten", 1));
        ItemTree.Account a = ItemTree.Account.fresh();
        a.owned.put("kitten", 1);
        Planner.Route r = Planner.plan(q, null, a, 300, List.of(com.goalguide.goals.Goal.quest("ICTHLARINS_LITTLE_HELPER")));
        for (Planner.Step s : r.steps)
            for (Planner.Step g : s.parts.isEmpty() ? List.of(s) : s.parts)
                assertFalse("no Get step for the cat: " + g, g.kind == Planner.Kind.GET && g.what.toLowerCase().contains("cat"));
        Planner.Step ilh = null;
        for (Planner.Step s : r.steps) if ("ICTHLARINS_LITTLE_HELPER".equals(s.quest)) ilh = s;
        boolean carried = false;
        for (StepItems.Item i : StepItems.of(ilh, st, x -> null)) carried |= i.name.equals("Cat") && i.has == StepItems.Has.CARRIED;
        assertTrue(carried);
        assertTrue(com.goalguide.items.ItemData.sameKind("lazy hellcat", "cat"));
        assertFalse(com.goalguide.items.ItemData.sameKind("overgrown cat", "hunting cat"));
        assertFalse(com.goalguide.items.ItemData.sameKind("wooden cat", "cat"));
        assertTrue(com.goalguide.items.ItemData.sameKind("wooden cat", "wooden cat"));
    }

    /** A quest already started gets no Get step for an item it may have used; the next quest does. */
    @Test
    public void startedQuestDoesNotFetchUsedItemsAgain()
    {
        com.goalguide.quests.QuestData q = com.goalguide.quests.QuestData.get();
        ItemTree.Account a = ItemTree.Account.fresh();
        List<com.goalguide.goals.Goal> goal = List.of(com.goalguide.goals.Goal.quest("FAMILY_CREST"));
        Planner.Route before = Planner.plan(q, null, a, 300, goal);
        boolean got = false;
        for (Planner.Step s : before.steps) got |= s.kind == Planner.Kind.GET;
        assertTrue("not started: items are fetched", got);

        a.questsStarted.add("FAMILY_CREST");
        Planner.Route r = Planner.plan(q, null, a, 300, goal);
        Planner.Step fc = null;
        for (Planner.Step s : r.steps)
        {
            if ("FAMILY_CREST".equals(s.quest)) fc = s;
            assertFalse("no Get step for a started quest's items: " + s, s.kind == Planner.Kind.GET);
        }
        assertTrue(fc.started);
        boolean used = false;
        for (StepItems.Item i : StepItems.of(fc, Map.of(), x -> null))
        {
            assertFalse(i.has == StepItems.Has.MISSING);
            used |= i.has == StepItems.Has.USED && i.say().contains("maybe used");
        }
        assertTrue(used);
    }
}
